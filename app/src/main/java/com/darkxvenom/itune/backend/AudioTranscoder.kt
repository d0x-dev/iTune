package com.darkxvenom.itune.backend

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.util.Log
import java.io.File
import java.nio.ByteBuffer

/**
 * Re-encodes a downloaded audio file (AAC / Opus / any Android-decodable format) to AAC-LC in an
 * MP4 (.m4a) container at a fixed bitrate, using only platform MediaCodec APIs.
 * Used by the song cache to shrink files when the user picks a lower cache quality.
 */
object AudioTranscoder {
    private const val TAG = "AudioTranscoder"
    private const val TIMEOUT_US = 10_000L
    private const val MAX_PENDING_BYTES = 1 shl 20

    /** Returns true and leaves a valid file at [output] on success; deletes [output] on failure. */
    fun transcodeToAac(
        input: File,
        output: File,
        targetBitrateBps: Int,
        isActive: () -> Boolean = { true },
    ): Boolean {
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var ok = false
        try {
            if (output.exists()) output.delete()
            extractor = MediaExtractor().apply { setDataSource(input.absolutePath) }
            var trackIdx = -1
            var inFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIdx = i; inFormat = f; break
                }
            }
            if (trackIdx < 0 || inFormat == null) return false
            extractor.selectTrack(trackIdx)

            val mime = inFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
            if (inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) > 2) return false

            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(inFormat, null, null, 0)
                start()
            }
            val encFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrateBps)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val pending = ArrayDeque<ByteArray>()
            var pendingOffset = 0
            var pendingBytes = 0
            var totalPcmBytes = 0L
            var extractorDone = false
            var decoderDone = false
            var encoderEosSent = false
            var encoderDone = false
            var muxTrack = -1
            val info = MediaCodec.BufferInfo()
            val bytesPerSecond = sampleRate.toLong() * channels * 2L

            while (!encoderDone) {
                if (!isActive()) return false

                // 1. extractor -> decoder
                if (!extractorDone) {
                    val i = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val buf = decoder.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buf, 0)
                        if (n < 0) {
                            decoder.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            decoder.queueInputBuffer(i, 0, n, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // 2. decoder -> pending PCM
                while (!decoderDone && pendingBytes < MAX_PENDING_BYTES) {
                    val o = decoder.dequeueOutputBuffer(info, 0)
                    if (o == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = decoder.outputFormat
                        if (f.getInteger(MediaFormat.KEY_SAMPLE_RATE) != sampleRate ||
                            f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != channels
                        ) return false
                        continue
                    }
                    if (o < 0) continue
                    if (info.size > 0) {
                        val out = decoder.getOutputBuffer(o)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        val bytes = ByteArray(info.size)
                        out.get(bytes)
                        pending.addLast(bytes)
                        pendingBytes += bytes.size
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decoderDone = true
                    decoder.releaseOutputBuffer(o, false)
                }

                // 3. pending PCM -> encoder
                if (!encoderEosSent && (pendingBytes > 0 || decoderDone)) {
                    val i = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val buf = encoder.getInputBuffer(i)!!
                        buf.clear()
                        var filled = 0
                        while (pending.isNotEmpty() && buf.remaining() > 0) {
                            val head = pending.first()
                            val n = minOf(head.size - pendingOffset, buf.remaining())
                            buf.put(head, pendingOffset, n)
                            filled += n
                            pendingOffset += n
                            if (pendingOffset >= head.size) {
                                pending.removeFirst()
                                pendingOffset = 0
                            }
                        }
                        pendingBytes -= filled
                        val pts = totalPcmBytes * 1_000_000L / bytesPerSecond
                        if (filled > 0) {
                            totalPcmBytes += filled
                            encoder.queueInputBuffer(i, 0, filled, pts, 0)
                        } else {
                            encoder.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            encoderEosSent = true
                        }
                    }
                }

                // 4. encoder -> muxer
                while (true) {
                    val o = encoder.dequeueOutputBuffer(info, 0)
                    if (o == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        muxTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                        continue
                    }
                    if (o < 0) continue
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerStarted) {
                        val out = encoder.getOutputBuffer(o)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        muxer.writeSampleData(muxTrack, out, info)
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(o, false)
                    if (eos) { encoderDone = true; break }
                }
            }
            ok = muxerStarted && output.exists() && output.length() > 0
            return ok
        } catch (e: Exception) {
            Log.w(TAG, "Transcode failed: ${e.message}")
            return false
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor?.release() }
            if (!ok && output.exists()) output.delete()
        }
    }

    /**
     * Trims [input] between [startMs] and [endMs] and re-encodes to AAC-LC (.m4a) at [targetBitrateBps].
     * Works on any input audio format (Opus, AAC, MP3, FLAC, Vorbis, etc.).
     */
    fun trimAndTranscodeToAac(
        input: File,
        output: File,
        startMs: Long,
        endMs: Long,
        targetBitrateBps: Int = 192_000,
        isActive: () -> Boolean = { true },
    ): Boolean {
        if (startMs >= endMs) return false
        var extractor: MediaExtractor? = null
        var decoder: MediaCodec? = null
        var encoder: MediaCodec? = null
        var muxer: MediaMuxer? = null
        var muxerStarted = false
        var ok = false
        try {
            if (output.exists()) output.delete()
            extractor = MediaExtractor().apply { setDataSource(input.absolutePath) }
            var trackIdx = -1
            var inFormat: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val f = extractor.getTrackFormat(i)
                if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                    trackIdx = i; inFormat = f; break
                }
            }
            if (trackIdx < 0 || inFormat == null) return false
            extractor.selectTrack(trackIdx)
            extractor.seekTo(startMs * 1000L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val mime = inFormat.getString(MediaFormat.KEY_MIME) ?: return false
            val sampleRate = inFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceIn(1, 2)
            if (inFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) > 2) return false

            decoder = MediaCodec.createDecoderByType(mime).apply {
                configure(inFormat, null, null, 0)
                start()
            }
            val encFormat = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, sampleRate, channels).apply {
                setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC)
                setInteger(MediaFormat.KEY_BIT_RATE, targetBitrateBps)
                setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 64 * 1024)
            }
            encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC).apply {
                configure(encFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
                start()
            }
            muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)

            val pending = ArrayDeque<ByteArray>()
            var pendingOffset = 0
            var pendingBytes = 0
            var totalPcmBytes = 0L
            var extractorDone = false
            var decoderDone = false
            var encoderEosSent = false
            var encoderDone = false
            var muxTrack = -1
            val info = MediaCodec.BufferInfo()
            val bytesPerSecond = sampleRate.toLong() * channels * 2L
            val startUs = startMs * 1000L
            val endUs = endMs * 1000L

            while (!encoderDone) {
                if (!isActive()) return false

                // 1. extractor -> decoder
                if (!extractorDone) {
                    val i = decoder.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val buf = decoder.getInputBuffer(i)!!
                        val n = extractor.readSampleData(buf, 0)
                        val sampleTime = extractor.sampleTime
                        if (n < 0 || (sampleTime > 0 && sampleTime > endUs + 1_000_000L)) {
                            decoder.queueInputBuffer(i, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            extractorDone = true
                        } else {
                            decoder.queueInputBuffer(i, 0, n, sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                // 2. decoder -> pending PCM
                while (!decoderDone && pendingBytes < MAX_PENDING_BYTES) {
                    val o = decoder.dequeueOutputBuffer(info, 0)
                    if (o == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        val f = decoder.outputFormat
                        if (f.getInteger(MediaFormat.KEY_SAMPLE_RATE) != sampleRate ||
                            f.getInteger(MediaFormat.KEY_CHANNEL_COUNT) != channels
                        ) return false
                        continue
                    }
                    if (o < 0) continue
                    if (info.size > 0) {
                        val bufUs = info.presentationTimeUs
                        val bufDurationUs = info.size * 1_000_000L / bytesPerSecond
                        val bufEndUs = bufUs + bufDurationUs

                        if (bufEndUs <= startUs) {
                            // Completely before start time, discard
                        } else if (bufUs >= endUs) {
                            // Completely after end time, stop decoding
                            decoderDone = true
                        } else {
                            val out = decoder.getOutputBuffer(o)!!
                            var offset = info.offset
                            var size = info.size

                            // Trim leading bytes if buffer starts before startUs
                            if (bufUs < startUs) {
                                val leadingUs = startUs - bufUs
                                val skipBytes = ((leadingUs * bytesPerSecond / 1_000_000L).toInt() / 2) * 2
                                val actualSkip = skipBytes.coerceIn(0, size)
                                offset += actualSkip
                                size -= actualSkip
                            }

                            // Trim trailing bytes if buffer extends beyond endUs
                            if (bufUs + (offset - info.offset + size) * 1_000_000L / bytesPerSecond > endUs) {
                                val remainingUs = endUs - maxOf(bufUs, startUs)
                                val keepBytes = ((remainingUs * bytesPerSecond / 1_000_000L).toInt() / 2) * 2
                                size = keepBytes.coerceIn(0, size)
                                decoderDone = true
                            }

                            if (size > 0) {
                                out.position(offset)
                                out.limit(offset + size)
                                val bytes = ByteArray(size)
                                out.get(bytes)
                                pending.addLast(bytes)
                                pendingBytes += bytes.size
                            }
                        }
                    }
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) decoderDone = true
                    decoder.releaseOutputBuffer(o, false)
                }

                // 3. pending PCM -> encoder
                if (!encoderEosSent && (pendingBytes > 0 || decoderDone)) {
                    val i = encoder.dequeueInputBuffer(TIMEOUT_US)
                    if (i >= 0) {
                        val buf = encoder.getInputBuffer(i)!!
                        buf.clear()
                        var filled = 0
                        while (pending.isNotEmpty() && buf.remaining() > 0) {
                            val head = pending.first()
                            val n = minOf(head.size - pendingOffset, buf.remaining())
                            buf.put(head, pendingOffset, n)
                            filled += n
                            pendingOffset += n
                            if (pendingOffset >= head.size) {
                                pending.removeFirst()
                                pendingOffset = 0
                            }
                        }
                        pendingBytes -= filled
                        val pts = totalPcmBytes * 1_000_000L / bytesPerSecond
                        if (filled > 0) {
                            totalPcmBytes += filled
                            encoder.queueInputBuffer(i, 0, filled, pts, 0)
                        } else {
                            encoder.queueInputBuffer(i, 0, 0, pts, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            encoderEosSent = true
                        }
                    }
                }

                // 4. encoder -> muxer
                while (true) {
                    val o = encoder.dequeueOutputBuffer(info, 0)
                    if (o == MediaCodec.INFO_TRY_AGAIN_LATER) break
                    if (o == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                        muxTrack = muxer.addTrack(encoder.outputFormat)
                        muxer.start()
                        muxerStarted = true
                        continue
                    }
                    if (o < 0) continue
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) info.size = 0
                    if (info.size > 0 && muxerStarted) {
                        val out = encoder.getOutputBuffer(o)!!
                        out.position(info.offset)
                        out.limit(info.offset + info.size)
                        muxer.writeSampleData(muxTrack, out, info)
                    }
                    val eos = info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                    encoder.releaseOutputBuffer(o, false)
                    if (eos) { encoderDone = true; break }
                }
            }
            ok = muxerStarted && output.exists() && output.length() > 0
            return ok
        } catch (e: Exception) {
            Log.w(TAG, "Trim and transcode failed: ${e.message}")
            return false
        } finally {
            runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            runCatching { encoder?.stop() }
            runCatching { encoder?.release() }
            runCatching { if (muxerStarted) muxer?.stop() }
            runCatching { muxer?.release() }
            runCatching { extractor?.release() }
            if (!ok && output.exists()) output.delete()
        }
    }
}


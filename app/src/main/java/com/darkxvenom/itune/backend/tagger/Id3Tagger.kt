/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * Originally created by DxV Studio ~~ Dark.
 * Credits: DxV Studio, AirBeats, and Dark.
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 * Any copying, modification, or redistribution of this code must strictly comply with the
 * terms of the project LICENSE file and retain full author credit.
 */

package com.darkxvenom.itune.backend.tagger

import java.io.ByteArrayOutputStream

/**
 * Encodes and prepends standard ID3v2.3 tags (Title, Artist, Album, Album Artist, Lyrics, Cover Picture)
 * directly into an MP3 file.
 */
object Id3Tagger {

    fun tag(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String?,
        lyrics: String?,
        cover: ByteArray?,
        coverMime: String = "image/jpeg"
    ): ByteArray {
        val audioBytes = stripExistingId3v2(bytes)
        val framesOut = ByteArrayOutputStream()

        if (title.isNotBlank()) framesOut.write(createTextFrame("TIT2", title))
        if (artist.isNotBlank()) {
            framesOut.write(createTextFrame("TPE1", artist))
            framesOut.write(createTextFrame("TPE2", artist))
        }
        if (!album.isNullOrBlank()) framesOut.write(createTextFrame("TALB", album))
        if (!lyrics.isNullOrBlank()) framesOut.write(createLyricsFrame(lyrics))
        if (cover != null && cover.isNotEmpty()) framesOut.write(createApicFrame(cover, coverMime))

        val framesData = framesOut.toByteArray()
        if (framesData.isEmpty()) return bytes

        val header = ByteArray(10)
        header[0] = 'I'.code.toByte()
        header[1] = 'D'.code.toByte()
        header[2] = '3'.code.toByte()
        header[3] = 0x03 // ID3v2.3
        header[4] = 0x00
        header[5] = 0x00 // flags
        writeSynchsafe(header, 6, framesData.size)

        return header + framesData + audioBytes
    }

    private fun stripExistingId3v2(bytes: ByteArray): ByteArray {
        if (bytes.size >= 10 && bytes[0] == 'I'.code.toByte() && bytes[1] == 'D'.code.toByte() && bytes[2] == '3'.code.toByte()) {
            val tagSize = readSynchsafe(bytes, 6)
            val fullTagSize = 10 + tagSize
            if (fullTagSize in 10..bytes.size) {
                return bytes.copyOfRange(fullTagSize, bytes.size)
            }
        }
        return bytes
    }

    private fun createTextFrame(frameId: String, text: String): ByteArray {
        val textBytes = text.toByteArray(Charsets.UTF_8)
        val payload = ByteArray(1 + textBytes.size)
        payload[0] = 0x03 // UTF-8 encoding flag
        System.arraycopy(textBytes, 0, payload, 1, textBytes.size)
        return makeFrame(frameId, payload)
    }

    private fun createLyricsFrame(lyrics: String): ByteArray {
        val textBytes = lyrics.toByteArray(Charsets.UTF_8)
        val lang = "eng".toByteArray(Charsets.US_ASCII)
        val payload = ByteArray(1 + 3 + 1 + textBytes.size)
        payload[0] = 0x03 // UTF-8
        System.arraycopy(lang, 0, payload, 1, 3)
        payload[4] = 0x00 // empty description delimiter
        System.arraycopy(textBytes, 0, payload, 5, textBytes.size)
        return makeFrame("USLT", payload)
    }

    private fun createApicFrame(cover: ByteArray, mimeType: String): ByteArray {
        val cleanMime = if (mimeType.contains("png", ignoreCase = true)) "image/png" else "image/jpeg"
        val mimeBytes = cleanMime.toByteArray(Charsets.US_ASCII)
        val payload = ByteArray(1 + mimeBytes.size + 1 + 1 + 1 + cover.size)
        var p = 0
        payload[p++] = 0x00 // ISO-8859-1 for description
        System.arraycopy(mimeBytes, 0, payload, p, mimeBytes.size)
        p += mimeBytes.size
        payload[p++] = 0x00 // null terminator for MIME string
        payload[p++] = 0x03 // 0x03 = Front Cover
        payload[p++] = 0x00 // null terminator for empty description
        System.arraycopy(cover, 0, payload, p, cover.size)
        return makeFrame("APIC", payload)
    }

    private fun makeFrame(id: String, payload: ByteArray): ByteArray {
        val frame = ByteArray(10 + payload.size)
        val idBytes = id.toByteArray(Charsets.US_ASCII)
        System.arraycopy(idBytes, 0, frame, 0, 4)
        val size = payload.size
        frame[4] = ((size shr 24) and 0xFF).toByte()
        frame[5] = ((size shr 16) and 0xFF).toByte()
        frame[6] = ((size shr 8) and 0xFF).toByte()
        frame[7] = (size and 0xFF).toByte()
        frame[8] = 0x00
        frame[9] = 0x00
        System.arraycopy(payload, 0, frame, 10, payload.size)
        return frame
    }

    private fun readSynchsafe(b: ByteArray, off: Int): Int =
        ((b[off].toInt() and 0x7F) shl 21) or
        ((b[off + 1].toInt() and 0x7F) shl 14) or
        ((b[off + 2].toInt() and 0x7F) shl 7) or
        (b[off + 3].toInt() and 0x7F)

    private fun writeSynchsafe(b: ByteArray, off: Int, value: Int) {
        b[off] = ((value shr 21) and 0x7F).toByte()
        b[off + 1] = ((value shr 14) and 0x7F).toByte()
        b[off + 2] = ((value shr 7) and 0x7F).toByte()
        b[off + 3] = (value and 0x7F).toByte()
    }
}

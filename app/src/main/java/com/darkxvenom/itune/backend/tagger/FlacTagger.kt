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
 * Rewrites a downloaded FLAC file's metadata blocks to carry title, artist, album,
 * lyrics and cover art.
 */
object FlacTagger {

    private val MAGIC = "fLaC".toByteArray(Charsets.US_ASCII)
    private const val BLOCK_HEADER = 4

    private const val TYPE_STREAMINFO = 0
    private const val TYPE_PADDING = 1
    private const val TYPE_APPLICATION = 2
    private const val TYPE_SEEKTABLE = 3
    private const val TYPE_VORBIS_COMMENT = 4
    private const val TYPE_CUESHEET = 5
    private const val TYPE_PICTURE = 6

    private fun ByteArray.regionMatches(offset: Int, other: ByteArray): Boolean {
        if (offset < 0 || offset + other.size > this.size) return false
        for (i in other.indices) {
            if (this[offset + i] != other[i]) return false
        }
        return true
    }

    private class Block(val type: Int, val payload: ByteArray)

    fun tag(
        bytes: ByteArray,
        title: String,
        artist: String,
        album: String?,
        lyrics: String?,
        cover: ByteArray?,
        coverMime: String,
        wordLyrics: String? = null,
    ): ByteArray = runCatching {
        val done = header(bytes, title, artist, album, lyrics, cover, coverMime, wordLyrics)
            ?: return@runCatching bytes
        val out = ByteArrayOutputStream(done.metadata.size + (bytes.size - done.audioStart))
        out.write(done.metadata)
        out.write(bytes, done.audioStart, bytes.size - done.audioStart)
        out.toByteArray()
    }.getOrDefault(bytes)

    class Rewrite(val metadata: ByteArray, val audioStart: Int)

    fun header(
        head: ByteArray,
        title: String,
        artist: String,
        album: String?,
        lyrics: String?,
        cover: ByteArray?,
        coverMime: String,
        wordLyrics: String? = null,
    ): Rewrite? {
        if (head.size < 4 || !head.regionMatches(0, MAGIC)) return null

        val blocks = mutableListOf<Block>()
        var offset = MAGIC.size
        while (true) {
            if (offset + BLOCK_HEADER > head.size) return null
            val flags = head[offset].toInt() and 0xFF
            val length = ((head[offset + 1].toInt() and 0xFF) shl 16) or
                ((head[offset + 2].toInt() and 0xFF) shl 8) or
                (head[offset + 3].toInt() and 0xFF)
            val start = offset + BLOCK_HEADER
            if (start + length > head.size) return null

            val type = flags and 0x7F
            val isLast = (flags and 0x80) != 0

            if (type != TYPE_VORBIS_COMMENT && type != TYPE_PICTURE && type != TYPE_PADDING) {
                val payload = ByteArray(length)
                System.arraycopy(head, start, payload, 0, length)
                blocks += Block(type, payload)
            }

            offset = start + length
            if (isLast) break
        }

        if (blocks.isEmpty() || blocks.first().type != TYPE_STREAMINFO) return null

        val audioStart = offset
        val newBlocks = mutableListOf<Block>()
        newBlocks += blocks.first()
        blocks.drop(1).forEach { newBlocks += it }

        newBlocks += Block(TYPE_VORBIS_COMMENT, buildVorbisComment(title, artist, album, lyrics, wordLyrics))
        if (cover != null && cover.isNotEmpty()) {
            newBlocks += Block(TYPE_PICTURE, buildPictureBlock(cover, coverMime))
        }

        val out = ByteArrayOutputStream()
        out.write(MAGIC)
        newBlocks.forEachIndexed { index, b ->
            val isLast = index == newBlocks.size - 1
            val flags = (if (isLast) 0x80 else 0) or (b.type and 0x7F)
            out.write(flags)
            val len = b.payload.size
            out.write((len shr 16) and 0xFF)
            out.write((len shr 8) and 0xFF)
            out.write(len and 0xFF)
            out.write(b.payload)
        }

        return Rewrite(out.toByteArray(), audioStart)
    }

    private fun buildVorbisComment(
        title: String,
        artist: String,
        album: String?,
        lyrics: String?,
        wordLyrics: String?,
    ): ByteArray {
        val out = ByteArrayOutputStream()
        val vendor = "iTune".toByteArray(Charsets.UTF_8)
        writeU32Le(out, vendor.size)
        out.write(vendor)

        val comments = mutableListOf<String>()
        if (title.isNotBlank()) comments += "TITLE=$title"
        if (artist.isNotBlank()) {
            comments += "ARTIST=$artist"
            comments += "ALBUMARTIST=$artist"
        }
        if (!album.isNullOrBlank()) comments += "ALBUM=$album"
        if (!lyrics.isNullOrBlank()) comments += "LYRICS=$lyrics"
        if (!wordLyrics.isNullOrBlank()) comments += "WORD_LYRICS=$wordLyrics"

        writeU32Le(out, comments.size)
        for (c in comments) {
            val b = c.toByteArray(Charsets.UTF_8)
            writeU32Le(out, b.size)
            out.write(b)
        }
        return out.toByteArray()
    }

    private fun buildPictureBlock(cover: ByteArray, mimeType: String): ByteArray {
        val out = ByteArrayOutputStream()
        writeU32Be(out, 3) // Picture type: Cover (front)
        val mimeBytes = mimeType.toByteArray(Charsets.US_ASCII)
        writeU32Be(out, mimeBytes.size)
        out.write(mimeBytes)
        writeU32Be(out, 0) // Description string length
        writeU32Be(out, 0) // Width (0 = unspecified)
        writeU32Be(out, 0) // Height
        writeU32Be(out, 24) // Color depth
        writeU32Be(out, 0) // Indexed colors
        writeU32Be(out, cover.size)
        out.write(cover)
        return out.toByteArray()
    }

    private fun writeU32Le(out: ByteArrayOutputStream, v: Int) {
        out.write(v and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 24) and 0xFF)
    }

    private fun writeU32Be(out: ByteArrayOutputStream, v: Int) {
        out.write((v shr 24) and 0xFF)
        out.write((v shr 16) and 0xFF)
        out.write((v shr 8) and 0xFF)
        out.write(v and 0xFF)
    }
}

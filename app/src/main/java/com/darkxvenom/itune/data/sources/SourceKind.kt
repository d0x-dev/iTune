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

package com.darkxvenom.itune.data.sources

/**
 * The kinds of sources iTune knows how to talk to.
 * [rank] separates user-installed providers from the two default providers. The installed config list stores
 * playback priority, with JioSaavn followed by YouTube on a fresh install.
 */
enum class SourceKind(
    val label: String,
    val detail: String,
    val labels: List<String>,
    val needsServer: Boolean,
    val canServeLossless: Boolean,
    val worthPrefetching: Boolean = false,
    val rank: Int,
) {
    ADDON(
        label = "Addon",
        detail = "An HTTP addon server providing search and stream manifests over plain JSON.",
        labels = listOf("FLAC", "Lossless", "Hi-Res"),
        needsServer = true,
        canServeLossless = true,
        rank = 0,
    ),

    CUSTOM_MODULE(
        label = "Custom Module",
        detail = "A custom JavaScript module index or extension script.",
        labels = listOf("FLAC", "Lossless", "Hi-Res", "Plugins"),
        needsServer = true,
        canServeLossless = true,
        rank = 0,
    ),

    MODULE(
        label = "Module Source",
        detail = "QuickJS JavaScript plugins searching services like Deezer, Apple Music, and more.",
        labels = listOf("FLAC", "Lossless", "Hi-Res", "Plugins"),
        needsServer = true,
        canServeLossless = true,
        rank = 1,
    ),

    TIDAL(
        label = "Tidal Hi-Fi",
        detail = "Genuine lossless FLAC (up to 24-bit/192kHz) streamed via community instances with automatic failover.",
        labels = listOf("FLAC", "Lossless", "Hi-Res"),
        needsServer = true,
        canServeLossless = true,
        worthPrefetching = true,
        rank = 1,
    ),

    HIRES(
        label = "Hi-Res Lossless",
        detail = "Direct verified FLAC files up to 24-bit/192kHz from open catalogue.",
        labels = listOf("FLAC", "Lossless", "Hi-Res"),
        needsServer = false,
        canServeLossless = true,
        worthPrefetching = true,
        rank = 1,
    ),

    JIOSAAVN(
        label = "JioSaavn",
        detail = "High Quality AAC streams up to 320kbps.",
        labels = listOf("High Quality", "320kbps"),
        needsServer = false,
        canServeLossless = false,
        worthPrefetching = true,
        rank = 2,
    ),

    YOUTUBE(
        label = "YouTube Music",
        detail = "Full music catalogue, standard lossy stream.",
        labels = listOf("Lossy", "Full catalogue"),
        needsServer = false,
        canServeLossless = false,
        rank = 3,
    );

    val isUserAdded: Boolean get() = rank == USER_ADDED

    companion object {
        const val USER_ADDED = 0
    }
}

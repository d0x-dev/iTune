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

package com.darkxvenom.itune.discordrpc.rpc

import com.darkxvenom.itune.discordrpc.repository.DiscordRpcRepository

sealed class RpcImage {
    abstract suspend fun resolveImage(repository: DiscordRpcRepository): String?

    class DiscordImage(val image: String) : RpcImage() {
        override suspend fun resolveImage(repository: DiscordRpcRepository): String {
            return "mp:${image}"
        }
    }
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    class ExternalImage(val image: String) : RpcImage() {
        override suspend fun resolveImage(repository: DiscordRpcRepository): String? {
            return repository.getImage(image)
        }
    }
}

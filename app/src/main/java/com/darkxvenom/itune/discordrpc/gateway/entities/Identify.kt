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

package com.darkxvenom.itune.discordrpc.gateway.entities

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Identify(
    @SerialName("capabilities")
    val capabilities: Int,
    @SerialName("compress")
    val compress: Boolean,
    @SerialName("largeThreshold")
    val largeThreshold: Int,
    @SerialName("properties")
    val properties: Properties,
    @SerialName("token")
    val token: String,
) {
    companion object {
        fun String.toIdentifyPayload() = Identify(
            capabilities = 65,
            compress = false,
            largeThreshold = 100,
            properties = Properties(
                browser = "Discord Client",
                device = "ktor",
                os = "Windows"
            ),
            token = this
        )
    }
}
// Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

@Serializable
data class Properties(
    @SerialName("browser")
    val browser: String,
    @SerialName("device")
    val device: String,
    @SerialName("os")
    val os: String,
)

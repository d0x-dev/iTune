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

package com.darkxvenom.itune.discordrpc.gateway.entities.op

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

class OpCodeSerializer : KSerializer<OpCode> {
    override val descriptor: SerialDescriptor
        get() = PrimitiveSerialDescriptor("OpCode", PrimitiveKind.INT)
    // Originally created by DxV Studio ~~ Dark | Credits: DxV Studio, AirBeats, Dark — Licensed under GPL-3.0.

    override fun deserialize(decoder: Decoder): OpCode {
        val opCode = decoder.decodeInt()
        return OpCode.values().firstOrNull { it.value == opCode } ?: throw IllegalArgumentException("Unknown OpCode $opCode")
    }

    override fun serialize(encoder: Encoder, value: OpCode) {
        encoder.encodeInt(value.value)
    }
}

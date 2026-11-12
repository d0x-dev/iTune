package com.darkxvenom.itune.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsPlayerTypeTest {

    @Test
    fun defaultPlayerTypeIsDynamic() {
        val defaultState = SettingsUiState()
        assertEquals("Dynamic", defaultState.playerType)
    }

    @Test
    fun playerTypeKeyConstant() {
        assertEquals("player_type", SettingsStore.KEY_PLAYER_TYPE)
    }
}

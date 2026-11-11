package com.darkxvenom.itune.ui.welcome

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class WelcomeAmbientPaletteTest {

    @Test
    fun keyframesCoverBothDarkAndLightPhases() {
        val hasDark = WelcomeAmbientKeyframes.any { it.lightFactor == 0f }
        val hasLight = WelcomeAmbientKeyframes.any { it.lightFactor == 1f }
        assertTrue(hasDark)
        assertTrue(hasLight)
    }

    @Test
    fun sampleWrapsContinuouslyAroundLoopBoundary() {
        val atZero = sampleWelcomeAmbientFrame(0f)
        val atOne = sampleWelcomeAmbientFrame(1f)
        val nearEnd = sampleWelcomeAmbientFrame(0.9995f)
        val wrappedNegative = sampleWelcomeAmbientFrame(-0.25f)
        val equivalentPositive = sampleWelcomeAmbientFrame(0.75f)

        assertEquals(atZero.baseColor, atOne.baseColor)
        assertEquals(atZero.lightFactor, atOne.lightFactor, 0.0001f)
        assertTrue(abs(nearEnd.lightFactor - atZero.lightFactor) < 0.01f)
        assertEquals(equivalentPositive.baseColor, wrappedNegative.baseColor)
    }

    @Test
    fun typingAndBackspaceScheduleAreRealisticAndWithinSpec() {
        assertTrue(WELCOME_CURSOR_INITIAL_CUE_MS > 0L)
        assertTrue(WELCOME_TEXT_HOLD_HIDDEN_MS in 5_000L..6_000L)
        assertTrue(WELCOME_CURSOR_REAPPEAR_CUE_MS > 0L)
        val firstSpaceIndex = WELCOME_HEADLINE_TEXT.indexOf(' ')
        val afterSpaceDelay = welcomeTypingKeystrokeDelayMs(WELCOME_HEADLINE_TEXT, firstSpaceIndex + 1)
        val midWordDelay = welcomeTypingKeystrokeDelayMs(WELCOME_HEADLINE_TEXT, 2)
        assertTrue(afterSpaceDelay > midWordDelay)
        assertTrue(welcomeBackspaceDelayMs(0) > welcomeBackspaceDelayMs(5))
    }
}

package com.darkxvenom.itune.backend

import com.darkxvenom.itune.data.library.SettingsStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

class CrossfadeAndAutomixTest {

    @Test
    fun parseCrossfadeDurationMs_mapsOptionsCorrectly() {
        assertEquals(0, SettingsStore.parseCrossfadeDurationMs("Off"))
        assertEquals(0, SettingsStore.parseCrossfadeDurationMs("off"))
        assertEquals(2000, SettingsStore.parseCrossfadeDurationMs("2 sec"))
        assertEquals(4000, SettingsStore.parseCrossfadeDurationMs("4 sec"))
        assertEquals(6000, SettingsStore.parseCrossfadeDurationMs("6 sec"))
        assertEquals(8000, SettingsStore.parseCrossfadeDurationMs("8 sec"))
        assertEquals(12000, SettingsStore.parseCrossfadeDurationMs("12 sec"))
        assertEquals(0, SettingsStore.parseCrossfadeDurationMs("invalid"))
    }

    @Test
    fun equalPowerCurve_preservesTotalAcousticPower() {
        val steps = 100
        for (i in 0..steps) {
            val t = i.toFloat() / steps.toFloat()
            val radians = t.toDouble() * (PI / 2.0)
            val outgoing = cos(radians).toFloat()
            val incoming = sin(radians).toFloat()

            // Sum of squares (power) must equal 1.0 across all progress steps
            val power = (outgoing * outgoing) + (incoming * incoming)
            assertTrue("Power deviation at t=$t: power=$power", abs(power - 1.0f) < 1e-5f)
        }

        // At midpoint t = 0.5: both channels are at -3 dB (1/√2 ≈ 0.7071)
        val midRadians = 0.5 * (PI / 2.0)
        val midOut = cos(midRadians).toFloat()
        val midIn = sin(midRadians).toFloat()
        assertTrue("Midpoint outgoing factor must be ~0.7071", abs(midOut - 0.7071f) < 0.001f)
        assertTrue("Midpoint incoming factor must be ~0.7071", abs(midIn - 0.7071f) < 0.001f)
    }

    @Test
    fun automixQueueActions_manageTracksCorrectly() {
        AutomixManager.clearAutomix()
        val track1 = MusicTrack(id = "trk1", title = "Track 1", artists = "Artist 1")
        val track2 = MusicTrack(id = "trk2", title = "Track 2", artists = "Artist 2")

        AutomixManager.automixTracks.add(track1)
        AutomixManager.automixTracks.add(track2)

        assertEquals("trk1", AutomixManager.peekNextAutomix()?.id)
        assertEquals(2, AutomixManager.automixTracks.size)

        AutomixManager.clearAutomix()
        assertEquals(0, AutomixManager.automixTracks.size)
    }
}

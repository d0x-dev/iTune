package com.darkxvenom.itune.data.sources

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourcesTest {
    @Test
    fun streamQualityOnlyCallsLosslessCodecsLossless() {
        val saavn = describeStreamQuality("AAC", 320, 0, 0)
        val flac = describeStreamQuality("FLAC", 1411, 44_100, 16)

        assertTrue(saavn.contains("High bitrate (lossy)"))
        assertFalse(saavn.contains("Lossless"))
        assertTrue(flac.contains("Lossless"))
        assertFalse(flac.contains("Hi-Res"))
    }

    @Test
    fun highResolutionRequiresMeasuredSampleRateOrBitDepth() {
        val highSampleRate = describeStreamQuality("FLAC", 4608, 192_000, 24)
        val highBitDepth = describeStreamQuality("ALAC", 0, 48_000, 24)

        assertTrue(highSampleRate.contains("Hi-Res Lossless"))
        assertTrue(highBitDepth.contains("Hi-Res Lossless"))
    }
}

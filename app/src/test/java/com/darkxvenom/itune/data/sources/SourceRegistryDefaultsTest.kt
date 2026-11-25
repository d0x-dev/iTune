/*
 * Copyright (C) 2026 DxV Studio ~~ Dark
 *
 * This file is part of iTune and is licensed under the GNU General Public License v3.0 (GPL-3.0).
 */

package com.darkxvenom.itune.data.sources

import com.darkxvenom.itune.data.tidal.TidalInstances
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceRegistryDefaultsTest {
    @Test
    fun freshInstallSeedsJioSaavnBeforeYouTubeWithExpectedStates() {
        val sources = SourceRegistry.sourcesForInit(emptyList(), forceJioSaavnOff = false)

        assertEquals(listOf(SourceKind.JIOSAAVN, SourceKind.YOUTUBE), sources.map { it.kind })
        assertFalse(sources.first().enabled)
        assertTrue(sources.last().enabled)
    }

    @Test
    fun upgradeRemovesBundledLosslessSourcesAndPreservesInstalledProviders() {
        val addon = SourceConfig(kind = SourceKind.ADDON, baseUrl = "https://example.com")
        val customTidal = SourceConfig(kind = SourceKind.TIDAL, baseUrl = "https://custom-tidal.example")
        val stored = listOf(
            SourceConfig(kind = SourceKind.TIDAL, baseUrl = TidalInstances.BUNDLED.first().url),
            SourceConfig(kind = SourceKind.HIRES),
            SourceConfig(kind = SourceKind.JIOSAAVN, enabled = true),
            SourceConfig(kind = SourceKind.YOUTUBE, enabled = true),
            addon,
            customTidal,
        )

        val sources = SourceRegistry.sourcesForInit(stored, forceJioSaavnOff = true)

        assertEquals(
            listOf(SourceKind.JIOSAAVN, SourceKind.YOUTUBE, SourceKind.ADDON, SourceKind.TIDAL),
            sources.map { it.kind },
        )
        assertFalse(sources.first().enabled)
        assertTrue(sources[1].enabled)
        assertEquals(addon.id, sources[2].id)
        assertEquals(customTidal.id, sources.last().id)
    }
}

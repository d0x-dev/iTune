package com.darkxvenom.itune.data.sources

import com.darkxvenom.itune.data.sources.module.ModuleIndex
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModuleIndexTest {
    @Test
    fun readsExtensionsAcrossModuleIndexCategories() {
        val modules = ModuleIndex.parseModules(
            """{
                "category:music": [{"id":"tidal","name":"Tidal","author":"Community","version":"2","download":"tidal.js","tags":["Lossless"]}],
                "category:other": [{"id":"deezer","name":"Deezer","download":"https://example.test/deezer.js"}],
                "category:testing": [{"id":"test","name":"Test","download":"test.js"}]
            }""",
        )

        assertEquals(listOf("tidal", "deezer"), modules.map { it.id })
        assertEquals("Community", modules.first().author)
        assertTrue(modules.first().isLossless)
    }
}

package com.darkxvenom.itune.backend.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsProvidersTest {

    @Test
    fun testAllProvidersCatalogCompleteness() {
        assertEquals("Expected 11 lyrics providers", 11, LyricsHelper.ALL_PROVIDERS.size)
        assertEquals("Expected 11 metadata entries", 11, LyricsHelper.PROVIDER_METADATA.size)

        val expectedIds = listOf(
            "lrclib",
            "kugou",
            "simpmusic",
            "youtube_subtitles",
            "paxsenix",
            "unison",
            "betterlyrics",
            "portato",
            "youly",
            "megalobiz",
            "youtube_music"
        )

        for (id in expectedIds) {
            assertTrue("Missing provider ID: $id in ALL_PROVIDERS", LyricsHelper.ALL_PROVIDERS.containsKey(id))
            val meta = LyricsHelper.PROVIDER_METADATA.firstOrNull { it.id == id }
            assertNotNull("Missing provider metadata for: $id", meta)
            assertTrue("Provider $id displayName should not be blank", meta!!.displayName.isNotBlank())
            assertTrue("Provider $id description should not be blank", meta.description.isNotBlank())
            assertTrue("Provider $id should have a non-zero iconRes", meta.iconRes != 0)
        }
    }

    @Test
    fun testTtmlDetectionAndParsing() {
        val sampleTtml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="00:04.25" end="00:08.50">
                            <span begin="00:04.25" end="00:06.00">Welcome</span>
                            <span begin="00:06.10" end="00:08.50">home</span>
                        </p>
                        <p begin="00:09.00" end="00:12.00">The sound is clear</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        assertTrue("Should detect TTML", TTMLParser.isTtml(sampleTtml))
        assertFalse("Should not detect plain text as TTML", TTMLParser.isTtml("Just plain lyrics"))
        assertFalse("Should not detect JSON as TTML", TTMLParser.isTtml("""{"lyrics": "hello"}"""))

        val parsed = TTMLParser.parseTtml(sampleTtml)
        assertEquals(2, parsed.size)

        val line1 = parsed[0]
        assertEquals(4250L, line1.timeMs)
        assertEquals("Welcome home", line1.text)
        assertEquals(2, line1.words.size)
        assertEquals("Welcome", line1.words[0].text)
        assertEquals(4250L, line1.words[0].startMs)
        assertEquals("home", line1.words[1].text)

        val line2 = parsed[1]
        assertEquals(9000L, line2.timeMs)
        assertEquals("The sound is clear", line2.text)

        val lrc = TTMLParser.ttmlToLrc(sampleTtml)
        assertTrue(lrc.contains("[00:04.25] Welcome home"))
        assertTrue(lrc.contains("[00:09.00] The sound is clear"))
    }

    @Test
    fun testParseLyricsTextSeamlessHandling() {
        // 1. TTML input
        val ttml = """<tt xmlns="http://www.w3.org/ns/ttml"><body><div><p begin="00:02.00" end="00:05.00">Sing along</p></div></body></tt>"""
        val fromTtml = LyricsHelper.parseLyricsText(ttml)
        assertEquals(1, fromTtml.size)
        assertEquals(2000L, fromTtml[0].timeMs)
        assertEquals("Sing along", fromTtml[0].text)

        // 2. LRC input
        val lrc = "[01:15.30] Synced line here\n[01:20.00] Next line here"
        val fromLrc = LyricsHelper.parseLyricsText(lrc)
        assertEquals(2, fromLrc.size)
        assertEquals(75300L, fromLrc[0].timeMs)
        assertEquals("Synced line here", fromLrc[0].text)

        // 3. Plain text fallback
        val plain = "Plain line one\nPlain line two"
        val fromPlain = LyricsHelper.parseLyricsText(plain)
        assertEquals(2, fromPlain.size)
        assertEquals(0L, fromPlain[0].timeMs)
        assertEquals("Plain line one", fromPlain[0].text)
    }

    @Test
    fun testAiTranslationProvidersConfig() {
        val providers = LyricsTranslationHelper.SUPPORTED_PROVIDERS
        assertEquals("Should support 4 AI providers", 4, providers.size)

        val providerIds = providers.map { it.first }
        assertTrue("Should include OpenRouter", providerIds.contains(LyricsTranslationHelper.PROVIDER_OPENROUTER))
        assertTrue("Should include Gemini", providerIds.contains(LyricsTranslationHelper.PROVIDER_GEMINI))
        assertTrue("Should include OpenAI", providerIds.contains(LyricsTranslationHelper.PROVIDER_OPENAI))
        assertTrue("Should include Claude", providerIds.contains(LyricsTranslationHelper.PROVIDER_CLAUDE))

        // Check default URLs
        assertTrue(LyricsTranslationHelper.defaultBaseUrl(LyricsTranslationHelper.PROVIDER_OPENROUTER).contains("openrouter.ai"))
        assertTrue(LyricsTranslationHelper.defaultBaseUrl(LyricsTranslationHelper.PROVIDER_GEMINI).contains("googleapis.com"))
        assertTrue(LyricsTranslationHelper.defaultBaseUrl(LyricsTranslationHelper.PROVIDER_OPENAI).contains("api.openai.com"))
        assertTrue(LyricsTranslationHelper.defaultBaseUrl(LyricsTranslationHelper.PROVIDER_CLAUDE).contains("anthropic.com"))

        // Check default models
        assertTrue(LyricsTranslationHelper.defaultModel(LyricsTranslationHelper.PROVIDER_OPENROUTER).isNotBlank())
        assertTrue(LyricsTranslationHelper.defaultModel(LyricsTranslationHelper.PROVIDER_GEMINI).isNotBlank())
        assertTrue(LyricsTranslationHelper.defaultModel(LyricsTranslationHelper.PROVIDER_OPENAI).isNotBlank())
        assertTrue(LyricsTranslationHelper.defaultModel(LyricsTranslationHelper.PROVIDER_CLAUDE).isNotBlank())
    }

    @Test
    fun testAiTranslationCacheOps() {
        LyricsTranslationHelper.clearCache()
        LyricsTranslationHelper.clearCacheForSong("test_song_123")
    }
}

package com.darkxvenom.itune.diagnostics

import org.junit.Assert.*
import org.junit.Test

class DiagnosticTextTest {
    @Test fun preservesFailureAndUnicodeWhileMaskingCredentials() {
        val diagnostic = "W/Source: ошибка HTTP 403: région indisponible\nCaused by: java.net.UnknownHostException: music.example"
        assertEquals(diagnostic, DiagnosticText.mask(diagnostic))
        val lines = listOf(
            "Set-Cookie: sid=example-secret; other=second-secret",
            """{"access_token":"example-secret","reason":"expired"}""",
            "Authorization: Bearer example-secret",
            "Cookie: sp_dc=example-secret; second=second-secret",
            "GET https://user:example-secret@example.org/player?sig=second-secret",
            """{"refreshToken":"example-secret""",
        )
        for (line in lines) {
            val result = DiagnosticText.mask(line)
            assertFalse(result.contains("example-secret"))
            assertFalse(result.contains("second-secret"))
        }
    }

    @Test fun extractsActualProviderReason() {
        val error = """{"error":{"code":403,"status":"PERMISSION_DENIED","detail":{"region":"IN"}}}"""
        assertEquals(error, DiagnosticText.mask(error))
        assertEquals(error, DiagnosticText.responseReason(error))
        val status = """{"status":"UNPLAYABLE","reason":"権利制限"}"""
        val captured = org.json.JSONObject(DiagnosticText.responseReason("""{"playerResponse":{"playabilityStatus":$status}}""")!!)
        assertEquals("権利制限", captured.getString("reason"))
        assertEquals("UNPLAYABLE", captured.getString("status"))
        val endpoint = "https://example.org/media?itag=251&quality=high"
        assertEquals(endpoint, DiagnosticText.mask(endpoint))
        assertNull(DiagnosticText.responseReason("""{"playabilityStatus":{"status":"OK"},"streamingData":{}}"""))
        assertNull(DiagnosticText.responseReason("media bytes"))
    }
}

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

package com.darkxvenom.itune.data.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeveloperNewsManagerTest {

    @Test
    fun parseNewsItems_parsesITuneAnnouncementsAndFiltersIrrelevant() {
        val json = """
            {
              "success": true,
              "news": [
                {
                  "id": "itune_telegram",
                  "title": "Join Telegram",
                  "message": "Join our community",
                  "showPopup": true,
                  "active": true,
                  "tag": "iTune",
                  "timestamp": 1795153013943,
                  "priority": 200
                },
                {
                  "id": "announcement_release",
                  "title": "iTune 1.0 — First Public Release",
                  "message": "First public release of iTune",
                  "showPopup": true,
                  "active": true,
                  "tag": "",
                  "timestamp": 1795153013942,
                  "priority": 100
                },
                {
                  "id": "airbeats_update",
                  "title": "AirBeats Nightly",
                  "message": "Nightly release for AirBeats",
                  "showPopup": true,
                  "active": true,
                  "tag": "Nightly",
                  "timestamp": 1789727785982,
                  "priority": 50
                }
              ]
            }
        """.trimIndent()

        val items = DeveloperNewsManager.parseNewsItems(json)
        assertEquals(2, items.size)
        assertTrue(items.any { it.id == "itune_telegram" })
        assertTrue(items.any { it.id == "announcement_release" })
    }

    @Test
    fun findActivePopupCandidate_firstLaunch_returnsEligibleCandidate() {
        val item1 = DeveloperNewsItem(
            id = "itune_1",
            title = "Welcome",
            message = "Hello",
            showPopup = true,
            active = true,
            timestamp = 1000L,
            priority = 100
        )
        val items = listOf(item1)

        val candidate = DeveloperNewsManager.findActivePopupCandidate(
            items = items,
            dismissedKeys = emptySet(),
            lastDismissedTimestamp = 0L
        )

        assertNotNull(candidate)
        assertEquals("itune_1", candidate?.id)
    }

    @Test
    fun findActivePopupCandidate_dismissedInstance_isExcluded() {
        val item1 = DeveloperNewsItem(
            id = "itune_1",
            title = "Welcome",
            message = "Hello",
            showPopup = true,
            active = true,
            timestamp = 1000L,
            priority = 100
        )
        val items = listOf(item1)

        val candidate = DeveloperNewsManager.findActivePopupCandidate(
            items = items,
            dismissedKeys = setOf("itune_1_1000"),
            lastDismissedTimestamp = 1000L
        )

        assertNull(candidate)
    }

    @Test
    fun findActivePopupCandidate_olderThanSkipped_isExcluded() {
        val item1 = DeveloperNewsItem(
            id = "itune_old",
            title = "Old News",
            message = "Old Content",
            showPopup = true,
            active = true,
            timestamp = 1500L,
            priority = 100
        )
        val items = listOf(item1)

        // User previously skipped news up to timestamp 2000L
        val candidate = DeveloperNewsManager.findActivePopupCandidate(
            items = items,
            dismissedKeys = setOf("itune_prev_2000"),
            lastDismissedTimestamp = 2000L
        )

        assertNull(candidate)
    }

    @Test
    fun findActivePopupCandidate_newerThanSkipped_isIncluded() {
        val oldItem = DeveloperNewsItem(
            id = "itune_old",
            title = "Old News",
            message = "Old Content",
            showPopup = true,
            active = true,
            timestamp = 1500L,
            priority = 50
        )
        val newItem = DeveloperNewsItem(
            id = "itune_new",
            title = "New Update Available",
            message = "Check out the latest features",
            showPopup = true,
            active = true,
            timestamp = 3000L,
            priority = 100
        )
        val items = listOf(oldItem, newItem)

        // User previously skipped news at timestamp 2000L
        val candidate = DeveloperNewsManager.findActivePopupCandidate(
            items = items,
            dismissedKeys = setOf("itune_old_1500", "itune_old", "itune_prev_2000"),
            lastDismissedTimestamp = 2000L
        )

        assertNotNull(candidate)
        assertEquals("itune_new", candidate?.id)
        assertEquals(3000L, candidate?.timestamp)
    }

    @Test
    fun findActivePopupCandidate_legacyDismissedId_showsNewerInstance() {
        val currentNews = DeveloperNewsItem(
            id = "iTune",
            title = "New Announcement",
            message = "Important update",
            showPopup = true,
            active = true,
            timestamp = 1795153013943L,
            priority = 200
        )
        val items = listOf(currentNews)

        // Legacy dismissal only had the bare id "iTune", lastDismissedTimestamp was 0L
        val candidate = DeveloperNewsManager.findActivePopupCandidate(
            items = items,
            dismissedKeys = setOf("iTune"),
            lastDismissedTimestamp = 0L
        )

        // The specific instance "iTune_1795153013943" was not dismissed, so it displays
        assertNotNull(candidate)
        assertEquals("iTune", candidate?.id)
    }
}

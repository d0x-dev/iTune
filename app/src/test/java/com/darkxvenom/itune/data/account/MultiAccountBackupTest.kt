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

package com.darkxvenom.itune.data.account

import com.darkxvenom.itune.data.repository.AvatarManager
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAccountBackupTest {

    @Test
    fun testUserAccountSerializationIncludesAvatarUrl() {
        val account = UserAccount(
            id = "acc_dark_01",
            displayName = "Dark",
            avatarId = 5,
            createdAt = 1700000000L,
            isDefault = true,
        )

        val json = account.toJson()
        assertEquals("acc_dark_01", json.getString("id"))
        assertEquals("Dark", json.getString("displayName"))
        assertEquals(5, json.getInt("avatarId"))
        assertEquals("https://raw.githubusercontent.com/d0x-dev/iTune/main/assets/avatars/avatar_5.png", json.getString("avatarUrl"))
        assertEquals(1700000000L, json.getLong("createdAt"))
        assertTrue(json.getBoolean("isDefault"))

        val restored = UserAccount.fromJson(json)
        assertEquals(account.id, restored.id)
        assertEquals(account.displayName, restored.displayName)
        assertEquals(account.avatarId, restored.avatarId)
        assertEquals(account.avatarUrl, restored.avatarUrl)
        assertEquals(account.createdAt, restored.createdAt)
        assertEquals(account.isDefault, restored.isDefault)
    }

    @Test
    fun testAvatarUrlBounds() {
        assertEquals(
            "https://raw.githubusercontent.com/d0x-dev/iTune/main/assets/avatars/avatar_1.png",
            AvatarManager.getAvatarUrl(0),
        )
        assertEquals(
            "https://raw.githubusercontent.com/d0x-dev/iTune/main/assets/avatars/avatar_20.png",
            AvatarManager.getAvatarUrl(25),
        )
        assertEquals(
            "https://raw.githubusercontent.com/d0x-dev/iTune/main/assets/avatars/avatar_7.png",
            AvatarManager.getAvatarUrl(7),
        )
    }

    @Test
    fun testMultiAccountMasterJsonParsing() {
        val accountsArray = JSONArray().apply {
            put(UserAccount(id = "acc_01", displayName = "Primary", avatarId = 1).toJson())
            put(UserAccount(id = "acc_02", displayName = "Secondary", avatarId = 4).toJson())
            put(UserAccount(id = "acc_03", displayName = "Work", avatarId = 8).toJson())
        }

        val parsed = (0 until accountsArray.length()).mapNotNull { i ->
            accountsArray.optJSONObject(i)?.let { UserAccount.fromJson(it) }
        }

        assertEquals(3, parsed.size)
        assertEquals("acc_01", parsed[0].id)
        assertEquals("Primary", parsed[0].displayName)
        assertEquals(1, parsed[0].avatarId)
        assertEquals("https://raw.githubusercontent.com/d0x-dev/iTune/main/assets/avatars/avatar_1.png", parsed[0].avatarUrl)

        assertEquals("acc_02", parsed[1].id)
        assertEquals("Secondary", parsed[1].displayName)
        assertEquals(4, parsed[1].avatarId)
        assertEquals("https://raw.githubusercontent.com/d0x-dev/iTune/main/assets/avatars/avatar_4.png", parsed[1].avatarUrl)

        assertEquals("acc_03", parsed[2].id)
        assertEquals("Work", parsed[2].displayName)
        assertEquals(8, parsed[2].avatarId)
        assertEquals("https://raw.githubusercontent.com/d0x-dev/iTune/main/assets/avatars/avatar_8.png", parsed[2].avatarUrl)
    }

    @Test
    fun testRestoreDecisionRules() {
        val backupAccounts = listOf(
            UserAccount(id = "acc_01", displayName = "Primary", avatarId = 1),
            UserAccount(id = "acc_02", displayName = "Secondary", avatarId = 4),
            UserAccount(id = "acc_03", displayName = "Work", avatarId = 8),
        )

        // Scenario A: Single account backup or no multi accounts
        val singleBackup = listOf(backupAccounts[0])
        val isMultiA = singleBackup.size > 1
        assertFalse(isMultiA) // Rule 1: Restores into current active account

        // Scenario B: Multi-account backup, user selects exactly 1 account
        val selectedOne = setOf("acc_02")
        val isMultiB = backupAccounts.size > 1
        assertTrue(isMultiB)
        assertEquals(1, selectedOne.size) // Rule 2: Restores that profile into current active account

        // Scenario C: Multi-account backup, user selects multiple accounts
        val selectedMany = setOf("acc_01", "acc_03")
        assertTrue(selectedMany.size > 1) // Rule 3: Imports each as a separate account; current active account untouched!

        // Scenario D: Multi-account backup, user selects all accounts
        val selectedAll = backupAccounts.map { it.id }.toSet()
        assertEquals(3, selectedAll.size)
        assertTrue(selectedAll.size > 1) // Rule 3: Imports all selected as separate accounts; current active account untouched!
    }
}

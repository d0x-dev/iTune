package com.darkxvenom.itune.ui.home

import com.darkxvenom.itune.data.model.HomeFeedItem
import com.darkxvenom.itune.data.model.HomeFeedShelf
import com.darkxvenom.itune.data.model.HomeItemKind
import com.darkxvenom.itune.data.model.HomeShelfGroup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeShelfOrderingTest {

    private fun songs(key: String, title: String, group: HomeShelfGroup = HomeShelfGroup.FEED) = HomeFeedShelf(
        key, title, group = group,
        items = listOf(HomeFeedItem(HomeItemKind.SONG, "s-$key", videoId = "v-$key")),
    )

    private fun albums(key: String, title: String, group: HomeShelfGroup = HomeShelfGroup.FEED) = HomeFeedShelf(
        key, title, group = group,
        items = listOf(HomeFeedItem(HomeItemKind.ALBUM, "a-$key", browseId = "MPREb_$key")),
    )

    @Test
    fun signedIn_startsWithQuickPicksThenPersonal() {
        val remote = listOf(albums("h1", "Albums for you"), songs("h2", "Quick picks"))
        val personal = listOf(songs("p1", "Recently Played", HomeShelfGroup.PERSONAL))
        val out = orderHomeShelves(remote, personal, emptyList(), emptyList(), signedIn = true)
        assertEquals(listOf("h2", "p1", "h1"), out.map { it.key })
    }

    @Test
    fun signedOut_startsWithTrendingSongs() {
        val remote = listOf(
            albums("h1", "Mixed for you"),
            songs("t1", "Trending", HomeShelfGroup.TRENDING),
        )
        val out = orderHomeShelves(remote, emptyList(), emptyList(), emptyList(), signedIn = false)
        assertEquals("t1", out.first().key)
        assertTrue(out.any { it.key == "h1" })
    }

    @Test
    fun podcastsComeAfterFeedShelves() {
        val remote = listOf(
            albums("pod", "Podcasts", HomeShelfGroup.PODCASTS),
            albums("h1", "Albums for you"),
            songs("h2", "Quick picks"),
        )
        val out = orderHomeShelves(remote, emptyList(), emptyList(), emptyList(), signedIn = true)
        assertEquals(listOf("h2", "h1", "pod"), out.map { it.key })
    }

    @Test
    fun explicitFilter_dropsEmptyShelves() {
        val shelf = HomeFeedShelf(
            "k", "t",
            items = listOf(HomeFeedItem(HomeItemKind.SONG, "x", videoId = "v", isExplicit = true)),
        )
        assertTrue(listOf(shelf).withoutExplicit(hide = true).isEmpty())
        assertEquals(1, listOf(shelf).withoutExplicit(hide = false).size)
    }
}

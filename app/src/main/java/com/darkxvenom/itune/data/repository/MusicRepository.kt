package com.darkxvenom.itune.data.repository

import androidx.compose.ui.graphics.Color
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.model.BrowseCategoryItem
import com.darkxvenom.itune.data.model.CoverArtType
import com.darkxvenom.itune.data.model.FeaturedCard
import com.darkxvenom.itune.data.model.LibraryMenuItem
import com.darkxvenom.itune.data.model.LibraryPinItem
import com.darkxvenom.itune.data.model.LibraryRecentAlbum
import com.darkxvenom.itune.data.model.Playlist
import com.darkxvenom.itune.data.model.RadioStation
import com.darkxvenom.itune.data.model.SearchCategory
import com.darkxvenom.itune.data.model.SearchCategoryType
import com.darkxvenom.itune.data.model.Track

object MusicRepository {

    val featuredCards = listOf(
        FeaturedCard(
            id = "f1",
            category = "UPDATED PLAYLIST",
            title = "Today's Hits",
            subtitle = "Apple Music Hits",
            caption = "It feels \"Illegal,\" but it's not. Hear the PinkPantheress single in Spatial Audio now.",
            heroType = CoverArtType.TODAYS_HITS_HERO,
            accentColor = Color(0xFFFFD159),
            gradientColors = listOf(Color(0xFF2C241E), Color(0xFF191614), Color(0xFF0C0A09)),
            imageUrl = "https://is1-ssl.mzstatic.com/image/thumb/Features125/v4/4a/01/70/4a017006-2dc0-5906-8d82-c2cb8b420235/mza_1033878564177727415.png/1000x1000bb.webp"
        ),
        FeaturedCard(
            id = "f2",
            category = "NEW ALBUM",
            title = "Never Enough",
            subtitle = "The Weeknd",
            caption = "A thrilling descent into midnight alt-r&b. Experience in Spatial Audio with Dolby Atmos.",
            heroType = CoverArtType.NEVER_ENOUGH_HERO,
            accentColor = Color(0xFFFF3B30),
            gradientColors = listOf(Color(0xFF330B0B), Color(0xFF1D0606), Color(0xFF0A0505)),
            imageUrl = "https://is1-ssl.mzstatic.com/image/thumb/Music211/v4/a4/d0/2d/a4d02dc2-80ea-3129-8e2b-fbc6d3ad93bc/24UMGIM88531.rgb.jpg/1000x1000bb.webp"
        ),
        FeaturedCard(
            id = "f3",
            category = "FEATURED PLAYLIST",
            title = "A-List Pop",
            subtitle = "Apple Music Pop",
            caption = "The defining sounds of today's pop universe, updated weekly in pure Lossless.",
            heroType = CoverArtType.ALIST_POP_HERO,
            accentColor = Color(0xFF5AC8FA),
            gradientColors = listOf(Color(0xFF122C3D), Color(0xFF091621), Color(0xFF060B10)),
            imageUrl = "https://is1-ssl.mzstatic.com/image/thumb/Features115/v4/eb/fa/1c/ebfa1cca-54ee-57c2-f1df-bb3470717208/mza_1495937177559124430.png/1000x1000bb.webp"
        )
    )

    val latestSongColumns: List<List<Track>> = listOf(
        // Column 1 (Directly from user screenshot)
        listOf(
            Track(
                id = "t1",
                title = "Dis Badman",
                artist = "Sammy Virji, Champion & IRAH",
                isExplicit = false,
                coverType = CoverArtType.DIS_BADMAN
            ),
            Track(
                id = "t2",
                title = "Victory Lap Four",
                artist = "Fred again.., Skepta, PlaqueBoyM...",
                isExplicit = true,
                coverType = CoverArtType.VICTORY_LAP
            ),
            Track(
                id = "t3",
                title = "ESPÍRITU",
                artist = "Jotaerre",
                isExplicit = true,
                coverType = CoverArtType.ESPIRITU
            ),
            Track(
                id = "t4",
                title = "Schizo (And The Little Girl)",
                artist = "Schizo",
                isExplicit = false,
                coverType = CoverArtType.SCHIZO
            )
        ),
        // Column 2 (Secondary horizontal peek)
        listOf(
            Track(
                id = "t5",
                title = "Blinding Lights",
                artist = "The Weeknd",
                isExplicit = false,
                coverType = CoverArtType.BLINDING_LIGHTS
            ),
            Track(
                id = "t6",
                title = "Paint The Town Red",
                artist = "Doja Cat",
                isExplicit = true,
                coverType = CoverArtType.PAINT_THE_TOWN
            ),
            Track(
                id = "t7",
                title = "Starboy",
                artist = "The Weeknd, Daft Punk",
                isExplicit = true,
                coverType = CoverArtType.STARBOY
            ),
            Track(
                id = "t8",
                title = "vampire",
                artist = "Olivia Rodrigo",
                isExplicit = false,
                coverType = CoverArtType.VAMPIRE
            )
        )
    )

    val essentialPlaylists = listOf(
        Playlist(
            id = "p1",
            title = "Superbloom",
            curator = "Apple Music",
            description = "Ethereal acoustic, dreamy pop, and indie reveries.",
            gradientColors = listOf(Color(0xFF386641), Color(0xFF1F3824), Color(0xFF111E13))
        ),
        Playlist(
            id = "p2",
            title = "danceXL",
            curator = "Apple Music Dance",
            description = "The heavyweight anthems electrifying club culture.",
            gradientColors = listOf(Color(0xFF5E17EB), Color(0xFF340B8C), Color(0xFF17043F))
        ),
        Playlist(
            id = "p3",
            title = "Rap Life",
            curator = "Apple Music Hip-Hop",
            description = "Where hip-hop culture lives, speaks, and breathes.",
            gradientColors = listOf(Color(0xFF8B0000), Color(0xFF4A0000), Color(0xFF1E0000))
        ),
        Playlist(
            id = "p4",
            title = "Chill Mix",
            curator = "Made for You",
            description = "Unwind to mellow rhythms and atmospheric textures.",
            gradientColors = listOf(Color(0xFF0F3A5D), Color(0xFF072136), Color(0xFF03101C))
        )
    )

    val radioStations = listOf(
        RadioStation(
            id = "r1",
            name = "Apple Music 1",
            host = "Zane Lowe",
            showName = "The Zane Lowe Show",
            isLive = true,
            badgeColor = Color(0xFFFA2D48),
            backgroundGradient = listOf(Color(0xFFFA2D48), Color(0xFF990D24))
        ),
        RadioStation(
            id = "r2",
            name = "Apple Music Hits",
            host = "Estelle",
            showName = "The 90s & 2000s Spotlight",
            isLive = true,
            badgeColor = Color(0xFFFF9F0A),
            backgroundGradient = listOf(Color(0xFFFF9F0A), Color(0xFFA35F00))
        ),
        RadioStation(
            id = "r3",
            name = "Apple Music Country",
            host = "Kelleigh Bannen",
            showName = "Today's Country Today",
            isLive = true,
            badgeColor = Color(0xFF30D158),
            backgroundGradient = listOf(Color(0xFF30D158), Color(0xFF15752E))
        )
    )

    private const val GITHUB_IMAGE_BASE_URL = "https://raw.githubusercontent.com/d0x-dev/iTune/main/assets/images"

    private const val UNSPLASH_SUFFIX = "auto=format&fit=crop&w=600&q=80"

    val searchCategories = listOf(
        SearchCategory(
            id = "c1",
            title = "Chill",
            type = SearchCategoryType.CHILL,
            backgroundColor = Color(0xFF5A758D),
            imageUrl = "https://images.unsplash.com/photo-1501386761578-eac5c94b800a?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c2",
            title = "Community",
            type = SearchCategoryType.COMMUNITY,
            backgroundColor = Color(0xFF8F7523),
            imageUrl = "https://images.unsplash.com/photo-1508700115892-45ecd05ae2ad?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c3",
            title = "Energize",
            type = SearchCategoryType.ENERGIZE,
            backgroundColor = Color(0xFFA89547),
            imageUrl = "https://images.unsplash.com/photo-1514525253161-7a46d19cd819?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c4",
            title = "Feel good",
            type = SearchCategoryType.FEEL_GOOD,
            backgroundColor = Color(0xFF5B8E60),
            imageUrl = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c5",
            title = "Focus",
            type = SearchCategoryType.FOCUS,
            backgroundColor = Color(0xFF6B7A7A),
            imageUrl = "https://images.unsplash.com/photo-1518609878373-06d740f60d8b?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c6",
            title = "Gaming",
            type = SearchCategoryType.GAMING,
            backgroundColor = Color(0xFF383E44),
            imageUrl = "https://images.unsplash.com/photo-1542751371-adc38448a05e?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c7",
            title = "Party",
            type = SearchCategoryType.PARTY,
            backgroundColor = Color(0xFF74528E),
            imageUrl = "https://images.unsplash.com/photo-1492684223066-81342ee5ff30?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c8",
            title = "Romance",
            type = SearchCategoryType.ROMANCE,
            backgroundColor = Color(0xFF9E422D),
            imageUrl = "https://images.unsplash.com/photo-1518199266791-5375a83190b7?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c9",
            title = "Sad",
            type = SearchCategoryType.SAD,
            backgroundColor = Color(0xFF535D5C),
            imageUrl = "https://images.unsplash.com/photo-1516589178581-6cd7833ae3b2?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c10",
            title = "Sleep",
            type = SearchCategoryType.SLEEP,
            backgroundColor = Color(0xFF4C3A7A),
            imageUrl = "https://images.unsplash.com/photo-1444703686981-a3abbc4d4fe3?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c11",
            title = "Workout",
            type = SearchCategoryType.WORKOUT,
            backgroundColor = Color(0xFFA35728),
            imageUrl = "https://images.unsplash.com/photo-1517838277536-f5f99be501cd?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c12",
            title = "African",
            type = SearchCategoryType.AFRICAN,
            backgroundColor = Color(0xFF10722C),
            imageUrl = "https://images.unsplash.com/photo-1534528741775-53994a69daeb?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c13",
            title = "Arabic",
            type = SearchCategoryType.ARABIC,
            backgroundColor = Color(0xFF984617),
            imageUrl = "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c14",
            title = "Bengali",
            type = SearchCategoryType.BENGALI,
            backgroundColor = Color(0xFF837E43),
            imageUrl = "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c15",
            title = "Pop",
            type = SearchCategoryType.POP,
            backgroundColor = Color(0xFFAD3869),
            imageUrl = "https://images.unsplash.com/photo-1511671782779-c97d3d27a1d4?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c16",
            title = "Rock",
            type = SearchCategoryType.ROCK,
            backgroundColor = Color(0xFF8F2929),
            imageUrl = "https://images.unsplash.com/photo-1498038432885-c6f3f1b912ee?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c17",
            title = "Hip-Hop",
            type = SearchCategoryType.HIP_HOP,
            backgroundColor = Color(0xFFBA5A20),
            imageUrl = "https://images.unsplash.com/photo-1509198397868-475647b2a1e5?$UNSPLASH_SUFFIX"
        ),
        SearchCategory(
            id = "c18",
            title = "Bollywood",
            type = SearchCategoryType.BOLLYWOOD,
            backgroundColor = Color(0xFF8B2F57),
            imageUrl = "https://images.unsplash.com/photo-1533174072545-7a4b6ad7a6c3?$UNSPLASH_SUFFIX"
        )
    )

    val libraryPins = listOf(
        LibraryPinItem("lp1", "Why Not More?", "$GITHUB_IMAGE_BASE_URL/lib_album_why_not_more.png"),
        LibraryPinItem("lp2", "R&B Now", "$GITHUB_IMAGE_BASE_URL/lib_album_rnb_now.png"),
        LibraryPinItem("lp3", "MIKE", "$GITHUB_IMAGE_BASE_URL/lib_album_mike.png"),
        LibraryPinItem("lp4", "Like A Ribbon", "$GITHUB_IMAGE_BASE_URL/lib_album_like_a_ribbon.png"),
        LibraryPinItem("lp5", "Earcandy", "$GITHUB_IMAGE_BASE_URL/lib_album_earcandy.png"),
        LibraryPinItem("lp6", "Jupiter", "$GITHUB_IMAGE_BASE_URL/lib_album_jupiter.png")
    )

    val libraryMenuItems = listOf(
        LibraryMenuItem("lm1", "Playlists", R.drawable.ic_lib_playlists),
        LibraryMenuItem("lm2", "Artists", R.drawable.ic_lib_artists),
        LibraryMenuItem("lm3", "Albums", R.drawable.ic_lib_albums),
        LibraryMenuItem("lm4", "History", R.drawable.ic_settings_history)
    )

    val libraryRecentAlbums = listOf(
        LibraryRecentAlbum("lr1", "Eclipse Aura", "Kavinsky", "$GITHUB_IMAGE_BASE_URL/lib_recent_headphones.png"),
        LibraryRecentAlbum("lr2", "Sakura Dimension", "Komorebi", "$GITHUB_IMAGE_BASE_URL/lib_recent_sakura.png"),
        LibraryRecentAlbum("lr3", "Why Not More?", "Gilles Peterson", "$GITHUB_IMAGE_BASE_URL/lib_album_why_not_more.png"),
        LibraryRecentAlbum("lr4", "Like A Ribbon", "John Carroll Kirby", "$GITHUB_IMAGE_BASE_URL/lib_album_like_a_ribbon.png")
    )

    val browseCategories = listOf(
        BrowseCategoryItem("b1", "Chill", "$GITHUB_IMAGE_BASE_URL/browse_composers.png", params = "ggMPOg1uX1JOQWZFeDByc2Jm"),
        BrowseCategoryItem("b2", "Commute", "$GITHUB_IMAGE_BASE_URL/browse_periods.png", params = "ggMPOg1uX044Z2o5WERLckpU"),
        BrowseCategoryItem("b3", "Diwali", "$GITHUB_IMAGE_BASE_URL/browse_genres.png", params = "ggMPOg1uX0lQVm1ablYxWml5"),
        BrowseCategoryItem("b4", "Energize", "$GITHUB_IMAGE_BASE_URL/browse_conductors.png", params = "ggMPOg1uX2lRZUZiMnNrQnJW"),
        BrowseCategoryItem("b5", "Feel good", "$GITHUB_IMAGE_BASE_URL/browse_orchestras.png", params = "ggMPOg1uXzZQbDB5eThLRTQ3"),
        BrowseCategoryItem("b6", "Focus", "$GITHUB_IMAGE_BASE_URL/browse_soloists.png", params = "ggMPOg1uX0NvNGNhWThMYWRh"),
        BrowseCategoryItem("b7", "Gaming", "$GITHUB_IMAGE_BASE_URL/browse_ensembles.png", params = "ggMPOg1uX3NmUVV4Vzl3WGQ0"),
        BrowseCategoryItem("b8", "Party", "$GITHUB_IMAGE_BASE_URL/browse_choirs.png", params = "ggMPOg1uX0pmQ0s2V0JRclZs"),
        BrowseCategoryItem("b9", "Romance", "$GITHUB_IMAGE_BASE_URL/browse_instruments.png", params = "ggMPOg1uX0FzQ2FhZWtUY211"),
        BrowseCategoryItem("b10", "Sad", "$GITHUB_IMAGE_BASE_URL/browse_spatial_audio.png", params = "ggMPOg1uX0JLQ0gySWZKZVY1"),
        BrowseCategoryItem("b11", "Sleep", "$GITHUB_IMAGE_BASE_URL/browse_classical.png", params = "ggMPOg1uX1MxaFQ3Z0JMZkN4"),
        BrowseCategoryItem("b12", "Workout", "$GITHUB_IMAGE_BASE_URL/browse_contemporary.png", params = "ggMPOg1uX09LWkhnTjRGRUJh")
    )
}

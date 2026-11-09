package com.darkxvenom.itune.data.repository

import androidx.compose.ui.graphics.Color
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.model.CoverArtType
import com.darkxvenom.itune.data.model.FeaturedCard
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
            gradientColors = listOf(Color(0xFF2C241E), Color(0xFF191614), Color(0xFF0C0A09))
        ),
        FeaturedCard(
            id = "f2",
            category = "NEW ALBUM",
            title = "Never Enough",
            subtitle = "The Weeknd",
            caption = "A thrilling descent into midnight alt-r&b. Experience in Spatial Audio with Dolby Atmos.",
            heroType = CoverArtType.NEVER_ENOUGH_HERO,
            accentColor = Color(0xFFFF3B30),
            gradientColors = listOf(Color(0xFF330B0B), Color(0xFF1D0606), Color(0xFF0A0505))
        ),
        FeaturedCard(
            id = "f3",
            category = "FEATURED PLAYLIST",
            title = "A-List Pop",
            subtitle = "Apple Music Pop",
            caption = "The defining sounds of today's pop universe, updated weekly in pure Lossless.",
            heroType = CoverArtType.ALIST_POP_HERO,
            accentColor = Color(0xFF5AC8FA),
            gradientColors = listOf(Color(0xFF122C3D), Color(0xFF091621), Color(0xFF060B10))
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

    val searchCategories = listOf(
        SearchCategory(
            id = "c1",
            title = "Apple Music Live",
            type = SearchCategoryType.APPLE_MUSIC_LIVE,
            backgroundColor = Color(0xFF18181A),
            imageRes = R.drawable.card_apple_music_live
        ),
        SearchCategory(
            id = "c2",
            title = "Hits",
            type = SearchCategoryType.HITS,
            backgroundColor = Color(0xFFE5AC15),
            imageRes = R.drawable.card_hits
        ),
        SearchCategory(
            id = "c3",
            title = "Alternative",
            type = SearchCategoryType.ALTERNATIVE,
            backgroundColor = Color(0xFFC4B526),
            imageRes = R.drawable.card_alternative
        ),
        SearchCategory(
            id = "c4",
            title = "Pop",
            type = SearchCategoryType.POP,
            backgroundColor = Color(0xFFE63B6B),
            imageRes = R.drawable.card_pop
        ),
        SearchCategory(
            id = "c5",
            title = "Hip-Hop",
            type = SearchCategoryType.HIP_HOP,
            backgroundColor = Color(0xFF5087EC),
            imageRes = R.drawable.card_hip_hop
        ),
        SearchCategory(
            id = "c6",
            title = "Country",
            type = SearchCategoryType.COUNTRY,
            backgroundColor = Color(0xFFD97724),
            imageRes = R.drawable.card_country
        ),
        SearchCategory(
            id = "c7",
            title = "Dance",
            type = SearchCategoryType.DANCE,
            backgroundColor = Color(0xFF0FB854),
            imageRes = R.drawable.card_dance
        ),
        SearchCategory(
            id = "c8",
            title = "Rock",
            type = SearchCategoryType.ROCK,
            backgroundColor = Color(0xFFDC4E33),
            imageRes = R.drawable.card_rock
        ),
        SearchCategory(
            id = "c9",
            title = "Latin",
            type = SearchCategoryType.LATIN,
            backgroundColor = Color(0xFFD83375),
            imageRes = R.drawable.card_latin
        ),
        SearchCategory(
            id = "c10",
            title = "R&B",
            type = SearchCategoryType.RNB,
            backgroundColor = Color(0xFF826FE3),
            imageRes = R.drawable.card_rnb
        ),
        SearchCategory(
            id = "c11",
            title = "Classical",
            type = SearchCategoryType.CLASSICAL,
            backgroundColor = Color(0xFF28559A)
        ),
        SearchCategory(
            id = "c12",
            title = "Chill",
            type = SearchCategoryType.CHILL,
            backgroundColor = Color(0xFF2E7D7A)
        )
    )
}

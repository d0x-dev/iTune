# iTune feature catalog

This page inventories user-facing features in the current application. Some features depend on Android version, device audio hardware, network access, upstream providers, or optional account sign-in.

## Discover and search

- Search music and browse songs, artists, albums, playlists, and supported collections.
- Browse home shelves, charts, moods, genres, new releases, and other catalog groupings exposed by the active source.
- Open artist and album pages, view track lists, and navigate between related artists and releases.
- Filter supported search results by content type and region/language settings.
- Keep a local search history, pause saving new queries, or clear saved searches.

## Playback and queue

- Stream music through the Media3 playback engine.
- Use play/pause, seek, previous/next, repeat, shuffle, and queue controls.
- Add, remove, reorder, and play queued songs.
- Show current artwork, track and artist details, progress, and the active output device.
- Support radio playback and state updates in the player.
- Resume from a saved playback session when the app restores its player state.
- Configure crossfade, AutoMix, Afterglow, permanent shuffle, pause-at-zero-volume, and auto-skip after playback errors.
- Schedule recurring sleep hours with selected days and start/end times.
- Enable offline mode to require a local downloaded copy for playback.

## Lyrics

- Search and display lyrics from an ordered set of providers, with controls to reorder or disable providers.
- Follow time-synchronized lyrics when timing data is available.
- Open the full lyrics view, adjust supported lyric timing, and share selected lines as a lyric card.
- Translate lyrics through supported providers after configuring a provider, endpoint, model, and API key.
- Choose whether translated text replaces the original where supported.
- Configure lyric language and translation behavior in Lyrics settings.

## Audio engine and output

- Configure the in-app equalizer and supported audio effects: Dolby Atmos, 8D audio, spatial audio, low-pass filtering, DJ processing, and normalization.
- Inspect the signal pipeline and active output device.
- Use pure-audio bypass when the Android/device audio path supports it.
- Configure USB bit-perfect output, supported PCM format, and output behavior for compatible devices.
- Show Bluetooth output details where Android permissions and the connected device make them available.
- Audio processing may be unavailable or bypassed on some devices, routes, codecs, or apps.

## Downloads, cache, and storage

- Download available audio streams from supported song actions and choose a default download quality.
- Save audio to the iTune downloads location and show download progress and completion notifications.
- Cache songs during playback for offline use, set cache quality and storage limits, and clear cached songs.
- Configure Wi-Fi-only downloads, cellular streaming quality, and automatic downloads for newly liked or added music.
- Clear temporary artwork and network buffer caches.
- Playback and download availability depend on the upstream source and the Android media-storage rules for the device.

## Library and listening history

- Save favorites and keep supported likes on the device when signed out.
- Organize and sync supported library information when connected to an account.
- Record listening history and update listening profile/statistics when enabled.
- Pause or clear listening history and reset related taste data.
- Import supported playlist links and use supported playlist/library actions.

## Radio and connected features

- Browse and search online radio stations, select a region, play stations, and save favorites.
- Create or join iShare listening sessions using supported connection modes.
- Optionally connect Last.fm for scrobbling.
- Optionally connect Discord for listening Rich Presence.
- Sign in to supported Google/account features and manage the connected profile.
- Import compatible shared links, text, audio, or video through Android share intents; use the music-identification flow where supported.

## Player appearance and Android surfaces

- Choose the dynamic or static player presentation and configure artwork-driven player colors.
- Customize the home top-card style, app font, splash screen, animated cover art, tactile haptics, and liquid-glass chrome.
- Keep the player’s own SF-style typography independent from the app font setting.
- Add the iTune home-screen player widget with playback controls, favorite action, artwork visibility, compact/standard/expanded layout, style selection, and optional blurred artwork background.
- Use the optional Dynamic Island-style floating player and Always-On Display surface.
- Use playback notifications and lock-screen/media controls provided by Android.

## Settings, privacy, and maintenance

- Configure content language/country, explicit-content filtering, offline behavior, profile name, and supported network proxy settings.
- Pause search/listening history and enable screenshot protection.
- Read developer news and check GitHub releases for app updates.
- Create and restore password-encrypted `.itune` backups for selected account, settings, library, history, audio-effect, and statistics data.
- Configure scheduled encrypted backups in the app’s backup directory.

## Device and service limitations

- Online search, lyrics, radio, artwork, account, and update features need network access.
- Some third-party providers may change, rate-limit requests, or be unavailable in particular regions.
- Android versions differ in notification, Bluetooth, media-storage, and overlay behavior.
- Unsupported hardware may hide or limit audio effects; bit-perfect output requires a compatible output path.
- Optional integrations require the corresponding account and user consent.

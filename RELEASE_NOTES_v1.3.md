<img width="1671" height="941" alt="image" src="https://github.com/user-attachments/assets/de2b1b0d-2f3d-4d8c-800b-b98e68767616" />

# iTune v1.3 — What's New

## 🎨 Apple-Style Home Cards (Top Picks)

The **Top Picks for You** section on the home screen now uses redesigned Apple-style cards inspired by the Apple Music home screen layout:

- **Tag badge moved outside the card** — The "Made For You" / "New" / category tag now floats **above** the card title, outside the card boundary, matching the clean Apple Music card layout.
- **Reduced corner radius** — Card corners are now subtler (8dp) so the cards feel sharp and modern, not overly rounded like older Material cards.
- **Frosted blur area** — The bottom blur overlay behind the title and subtitle is deeper (more opaque), so text is always readable even on bright album artwork.
- **Separate artists, albums and view counts** — Artist names, album names, and play counts are now displayed as distinct lines, not crammed into a single subtitle.
- **Three selectable card styles** — Settings lets you choose between card styles for the Top section: Default, Apple, and Legacy.

## 🧭 Liquid Glass Navigation Bar & Mini-Player

- **Floating Liquid Glass NavBar** — The navigation bar now uses a translucent, blurred "liquid glass" pill that floats above the content. It has spring physics: the active tab bubble expands on press and snaps back on release.
- **Bubble size tuned** — The active tab bubble is sized to feel natural and not oversized: width 66dp (rest) / hold, height 42dp (rest) with a subtle scale on press (10% scale boost instead of the previous 45% ballooning effect).
- **Floating Mini-Player** — A compact floating mini-player sits above the NavBar, showing the current track artwork, title, artist, and playback controls (play/pause, skip).
- **Liquid Glass setting** — A new toggle in Settings (Appearance) lets you switch between the classic bottom nav and the new Liquid Glass NavBar + mini-player combo.

## ⚙️ Settings Screen — Maximised & Minimised Layouts

- **Two settings layouts** — Settings now supports a **Maximised** layout (full expanded view) and a **Minimised** layout (compact grouped card rows with no counts, no extra spacing).
- **Minimised grouped layout fix** — The minimised settings layout correctly groups all options into clean card sections with no blank padding, no option count labels, and no extra gaps between groups — matching the compact look in the reference screenshot.
- **Appearance section with Liquid Glass toggle** — Appearance settings now include the Liquid Glass NavBar toggle alongside theme and display options.

## 🎵 JioSaavn Integration

- **JioSaavn logo in settings** — The JioSaavn streaming provider toggle now shows the official JioSaavn logo instead of a generic icon.
- **JioSaavn quality upgrade** — Added manual quality upgrade action for JioSaavn streams. iTune can now intelligently upgrade cached Saavn tracks to higher bitrate versions on demand.
- **Saavn quality glow label** — The player shows a quality badge/glow indicator when streaming via JioSaavn.
- **Multi-artist credit fix** — JioSaavn tracks with multiple artists now show each artist name correctly rather than merging them into a single credit string.

## 🔊 Audio Engine & Lossless

- **Lossless icon update** — The lossless quality indicator icons in the player have updated scaling and cleaner line weights.
- **Channel count check fix** — The Audio Engine popup no longer incorrectly disables the Atmos/spatial audio toggle when the stream has the right channel count.
- **Audio signal pipeline tracing** — Internal audio stream tracing added so playback transitions, buffer state, and stream source changes are logged for debugging.
- **Volume bar** — Added a left–right volume bar and Saavn quality glow UI in the player controls area.

## 🎪 Festival Mode

- **Festival version targeting** — Festival mode (the special themed UI shown during music festivals) now activates only on specific app version ranges, so a festival banner intended for 1.3 won't appear on older builds.
- **Festival color** — Changed the festival accent color from red to purple for a more stage-like look.
- **Festival data updates** — `festivals.json` updated with current festival schedules and status.

## 🔐 Google / YouTube Music Sign-In

- **Stable WebView sign-in** — The in-app Google sign-in screen reliably captures the YouTube Music session cookie after login. The auto-capture delay is calibrated to give the WebView enough time to fully settle before reading the session.
- **Library sync after login** — After a successful Google sign-in, iTune immediately syncs your YouTube Music library: liked songs, playlists, liked artists, and listening history all appear in the Library tab.
- **Account menu fallback** — The session verification step checks both YouTube Music library endpoints AND the account menu to confirm the account is authenticated.
- **Device clock warning** — If your device's date/time is out of sync with Google's servers, iTune shows a clear error message telling you to enable Automatic date & time and try again.

## 🏠 Home Feed Improvements

- **Google auth clock patch** — Fixed a race condition where the home feed could fail to load personalised content when the device clock drifted relative to Google's servers.
- **Home parsing fix** — Improved parsing of home feed sections so artist names, album names, and play counts display correctly as separate fields.

---

> **Installation:** Sideload `iTune_v1.3.apk` (phone) or `iTune_v1.3_TV.apk` (Android TV / Fire TV).  
> Requires Android 8.0+. No Play Store account needed.

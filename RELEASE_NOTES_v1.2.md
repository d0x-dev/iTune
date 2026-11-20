<img width="1671" height="941" alt="image" src="https://github.com/user-attachments/assets/2bff865b-b204-4281-82f1-a1e6b58f6d2a" />

# iTune 1.2 — Android TV Experience, Multi-Account Profiles & Sequential Audio Downloader

Welcome to **iTune 1.2**! This release introduces dedicated Android TV support with a full Apple TV-inspired living room experience, multi-account profile management with separate avatars and selective backup/restore, a sequential 1-at-a-time high-fidelity audio downloader with self-cleaning notifications, frame-exact crossfade relay transitions, and enhanced full-bleed player visuals.

---

## 📥 Downloads

* 📱 **[Download iTune 1.2 Phone APK (iTune_v1.2.apk)](https://github.com/d0x-dev/iTune/releases/download/1.2/iTune_v1.2.apk)** — Optimized for Android Phones and Tablets (Android 5.0+).
* 📺 **[Download iTune 1.2 Android TV APK (iTune_v1.2_TV.apk)](https://github.com/d0x-dev/iTune/releases/download/1.2/iTune_v1.2_TV.apk)** — Tailored for Android TV, Google TV, and TV Box remotes.

---

## 🌟 What's New in Version 1.2

### 📺 Dedicated Android TV & Leanback Interface
- **Living Room Apple TV Design**: Tailored 10-foot UI with Apple TV-inspired curvature (8dp cards), top pill tab navigation, and animated welcome brand docking.
- **D-Pad & Remote Navigation**: Smooth focus tracking with natural zoom scaling and caption drops—no distracting border lines or artificial highlight rings.
- **Split-View TV Player**: Large album art or looping video canvas on the left, with real-time synchronized kinetic lyrics and playback queue on the right.
- **Auto-Hiding TV Controls**: Full control overlay smoothly fades out during uninterrupted playback and reappears instantly on any remote key press.
- **Dynamic Ambient Wash**: Real-time ambient background lighting that extracts vibrant color palettes from active album art to fill your TV screen.
- **TV Settings & Account Sync**: Sign in directly to your Google / YouTube Music accounts and sync imported Spotify playlists on the big screen.

### 👥 Multi-Account Profiles & Fast Switching
- **Isolated User Profiles**: Support for multiple profiles, each with its own independent name, unique UUID, custom avatar, and isolated library data.
- **"Who is Listening?" Screen**: Apple-styled profile selection portal with instant touch response, spring bounce animations, and haptic feedback.
- **Clean Playback Isolation**: Switching accounts automatically stops active audio and isolates queues so playback never bleeds between users.
- **Apple-Style Account Actions**: 3-dot options menu on profile cards with sleek modal dialogs for easy **Rename** and **Delete** actions.

### 💾 Selective Multi-Account Backup & Restore
- **All-in-One Account Backup**: Export your full library, user settings, profile metadata, and avatar configurations into a single portable backup file.
- **Selective Restore Modal**: When restoring a multi-account backup, choose between restoring individual profiles via dedicated checkboxes or importing into the currently active profile.
- **Safety Confirmations**: Clear, elegant prompt dialogs prevent accidental overwrites or profile deletions.

### 📥 Sequential High-Fidelity Audio Downloader
- **FIFO 1-at-a-Time Queue**: Replaced concurrent batch downloading with a strict sequential single-track queue. Download 100+ track albums smoothly without network bottlenecks, socket exhaustion, or memory crashes.
- **Single Unified Notification**: Both the foreground service and active progress share a single notification ID (`2000`). Eliminates duplicate notifications and the "downloading twice" issue.
- **Self-Cleaning Notifications**: As each track finishes, the ongoing notification automatically morphs into the next queued song (`Track X of Y`) without spamming or cluttering the notification drawer.
- **No Stuck Banners**: Completely cancels the foreground notification when downloads finish or cancel, ensuring no stuck `(0/0)` banners remain.
- **Interactive Notification Controls**: Live bitrate/speed readout, **Pause / Resume**, **Skip**, and **Cancel All** actions directly from the notification.
- **Full Media Tagging (`MediaTagger`)**: Pure OGG remuxing for WebM/Opus, embedded ID3v2.4 / MP4 / FLAC metadata, embedded synchronized/raw lyrics, and high-resolution album cover artwork.
- **Background WakeLock & WifiLock**: Prevents Android from suspending downloads when the screen turns off.

### 🎵 Enhanced Player, Album Art Zoom & Centered Lyrics
- **Fluid Square-to-Fullscreen Artwork Transition**: On track change, album artwork starts as a crisp square with a subtle glass border and shadow, then smoothly spring-zooms into full bleed without obscuring ambient background blur.
- **Centered Full-Bleed Lyrics Line**: Perfectly positioned between the track title/artist and seek bar for optimal reading and high contrast across all themes and canvases.
- **Seek Bar Spring Buffer Pulse**: Glowing red spring pulse animation rendered directly inside the seek bar while tracks buffer.
- **In-Player Playlist Management**: Add currently playing songs directly to any playlist from the 3-dot More menu.

### 🎚️ Frame-Exact Crossfade & AutoMix Transition Engine
- **Frame-Exact Audio Hand-Off**: Enabled `SeekParameters.EXACT` on the tail player to eliminate audible skips and keyframe misalignment on WebM/Opus streams.
- **Buffer Pre-Verification**: Tail player verifies minimum buffered audio headroom (`2.5s`) before initiating hand-off, avoiding mid-transition audio freezes.
- **Muted Synchronization & Drift Correction**: Compares main and tail player playback positions while the tail is muted and auto-corrects sync drift (`< 45ms`) before crossfading.
- **Equal-Sum Linear Blend & Stall Recovery**: Seamless volume transitions that automatically restore primary playback if network buffering occurs mid-transition.

### 🎪 Festivals & In-App Developer News
- **Celebration Banners**: Festive visual enhancements celebrating major cultural events (e.g. Diwali).
- **Markdown Developer News**: In-app announcements modal with rich Markdown formatting, image support, and unread indicator badges.

---

## ⚖️ Licensing & Author Credits

iTune is free and open-source software licensed under the **GNU General Public License v3.0 (GPL-3.0)**.
* **Originally created by**: DxV Studio ~~ Dark
* **Credits**: DxV Studio, AirBeats, and Dark

Feedback, issues, and contributions are welcome on [GitHub](https://github.com/d0x-dev/iTune).

package com.darkxvenom.itune.ishare

/**
 * Generates the Apple iPhone Dark Liquid Glass Web Player HTML/CSS/JS served by [IShareLanServer].
 * Supports real-time playback sync, drift speed adjustment, permission-aware transport controls,
 * interactive Queue drawer (play, reorder, remove, search & add), and connected devices viewer.
 */
internal fun buildIShareWebPlayerHtml(
    hostDisplayName: String,
    hostIp: String,
    port: Int,
    sessionId: String,
): String {
    val safeHost = hostDisplayName
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
    val deepLink = "airbeats://together?host=$hostIp&port=$port&sid=$sessionId"

    return """<!DOCTYPE html>
<html lang="en" data-theme="dark">
<head>
  <meta charset="UTF-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
  <title>iShare • Listen Together</title>
  <style>
    :root {
      --bg: #000000;
      --glass-bg: rgba(24, 24, 30, 0.72);
      --glass-elevated: rgba(38, 38, 46, 0.78);
      --pill-bg: rgba(255, 255, 255, 0.09);
      --border: rgba(255, 255, 255, 0.14);
      --text: #ffffff;
      --text-muted: #98989f;
      --accent: #fa2d48;
      --green: #30d158;
      --blue: #0a84ff;
      --btn-primary: #ffffff;
      --btn-text: #000000;
      --track-bg: rgba(255, 255, 255, 0.16);
      --track-fill: #ffffff;
    }
    [data-theme="light"] {
      --bg: #f2f2f7;
      --glass-bg: rgba(255, 255, 255, 0.78);
      --glass-elevated: rgba(255, 255, 255, 0.92);
      --pill-bg: rgba(0, 0, 0, 0.06);
      --border: rgba(0, 0, 0, 0.10);
      --text: #000000;
      --text-muted: #6e6e73;
      --accent: #fa2d48;
      --green: #34c759;
      --blue: #007aff;
      --btn-primary: #000000;
      --btn-text: #ffffff;
      --track-bg: rgba(0, 0, 0, 0.14);
      --track-fill: #000000;
    }
    * { box-sizing: border-box; margin: 0; padding: 0; -webkit-tap-highlight-color: transparent; }
    body {
      background: var(--bg);
      color: var(--text);
      font-family: -apple-system, BlinkMacSystemFont, "SF Pro Display", "Segoe UI", Roboto, sans-serif;
      min-height: 100vh;
      overflow-x: hidden;
      transition: background 0.25s ease, color 0.25s ease;
    }
    .ambient-backdrop {
      position: fixed; inset: -40px; z-index: 0; overflow: hidden; pointer-events: none;
    }
    .ambient-art {
      width: 100%; height: 100%; object-fit: cover;
      filter: blur(72px) saturate(165%) brightness(0.45);
      transform: scale(1.2);
      opacity: 0; transition: opacity 0.6s ease;
    }
    [data-theme="light"] .ambient-art { filter: blur(80px) saturate(140%) brightness(1.1); opacity: 0.25; }
    .ambient-scrim {
      position: fixed; inset: 0; z-index: 1; pointer-events: none;
      background: radial-gradient(circle at 20% 10%, rgba(250,45,72,0.16), transparent 52%),
                  radial-gradient(circle at 85% 25%, rgba(88,86,214,0.16), transparent 55%);
    }
    .shell {
      position: relative; z-index: 2;
      width: 100%; max-width: 1040px; min-height: 100vh;
      margin: 0 auto; padding: 16px 18px 34px;
      display: flex; flex-direction: column;
    }
    .glass-pill {
      background: var(--glass-bg);
      backdrop-filter: saturate(180%) blur(28px);
      -webkit-backdrop-filter: saturate(180%) blur(28px);
      border: 1px solid var(--border);
      box-shadow: 0 10px 30px rgba(0, 0, 0, 0.28);
    }
    .top-bar {
      display: flex; align-items: center; justify-content: space-between; gap: 10px;
      margin-bottom: 18px;
    }
    .brand-pill {
      display: inline-flex; align-items: center; gap: 10px;
      padding: 7px 14px 7px 8px; border-radius: 999px;
    }
    .brand-badge {
      width: 32px; height: 32px; border-radius: 10px;
      background: linear-gradient(135deg, #fa2d48, #ff5e7e);
      display: flex; align-items: center; justify-content: center;
      color: #fff; font-weight: 800;
    }
    .brand-title { font-size: 1.05rem; font-weight: 700; letter-spacing: -0.3px; }
    .top-actions { display: flex; align-items: center; gap: 8px; }
    .circle-btn {
      width: 42px; height: 42px; border-radius: 50%; border: 1px solid var(--border);
      background: var(--glass-bg); color: var(--text);
      backdrop-filter: blur(24px); -webkit-backdrop-filter: blur(24px);
      display: inline-flex; align-items: center; justify-content: center;
      cursor: pointer; position: relative; transition: transform 0.15s ease;
    }
    .circle-btn:active { transform: scale(0.92); }
    .circle-btn svg { width: 19px; height: 19px; fill: currentColor; }
    .device-pill {
      height: 42px; padding: 0 14px; border-radius: 999px;
      display: inline-flex; align-items: center; gap: 7px;
      font-size: 0.84rem; font-weight: 600; color: var(--text); cursor: pointer;
    }
    .live-dot {
      width: 8px; height: 8px; border-radius: 50%; background: var(--green);
      box-shadow: 0 0 10px var(--green); animation: pulse 1.8s infinite;
    }
    @keyframes pulse { 0%,100% { transform: scale(0.9); opacity: 0.75; } 50% { transform: scale(1.25); opacity: 1; } }

    .main-grid {
      display: grid; grid-template-columns: 1fr; gap: 22px; margin: auto 0; align-items: center;
    }
    @media (min-width: 840px) {
      .main-grid { grid-template-columns: 410px 1fr; gap: 44px; padding: 10px 12px; }
    }
    .art-card {
      position: relative; width: 100%; max-width: 410px; margin: 0 auto; aspect-ratio: 1 / 1;
      border-radius: 28px; overflow: hidden;
      background: var(--glass-bg); border: 1px solid var(--border);
      box-shadow: 0 24px 60px rgba(0,0,0,0.48);
    }
    .art-img { width: 100%; height: 100%; object-fit: cover; display: none; }
    .art-placeholder {
      width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
      color: var(--text-muted); font-size: 48px;
    }
    .live-chip {
      position: absolute; top: 14px; left: 14px;
      padding: 6px 12px; border-radius: 999px;
      background: rgba(0,0,0,0.58); backdrop-filter: blur(16px); -webkit-backdrop-filter: blur(16px);
      border: 1px solid rgba(255,255,255,0.14);
      display: inline-flex; align-items: center; gap: 7px;
      color: var(--green); font-size: 0.72rem; font-weight: 700; letter-spacing: 0.7px;
    }

    .player-panel {
      border-radius: 26px; padding: 22px 20px; display: flex; flex-direction: column; gap: 14px;
    }
    .track-title { font-size: 1.65rem; font-weight: 800; letter-spacing: -0.45px; line-height: 1.2; }
    .track-artist { font-size: 1.02rem; font-weight: 600; color: var(--text-muted); margin-top: 2px; }
    .status-line {
      display: flex; align-items: center; justify-content: space-between; gap: 10px; flex-wrap: wrap;
      font-size: 0.82rem; color: var(--text-muted); font-weight: 600;
    }
    .wave-anim { display: none; align-items: flex-end; gap: 2px; height: 13px; margin-left: 6px; }
    .wave-anim span {
      display: block; width: 3px; background: var(--green); border-radius: 2px;
      animation: wave 0.75s ease-in-out infinite alternate;
    }
    .wave-anim span:nth-child(2) { animation-delay: 0.25s; }
    .wave-anim span:nth-child(3) { animation-delay: 0.15s; }
    @keyframes wave { from { height: 28%; } to { height: 100%; } }

    .scrubber-wrap { position: relative; width: 100%; height: 26px; display: flex; align-items: center; }
    .scrubber-wrap.locked { opacity: 0.65; pointer-events: none; }
    .scrubber-track {
      position: absolute; left: 0; right: 0; height: 6px; border-radius: 99px;
      background: var(--track-bg); overflow: hidden;
    }
    .scrubber-fill { height: 100%; width: 0%; background: var(--accent); border-radius: 99px; }
    .scrubber-thumb {
      position: absolute; left: 0%; width: 14px; height: 14px; border-radius: 50%;
      background: #fff; transform: translate(-50%, 0); box-shadow: 0 2px 8px rgba(0,0,0,0.45);
      pointer-events: none;
    }
    .scrubber-wrap.locked .scrubber-thumb { display: none; }
    .seek-input { position: absolute; inset: 0; width: 100%; opacity: 0; cursor: pointer; z-index: 3; }
    .time-row {
      display: flex; justify-content: space-between; font-size: 0.76rem; font-weight: 600;
      color: var(--text-muted); font-variant-numeric: tabular-nums; margin-top: 2px;
    }

    .transport-row { display: flex; align-items: center; gap: 12px; margin-top: 4px; }
    .ctrl-circle {
      width: 50px; height: 50px; border-radius: 50%; border: 1px solid var(--border);
      background: var(--pill-bg); color: var(--text);
      display: inline-flex; align-items: center; justify-content: center;
      cursor: pointer; flex-shrink: 0; transition: transform 0.15s ease;
    }
    .ctrl-circle:active:not(:disabled) { transform: scale(0.92); }
    .ctrl-circle:disabled { opacity: 0.35; cursor: not-allowed; }
    .ctrl-circle svg { width: 22px; height: 22px; fill: currentColor; }
    .hero-btn {
      flex: 1; height: 52px; border-radius: 999px; border: none;
      background: var(--btn-primary); color: var(--btn-text);
      font-size: 0.96rem; font-weight: 700; cursor: pointer;
      display: inline-flex; align-items: center; justify-content: center; gap: 8px;
      box-shadow: 0 8px 24px rgba(0,0,0,0.28); transition: transform 0.15s ease;
    }
    .hero-btn:active { transform: scale(0.97); }
    .hero-btn svg { width: 21px; height: 21px; fill: currentColor; }

    .sub-controls { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
    .vol-row { display: flex; align-items: center; gap: 8px; flex: 1; max-width: 210px; }
    .vol-btn { background: none; border: none; color: var(--text); cursor: pointer; display: flex; padding: 4px; }
    .vol-btn svg { width: 18px; height: 18px; fill: currentColor; }
    .vol-slider {
      width: 100%; height: 5px; -webkit-appearance: none; appearance: none;
      border-radius: 99px; outline: none; cursor: pointer;
    }
    .vol-slider::-webkit-slider-thumb {
      -webkit-appearance: none; width: 13px; height: 13px; border-radius: 50%; background: var(--track-fill);
    }
    .sync-btn {
      padding: 8px 14px; border-radius: 999px; border: 1px solid var(--border);
      background: var(--pill-bg); color: var(--text); font-size: 0.8rem; font-weight: 700; cursor: pointer;
    }
    .actions-row { display: flex; gap: 10px; align-items: center; margin-top: 4px; }
    .open-app-btn {
      flex: 1; height: 48px; border-radius: 999px; background: var(--accent); color: #fff;
      text-decoration: none; font-size: 0.92rem; font-weight: 700;
      display: inline-flex; align-items: center; justify-content: center; gap: 8px;
    }
    .queue-pill-btn {
      height: 48px; padding: 0 16px; border-radius: 999px; border: 1px solid var(--border);
      background: var(--pill-bg); color: var(--text); font-size: 0.88rem; font-weight: 700;
      display: inline-flex; align-items: center; gap: 8px; cursor: pointer;
    }
    .queue-pill-btn svg { width: 18px; height: 18px; fill: currentColor; }

    /* Drawer / Modal Sheets (Apple Liquid Glass) */
    .backdrop-dim {
      position: fixed; inset: 0; background: rgba(0,0,0,0.58);
      backdrop-filter: blur(8px); -webkit-backdrop-filter: blur(8px);
      z-index: 40; opacity: 0; pointer-events: none; transition: opacity 0.22s ease;
    }
    .backdrop-dim.show { opacity: 1; pointer-events: auto; }
    .drawer {
      position: fixed; left: 0; right: 0; bottom: 0; max-height: 86vh; height: 82vh;
      max-width: 520px; margin: 0 auto; z-index: 50;
      border-top-left-radius: 28px; border-top-right-radius: 28px;
      display: flex; flex-direction: column; overflow: hidden;
      transform: translateY(105%); transition: transform 0.28s cubic-bezier(0.16, 1, 0.3, 1);
    }
    .drawer.show { transform: translateY(0); }
    @media (min-width: 840px) {
      .drawer { left: auto; top: 0; bottom: 0; right: 0; width: 420px; height: 100vh; max-height: none; border-radius: 0; transform: translateX(105%); }
      .drawer.show { transform: translateX(0); }
    }
    .drawer-header {
      padding: 18px 20px; display: flex; align-items: center; justify-content: space-between;
      border-bottom: 1px solid var(--border);
    }
    .drawer-body { flex: 1; overflow-y: auto; padding: 14px 16px 26px; display: flex; flex-direction: column; gap: 8px; }
    .perm-banner {
      margin: 12px 16px 0; padding: 10px 14px; border-radius: 14px; font-size: 0.79rem; font-weight: 600;
      display: none; align-items: center; gap: 8px;
    }
    .perm-banner.locked { display: flex; background: rgba(250,45,72,0.14); color: #ff6b81; border: 1px solid rgba(250,45,72,0.3); }
    .search-box-wrap { padding: 12px 16px 4px; display: none; gap: 8px; }
    .search-box-wrap.show { display: flex; }
    .search-input {
      flex: 1; height: 42px; border-radius: 999px; border: 1px solid var(--border);
      background: var(--pill-bg); color: var(--text); padding: 0 16px; font-size: 0.9rem; outline: none;
    }
    .search-go {
      height: 42px; padding: 0 16px; border-radius: 999px; border: none;
      background: var(--accent); color: #fff; font-weight: 700; cursor: pointer;
    }
    .q-item {
      display: flex; align-items: center; gap: 11px; padding: 9px 11px; border-radius: 16px;
      background: rgba(255,255,255,0.03); border: 1px solid transparent; transition: background 0.15s;
    }
    .q-item:hover { background: var(--pill-bg); border-color: var(--border); }
    .q-thumb { width: 44px; height: 44px; border-radius: 10px; object-fit: cover; background: var(--pill-bg); flex-shrink: 0; }
    .q-info { flex: 1; min-width: 0; cursor: pointer; }
    .q-title { font-size: 0.92rem; font-weight: 600; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .q-artist { font-size: 0.78rem; color: var(--text-muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
    .q-actions { display: flex; gap: 4px; flex-shrink: 0; }
    .mini-btn {
      border: 1px solid var(--border); background: var(--pill-bg); color: var(--text);
      border-radius: 999px; padding: 5px 10px; font-size: 0.74rem; font-weight: 700; cursor: pointer;
    }
    .mini-btn.danger { color: #ff6b81; }
    .toast {
      position: fixed; bottom: 24px; left: 50%; transform: translateX(-50%);
      background: var(--glass-elevated); color: var(--text); border: 1px solid var(--border);
      backdrop-filter: blur(20px); -webkit-backdrop-filter: blur(20px);
      padding: 9px 18px; border-radius: 999px; font-size: 0.83rem; font-weight: 700;
      opacity: 0; pointer-events: none; transition: opacity 0.2s ease; z-index: 90;
    }
    .toast.show { opacity: 1; }
  </style>
</head>
<body>
  <audio id="audioEl" preload="auto" playsinline></audio>
  <div class="ambient-backdrop"><img id="bgArt" class="ambient-art" alt="" /></div>
  <div class="ambient-scrim"></div>

  <div class="shell">
    <div class="top-bar">
      <div class="brand-pill glass-pill">
        <div class="brand-badge">♫</div>
        <span class="brand-title">iShare</span>
      </div>
      <div class="top-actions">
        <button class="device-pill glass-pill" onclick="openDevices()" title="Connected Devices">
          <span class="live-dot"></span>
          <span id="listenerBadge">1 device</span>
        </button>
        <button class="circle-btn" onclick="copyShareUrl()" title="Copy Session Link">
          <svg viewBox="0 0 24 24"><path d="M16 1H4c-1.1 0-2 .9-2 2v14h2V3h12V1zm3 4H8c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h11c1.1 0 2-.9 2-2V7c0-1.1-.9-2-2-2zm0 16H8V7h11v14z"/></svg>
        </button>
        <button id="themeBtn" class="circle-btn" onclick="toggleTheme()" title="Toggle Theme">☾</button>
      </div>
    </div>

    <div class="main-grid">
      <div class="art-card">
        <img id="artImg" class="art-img" alt="Album Artwork" />
        <div id="artEmpty" class="art-placeholder">♫</div>
        <div class="live-chip"><span class="live-dot"></span><span>LAN LIVE</span></div>
      </div>

      <div class="player-panel glass-pill">
        <div>
          <h1 id="trackTitle" class="track-title">Connecting...</h1>
          <div id="trackArtist" class="track-artist">Host: $safeHost</div>
        </div>

        <div class="status-line">
          <div style="display:flex;align-items:center;">
            <span id="statusText">Syncing with $safeHost...</span>
            <div id="waveAnim" class="wave-anim"><span></span><span></span><span></span></div>
          </div>
          <span id="permChip">Host Controls</span>
        </div>

        <div>
          <div class="scrubber-wrap locked" id="scrubberWrap">
            <div class="scrubber-track"><div id="fillBar" class="scrubber-fill"></div></div>
            <div id="thumbDot" class="scrubber-thumb"></div>
            <input type="range" id="seekInput" class="seek-input" min="0" max="100" step="0.1" value="0" />
          </div>
          <div class="time-row">
            <span id="curTime">00:00</span>
            <span id="durTime">--:--</span>
          </div>
        </div>

        <div class="transport-row">
          <button id="btnPrev" class="ctrl-circle" onclick="cmdPrev()" disabled title="Previous">
            <svg viewBox="0 0 24 24"><path d="M6 6h2v12H6zm3.5 6l8.5 6V6z"/></svg>
          </button>
          <button id="heroBtn" class="hero-btn" onclick="toggleAudio()">
            <span id="heroIcon"><svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg></span>
            <span id="heroText">Start Listening</span>
          </button>
          <button id="btnNext" class="ctrl-circle" onclick="cmdNext()" disabled title="Next">
            <svg viewBox="0 0 24 24"><path d="M6 18l8.5-6L6 6v12zM16 6v12h2V6h-2z"/></svg>
          </button>
        </div>

        <div class="sub-controls">
          <div class="vol-row">
            <button class="vol-btn" onclick="toggleMute()">
              <svg viewBox="0 0 24 24"><path d="M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02z"/></svg>
            </button>
            <input type="range" id="volInput" class="vol-slider" min="0" max="1" step="0.02" value="1" oninput="onVol(this.value)" />
          </div>
          <button class="sync-btn" onclick="syncNow()">↻ Sync Live</button>
        </div>

        <div class="actions-row">
          <a href="$deepLink" class="open-app-btn">Open in App</a>
          <button class="queue-pill-btn" onclick="openQueue()">
            <svg viewBox="0 0 24 24"><path d="M15 6H3v2h12V6zm0 4H3v2h12v-2zM3 16h8v-2H3v2zM17 6v8.18c-.31-.11-.65-.18-1-.18-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3V8h3V6h-5z"/></svg>
            <span id="queueBtnBadge">Queue</span>
          </button>
        </div>
      </div>
    </div>
  </div>

  <!-- Real-Time Queue & Search Drawer -->
  <div id="drawerOverlay" class="backdrop-dim" onclick="closeDrawers()"></div>
  <div id="queueDrawer" class="drawer glass-pill">
    <div class="drawer-header">
      <div>
        <div style="font-size:1.1rem;font-weight:700;">Playback Queue</div>
        <div id="queueCountText" style="font-size:0.78rem;color:var(--text-muted);">0 upcoming tracks</div>
      </div>
      <button class="circle-btn" onclick="closeDrawers()">✕</button>
    </div>
    <div id="queuePermBanner" class="perm-banner locked">🔒 Host locked playback controls</div>
    <div id="searchWrap" class="search-box-wrap">
      <input id="searchQuery" class="search-input" placeholder="Add song to queue..." onkeydown="if(event.key==='Enter')searchSongs()" />
      <button class="search-go" onclick="searchSongs()">Search</button>
    </div>
    <div id="searchResults" style="padding:0 16px;display:flex;flex-direction:column;gap:6px;"></div>
    <div id="queueList" class="drawer-body"></div>
  </div>

  <!-- Connected Devices Drawer -->
  <div id="devicesDrawer" class="drawer glass-pill">
    <div class="drawer-header">
      <div>
        <div style="font-size:1.1rem;font-weight:700;">Connected Devices</div>
        <div id="devicesCountText" style="font-size:0.78rem;color:var(--text-muted);">Live room participants</div>
      </div>
      <button class="circle-btn" onclick="closeDrawers()">✕</button>
    </div>
    <div id="devicesList" class="drawer-body"></div>
  </div>

  <div id="toast" class="toast"></div>

  <script>
    var audio = document.getElementById('audioEl');
    var seekInput = document.getElementById('seekInput');
    var fillBar = document.getElementById('fillBar');
    var thumbDot = document.getElementById('thumbDot');
    var scrubberWrap = document.getElementById('scrubberWrap');
    var volInput = document.getElementById('volInput');
    var pid = 'web_' + Math.random().toString(36).slice(2, 8);
    var isAudioActive = false, userPaused = false, seeking = false;
    var curSongId = '', serverOffset = 0, lastState = null, lastData = null;
    var lastActionAt = 0, queueCache = [], isQueueOpen = false, isDevicesOpen = false;
    var playSvg = '<svg viewBox="0 0 24 24"><path d="M8 5v14l11-7z"/></svg>';
    var pauseSvg = '<svg viewBox="0 0 24 24"><path d="M6 19h4V5H6v14zm8-14v14h4V5h-4z"/></svg>';

    function toast(m) {
      var t = document.getElementById('toast');
      t.innerText = m; t.classList.add('show');
      setTimeout(function(){ t.classList.remove('show'); }, 2200);
    }
    function esc(s) {
      return String(s || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;').replace(/'/g,'&#39;');
    }
    function fmtTime(sec) {
      sec = Math.max(0, Math.floor(sec || 0));
      var m = Math.floor(sec / 60), s = sec % 60;
      return (m < 10 ? '0' : '') + m + ':' + (s < 10 ? '0' : '') + s;
    }
    function canControl() {
      var st = lastData && (lastData.settings || (lastData.state && lastData.state.settings));
      return !!(st && st.allowGuestsToControlPlayback);
    }
    function canEditQueue() {
      var st = lastData && (lastData.settings || (lastData.state && lastData.state.settings));
      return !!(st && (st.allowGuestsToAddTracks || st.allowGuestsToControlPlayback));
    }

    // Register web device cleanly so it shows in Host's Device List
    fetch('/together/join', {
      method: 'POST',
      headers: {'Content-Type': 'application/json'},
      body: JSON.stringify({ name: 'Web Listener (' + (navigator.platform || 'Browser') + ')' })
    }).then(function(r){ return r.json(); }).then(function(d){
      if (d && d.participantId) pid = d.participantId;
      updateUi(d);
    }).catch(function(){});

    window.addEventListener('beforeunload', function() {
      try { navigator.sendBeacon('/together/leave', JSON.stringify({ participantId: pid })); } catch(e) {}
    });

    function toggleTheme() {
      var cur = document.documentElement.getAttribute('data-theme') || 'dark';
      var next = cur === 'dark' ? 'light' : 'dark';
      document.documentElement.setAttribute('data-theme', next);
      document.getElementById('themeBtn').innerText = next === 'dark' ? '☀' : '☾';
      onVol(volInput.value);
    }

    function sendAction(action, positionMs, extraData) {
      lastActionAt = Date.now();
      var body = { action: action, participantId: pid };
      if (positionMs != null) body.positionMs = Math.round(positionMs);
      if (extraData != null) body.extraData = extraData;
      fetch('/together/action', {
        method: 'POST', headers: {'Content-Type': 'application/json'}, body: JSON.stringify(body)
      }).then(function(r){ return r.json().then(function(j){ return { ok: r.ok, data: j }; }); })
      .then(function(res){
        if (!res.ok) toast((res.data && res.data.error) || 'Action locked by host');
        else if (res.data) updateUi(res.data);
      }).catch(function(){ toast('Connection hiccup'); });
    }

    function toggleAudio() {
      if (!isAudioActive) {
        isAudioActive = true; userPaused = false;
        if (lastState && lastState.songId) loadSong(lastState.songId, lastState);
        else { audio.src = '/together/stream?t=' + Date.now(); audio.play().catch(function(){}); }
        if (canControl() && lastState && !lastState.isPlaying) sendAction('play', (audio.currentTime || 0) * 1000);
        syncHeroBtn();
        return;
      }
      if (audio.paused) {
        userPaused = false; lastActionAt = Date.now();
        audio.play().catch(function(){});
        if (canControl()) sendAction('play', (audio.currentTime || 0) * 1000);
      } else {
        userPaused = true; lastActionAt = Date.now();
        audio.pause();
        if (canControl()) sendAction('pause', (audio.currentTime || 0) * 1000);
      }
      syncHeroBtn();
    }
    function cmdPrev() { if (!canControl()) return toast('Locked by host'); sendAction('previous'); }
    function cmdNext() { if (!canControl()) return toast('Locked by host'); sendAction('next'); }
    function syncNow() {
      if (!lastState) return;
      var posMs = (lastState.positionMs || 0) + (lastState.isPlaying ? Math.max(0, (Date.now() + serverOffset) - (lastState.updatedAt || Date.now())) : 0);
      if (audio && isFinite(audio.duration)) audio.currentTime = Math.min(audio.duration, posMs / 1000);
      toast('Synced live • ' + fmtTime(posMs / 1000));
    }

    function loadSong(id, st) {
      curSongId = id;
      audio.src = '/together/stream?id=' + encodeURIComponent(id) + '&t=' + Date.now();
      audio.load();
      audio.onloadedmetadata = function() {
        var posMs = (st.positionMs || 0) + (st.isPlaying ? Math.max(0, (Date.now() + serverOffset) - (st.updatedAt || Date.now())) : 0);
        if (posMs > 0 && isFinite(audio.duration) && (posMs / 1000) < audio.duration) audio.currentTime = posMs / 1000;
      };
      if (!userPaused && st.isPlaying) audio.play().catch(function(){});
      syncHeroBtn();
    }

    function syncHeroBtn() {
      var icon = document.getElementById('heroIcon'), txt = document.getElementById('heroText');
      var wave = document.getElementById('waveAnim'), stTxt = document.getElementById('statusText');
      if (!isAudioActive) {
        icon.innerHTML = playSvg; txt.innerText = 'Start Listening'; wave.style.display = 'none';
      } else if (audio.paused) {
        icon.innerHTML = playSvg; txt.innerText = 'Resume Audio'; wave.style.display = 'none';
        stTxt.innerText = userPaused ? 'Paused Locally' : 'Paused by Host';
      } else {
        icon.innerHTML = pauseSvg; txt.innerText = 'Listening Live'; wave.style.display = 'inline-flex';
        stTxt.innerText = 'Synced Live';
      }
    }

    function onVol(v) {
      audio.volume = parseFloat(v); audio.muted = audio.volume === 0;
      var pct = Math.round(audio.volume * 100);
      volInput.style.background = 'linear-gradient(to right, var(--track-fill) ' + pct + '%, var(--track-bg) ' + pct + '%)';
    }
    function toggleMute() { audio.muted = !audio.muted; onVol(audio.muted ? 0 : audio.volume); }
    onVol(1);

    seekInput.oninput = function() {
      if (!canControl()) return;
      seeking = true;
      var pct = parseFloat(this.value);
      fillBar.style.width = pct + '%'; thumbDot.style.left = pct + '%';
    };
    seekInput.onchange = function() {
      seeking = false;
      if (!canControl()) return;
      var durMs = (lastState && lastState.durationMs > 0) ? lastState.durationMs : ((audio.duration || 0) * 1000);
      if (durMs > 0) {
        var targetMs = (parseFloat(this.value) / 100) * durMs;
        if (isAudioActive && isFinite(audio.duration)) audio.currentTime = targetMs / 1000;
        sendAction('seek', targetMs);
      }
    };

    function openQueue() { closeDrawers(); isQueueOpen = true; document.getElementById('drawerOverlay').classList.add('show'); document.getElementById('queueDrawer').classList.add('show'); renderQueue(); }
    function openDevices() { closeDrawers(); isDevicesOpen = true; document.getElementById('drawerOverlay').classList.add('show'); document.getElementById('devicesDrawer').classList.add('show'); renderDevices(); }
    function closeDrawers() {
      isQueueOpen = false; isDevicesOpen = false;
      document.getElementById('drawerOverlay').classList.remove('show');
      document.getElementById('queueDrawer').classList.remove('show');
      document.getElementById('devicesDrawer').classList.remove('show');
    }

    function renderQueue() {
      var list = document.getElementById('queueList'), banner = document.getElementById('queuePermBanner');
      var searchWrap = document.getElementById('searchWrap'), cntText = document.getElementById('queueCountText');
      banner.className = 'perm-banner' + (canControl() ? '' : ' locked');
      searchWrap.className = 'search-box-wrap' + (canEditQueue() ? ' show' : '');
      cntText.innerText = queueCache.length + (queueCache.length === 1 ? ' upcoming track' : ' upcoming tracks');
      document.getElementById('queueBtnBadge').innerText = 'Queue (' + queueCache.length + ')';

      if (!queueCache.length) {
        list.innerHTML = '<div style="text-align:center;padding:34px 12px;color:var(--text-muted);font-weight:600;">Up Next queue is empty</div>';
        return;
      }
      var ctrl = canControl(), editQ = canEditQueue(), html = '';
      for (var i = 0; i < queueCache.length; i++) {
        var q = queueCache[i];
        var img = q.thumbnailUrl ? '<img src="' + esc(q.thumbnailUrl) + '" class="q-thumb" />' : '<div class="q-thumb" style="display:flex;align-items:center;justify-content:center;">♫</div>';
        var acts = '';
        if (editQ) {
          if (i > 0) acts += '<button class="mini-btn" onclick="moveQ(' + i + ',' + (i - 1) + ')">↑</button>';
          acts += '<button class="mini-btn danger" onclick="removeQ(' + i + ',\'' + esc(q.id) + '\')">✕</button>';
        }
        html += '<div class="q-item">' + img +
          '<div class="q-info" onclick="playQ(' + i + ',\'' + esc(q.id) + '\')">' +
          '<div class="q-title">' + esc(q.title) + '</div>' +
          '<div class="q-artist">' + esc(q.artist || 'iShare') + (ctrl ? ' • Tap to play' : '') + '</div></div>' +
          '<div class="q-actions">' + acts + '</div></div>';
      }
      list.innerHTML = html;
    }

    function playQ(idx, id) { if (!canControl()) return toast('Playback locked by host'); sendAction('play_index', null, { index: idx, songId: id }); }
    function removeQ(idx, id) { if (!canEditQueue()) return toast('Queue locked'); sendAction('remove_track', null, { index: idx, songId: id }); }
    function moveQ(from, to) { if (!canEditQueue()) return; sendAction('move_track', null, { from: from, to: to }); }

    function searchSongs() {
      var q = document.getElementById('searchQuery').value.trim();
      if (!q) return;
      var box = document.getElementById('searchResults');
      box.innerHTML = '<div style="font-size:0.8rem;color:var(--text-muted);padding:6px;">Searching...</div>';
      fetch('/together/search?q=' + encodeURIComponent(q)).then(function(r){ return r.json(); }).then(function(d){
        var items = (d && d.items) || [];
        if (!items.length) { box.innerHTML = '<div style="font-size:0.8rem;color:var(--text-muted);padding:6px;">No songs found</div>'; return; }
        box.innerHTML = items.slice(0, 6).map(function(it, idx){
          window.__sr = items;
          return '<div class="q-item"><img src="' + esc(it.thumbnailUrl) + '" class="q-thumb" />' +
            '<div class="q-info"><div class="q-title">' + esc(it.title) + '</div><div class="q-artist">' + esc(it.artist) + '</div></div>' +
            '<div class="q-actions"><button class="mini-btn" onclick="addSearchTrack(' + idx + ',true)">Next</button>' +
            '<button class="mini-btn" onclick="addSearchTrack(' + idx + ',false)">+ Queue</button></div></div>';
        }).join('');
      }).catch(function(){ box.innerHTML = ''; toast('Search failed'); });
    }
    function addSearchTrack(idx, playNext) {
      var it = (window.__sr || [])[idx]; if (!it) return;
      sendAction(playNext ? 'play_next' : 'add_track', null, { songId: it.id, id: it.id, title: it.title, artists: [it.artist], thumbnailUrl: it.thumbnailUrl, durationMs: it.durationMs });
      toast(playNext ? 'Playing next: ' + it.title : 'Added to queue: ' + it.title);
    }

    function renderDevices() {
      var parts = (lastData && lastData.participantList) || [];
      document.getElementById('devicesCountText').innerText = parts.length + ' active device' + (parts.length === 1 ? '' : 's');
      document.getElementById('devicesList').innerHTML = parts.map(function(p){
        var role = p.isHost ? '<span class="mini-btn" style="color:var(--accent);">HOST</span>' : '<span class="mini-btn">SYNCED</span>';
        return '<div class="q-item"><div class="q-thumb" style="display:flex;align-items:center;justify-content:center;font-weight:700;">' + esc((p.name || 'U')[0]) + '</div>' +
          '<div class="q-info"><div class="q-title">' + esc(p.name) + (p.id === pid ? ' (You)' : '') + '</div>' +
          '<div class="q-artist">' + (p.isHost ? 'Session Host' : 'Connected Listener') + '</div></div>' + role + '</div>';
      }).join('');
    }

    function copyShareUrl() {
      if (navigator.clipboard) navigator.clipboard.writeText(window.location.href).then(function(){ toast('LAN Link copied!'); });
      else toast(window.location.href);
    }

    function updateUi(data) {
      if (!data) return;
      lastData = data; lastState = data.state;
      if (data.serverNow) serverOffset = data.serverNow - Date.now();
      if (data.queue) queueCache = data.queue;
      var ctrl = canControl();
      scrubberWrap.className = 'scrubber-wrap' + (ctrl ? '' : ' locked');
      seekInput.disabled = !ctrl;
      document.getElementById('btnPrev').disabled = !ctrl;
      document.getElementById('btnNext').disabled = !ctrl;
      document.getElementById('permChip').innerText = ctrl ? 'Shared Controls' : 'Host Only';
      document.getElementById('listenerBadge').innerText = (data.participants || 1) + ((data.participants || 1) === 1 ? ' device' : ' devices');
      renderQueue(); if (isDevicesOpen) renderDevices();

      var st = data.state; if (!st) return;
      document.getElementById('trackTitle').innerText = st.title || 'iShare Session';
      var artists = (st.artists || []).filter(function(a){ return a && !a.startsWith('__AB_'); });
      document.getElementById('trackArtist').innerText = artists.join(', ') || (data.hostName || '$safeHost');
      if (st.thumbnailUrl) {
        var img = document.getElementById('artImg'), bg = document.getElementById('bgArt');
        if (img.src !== st.thumbnailUrl) {
          img.src = st.thumbnailUrl; bg.src = st.thumbnailUrl;
          img.onload = function(){ img.style.display = 'block'; bg.style.opacity = '1'; document.getElementById('artEmpty').style.display = 'none'; };
        }
      }
      if (isAudioActive && st.songId && st.songId !== curSongId) loadSong(st.songId, st);
      if (isAudioActive && (Date.now() - lastActionAt > 2500)) {
        if (st.isPlaying && audio.paused && !userPaused) audio.play().catch(function(){});
        else if (!st.isPlaying && !audio.paused) audio.pause();
        var hostPos = (st.positionMs || 0) + (st.isPlaying ? Math.max(0, (Date.now() + serverOffset) - (st.updatedAt || Date.now())) : 0);
        if (st.isPlaying && isFinite(audio.duration) && audio.duration > 0) {
          var diffSec = audio.currentTime - (hostPos / 1000);
          if (Math.abs(diffSec) > 3.5) { audio.currentTime = hostPos / 1000; audio.playbackRate = 1.0; }
          else if (Math.abs(diffSec) > 0.9) { audio.playbackRate = diffSec < 0 ? 1.04 : 0.96; }
          else audio.playbackRate = 1.0;
        }
      }
      syncHeroBtn();
    }

    setInterval(function(){
      if (!lastState || seeking) return;
      var durMs = (lastState.durationMs > 0) ? lastState.durationMs : ((audio.duration || 0) * 1000);
      var curMs = (isAudioActive && audio && !audio.paused) ? audio.currentTime * 1000 : ((lastState.positionMs || 0) + (lastState.isPlaying ? Math.max(0, (Date.now() + serverOffset) - (lastState.updatedAt || Date.now())) : 0));
      document.getElementById('curTime').innerText = fmtTime(curMs / 1000);
      document.getElementById('durTime').innerText = durMs > 0 ? fmtTime(durMs / 1000) : '--:--';
      if (durMs > 0) {
        var pct = Math.max(0, Math.min(100, (curMs / durMs) * 100));
        seekInput.value = pct; fillBar.style.width = pct + '%'; thumbDot.style.left = pct + '%';
      }
    }, 100);

    function pollState() {
      fetch('/together/state?participantId=' + encodeURIComponent(pid)).then(function(r){ return r.json(); }).then(updateUi).catch(function(){});
    }
    setInterval(pollState, 350);
    pollState();
  </script>
</body>
</html>"""
}

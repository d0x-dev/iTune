package com.darkxvenom.itune.audio

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.media.MediaCodecList
import android.media.audiofx.AudioEffect
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.media3.common.C
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import com.darkxvenom.itune.R
import com.darkxvenom.itune.data.library.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Immutable
data class EqPresetSpec(
    val name: String,
    val levelsMb: List<Int>,
)

val AirBeatsEqPresets: List<EqPresetSpec> = listOf(
    EqPresetSpec("Flat", listOf(0, 0, 0, 0, 0)),
    EqPresetSpec("Bass+", listOf(850, 650, 150, -100, -200)),
    EqPresetSpec("Treble+", listOf(-200, -100, 150, 650, 850)),
    EqPresetSpec("8D Space", listOf(450, -250, 350, -150, 700)),
    EqPresetSpec("Vocal", listOf(-300, 100, 850, 350, -150)),
    EqPresetSpec("Rock", listOf(650, 250, -250, 350, 700)),
    EqPresetSpec("Pop", listOf(-100, 350, 650, 300, -100)),
    EqPresetSpec("Dance", listOf(750, 500, 0, 350, 600)),
    EqPresetSpec("Electronic", listOf(700, 250, -150, 500, 850)),
    EqPresetSpec("Jazz", listOf(350, 150, 250, 450, 300)),
    EqPresetSpec("Classical", listOf(300, 200, 150, 350, 550)),
    EqPresetSpec("Night", listOf(-350, -150, 150, 250, 100)),
)

enum class NormalizationProfile(val label: String, val targetLufs: Float, val baseGainMb: Int) {
    DYNAMIC("Dynamic", -16f, 0),
    STREAMING("Balanced", -14f, 350),
    LOUD("Loud", -11f, 750),
}

@Immutable
data class PlayerPillBadge(
    @param:DrawableRes val iconRes: Int,
    val text: String,
    val isBrandedITune: Boolean,
)

@Immutable
data class AudioEngineUiState(
    val dolbyAtmosEnabled: Boolean = false,
    val dolbyHardwareSupported: Boolean = false,
    val dolbyWidthWide: Boolean = true,
    val eightDAudioEnabled: Boolean = false,
    val eightDAudioLevel: Int = 8,
    val spatialAudioEnabled: Boolean = false,
    val spatialWidth: Float = 2.5f,
    val spatialCrossfeed: Boolean = true,
    val eqEnabled: Boolean = false,
    val eqHardwareAttached: Boolean = false,
    val eqPreset: String = "Flat",
    val eqBandsMb: List<Int> = listOf(0, 0, 0, 0, 0),
    val eqFrequenciesHz: List<Int> = listOf(60, 230, 910, 3600, 14000),
    val eqMinMb: Int = -1500,
    val eqMaxMb: Int = 1500,
    val djEnabled: Boolean = false,
    val djBoostEnabled: Boolean = false,
    val djBoostPercent: Int = 100,
    val echoEnabled: Boolean = false,
    val echoDelayMs: Int = 280,
    val echoFeedback: Float = 0.40f,
    val echoWetMix: Float = 0.45f,
    val echoPingPong: Boolean = true,
    val filterSweep: Float = 0.0f,
    val flangerEnabled: Boolean = false,
    val flangerRate: Float = 0.5f,
    val flangerDepth: Float = 0.5f,
    val saturation: Float = 0.0f,
    val tempoSpeed: Float = 1.0f,
    val pitch: Float = 1.0f,
    val turntableLinked: Boolean = false,
    val normalizationEnabled: Boolean = false,
    val normalizationProfile: NormalizationProfile = NormalizationProfile.STREAMING,
    val normalizationOffsetMb: Int = 0,
    val activeAppliedGainMb: Int = 0,
    val bypassAllEffects: Boolean = false,
    val bitPerfectEnabled: Boolean = false,
    val bitPerfectActive: Boolean = false,
    val pillBadge: PlayerPillBadge = PlayerPillBadge(R.drawable.graphic_eq, "iMusic • 128k", true),
)

/**
 * Full AirBeats-sourced DSP audio engine manager for iTune:
 * Drives Dolby Atmos spatializer, 8D 360° orbit, Spatial width/crossfeed,
 * Android hardware 5-band Equalizer (dots + sliders), Studio DJ Console (Echo/Mixer/Turntable/200% Boost),
 * LoudnessEnhancer Audio Normalization (Sound Check), Pure Audio Bypass, and USB Bit-Perfect lock.
 */
@UnstableApi
object AudioEngineManager {
    private const val PREFS = "itune_audio_engine_prefs"
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val djProcessor = DjAudioProcessor()
    val spatialProcessor = SpatialAudioProcessor()
    val eightDProcessor = EightDAudioProcessor()
    val visualizerManager = AudioVisualizerManager(scope)

    private var appContext: Context? = null
    private var prefs: SharedPreferences? = null
    private var hardwareEq: Equalizer? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null
    private var attachedSessionId: Int = C.AUDIO_SESSION_ID_UNSET
    private var sessionIdProvider: (() -> Int)? = null
    private var onApplyPlaybackParams: ((PlaybackParameters) -> Unit)? = null

    private val _state = MutableStateFlow(AudioEngineUiState())
    val state: StateFlow<AudioEngineUiState> = _state.asStateFlow()

    fun init(
        context: Context,
        getSessionId: () -> Int,
        applyPlaybackParams: (PlaybackParameters) -> Unit,
    ) {
        val ctx = context.applicationContext
        appContext = ctx
        sessionIdProvider = getSessionId
        onApplyPlaybackParams = applyPlaybackParams

        if (prefs == null) {
            prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            loadPersistedState()
            scope.launch {
                AudioOutputManager.state.collectLatest { out ->
                    val cur = _state.value
                    if (cur.bitPerfectEnabled != out.bitPerfectEnabled || cur.bitPerfectActive != out.bitPerfectActive) {
                        _state.value = cur.copy(
                            bitPerfectEnabled = out.bitPerfectEnabled,
                            bitPerfectActive = out.bitPerfectActive,
                        )
                        reconcileAllEffects()
                    }
                }
            }
        }
        reconcileAllEffects()
    }

    fun attachAudioSession(sessionId: Int, isPlaying: Boolean) {
        visualizerManager.isPlaying = isPlaying
        if (sessionId > 0) {
            visualizerManager.start(sessionId)
        }
        if (sessionId > 0 && sessionId != attachedSessionId) {
            releaseEffects()
            attachedSessionId = sessionId
            setupHardwareEqualizer(sessionId)
            setupLoudnessEnhancer(sessionId)
        }
    }

    fun updatePlayingState(isPlaying: Boolean) {
        visualizerManager.isPlaying = isPlaying
        val sid = sessionIdProvider?.invoke() ?: C.AUDIO_SESSION_ID_UNSET
        if (isPlaying && sid > 0) {
            visualizerManager.start(sid)
        }
    }

    // ── Master Effect Toggles & Individual Parameter Setters ──

    fun setDolbyAtmosEnabled(enabled: Boolean) {
        updateAndSave { cur ->
            prefs?.edit()?.putBoolean("dolby_enabled", enabled)?.apply()
            cur.copy(dolbyAtmosEnabled = enabled)
        }
        appContext?.let { SettingsStore.setBoolean(it, SettingsStore.KEY_DOLBY_ATMOS, enabled) }
    }

    fun setDolbyWidthWide(wide: Boolean) {
        updateAndSave { cur ->
            prefs?.edit()?.putBoolean("dolby_wide", wide)?.apply()
            cur.copy(dolbyWidthWide = wide)
        }
    }

    fun setEightDAudioEnabled(enabled: Boolean) {
        updateAndSave { cur ->
            prefs?.edit()?.putBoolean("eight_d_enabled", enabled)?.apply()
            cur.copy(eightDAudioEnabled = enabled)
        }
    }

    fun setEightDAudioLevel(level: Int) {
        val clamped = level.coerceIn(1, 16)
        updateAndSave { cur ->
            prefs?.edit()?.putInt("eight_d_level", clamped)?.apply()
            cur.copy(eightDAudioLevel = clamped)
        }
    }

    fun setSpatialAudioEnabled(enabled: Boolean) {
        updateAndSave { cur ->
            prefs?.edit()?.putBoolean("spatial_enabled", enabled)?.apply()
            cur.copy(spatialAudioEnabled = enabled)
        }
    }

    fun setSpatialWidth(width: Float) {
        val clamped = width.coerceIn(1.0f, 3.0f)
        updateAndSave { cur ->
            prefs?.edit()?.putFloat("spatial_width", clamped)?.apply()
            cur.copy(spatialWidth = clamped)
        }
    }

    fun setSpatialCrossfeed(enabled: Boolean) {
        updateAndSave { cur ->
            prefs?.edit()?.putBoolean("spatial_crossfeed", enabled)?.apply()
            cur.copy(spatialCrossfeed = enabled)
        }
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        if (_state.value.bypassAllEffects && enabled) return
        updateAndSave { cur ->
            prefs?.edit()?.putBoolean("eq_enabled", enabled)?.apply()
            cur.copy(eqEnabled = enabled)
        }
    }

    fun setEqualizerBandLevel(bandIndex: Int, levelMb: Int) {
        val cur = _state.value
        if (bandIndex !in cur.eqBandsMb.indices) return
        val clamped = levelMb.coerceIn(cur.eqMinMb, cur.eqMaxMb)
        val updated = cur.eqBandsMb.toMutableList().apply { this[bandIndex] = clamped }
        prefs?.edit()?.apply {
            putInt("eq_band_$bandIndex", clamped)
            putString("eq_preset", "Custom")
            putBoolean("eq_enabled", true)
            apply()
        }
        _state.value = cur.copy(
            eqEnabled = !cur.bypassAllEffects,
            eqPreset = "Custom",
            eqBandsMb = updated,
        )
        reconcileAllEffects()
    }

    fun applyEqPreset(preset: EqPresetSpec) {
        val cur = _state.value
        val mapped = cur.eqBandsMb.indices.map { idx ->
            preset.levelsMb.getOrElse(idx) { 0 }.coerceIn(cur.eqMinMb, cur.eqMaxMb)
        }
        prefs?.edit()?.apply {
            putBoolean("eq_enabled", preset.name != "Flat")
            putString("eq_preset", preset.name)
            mapped.forEachIndexed { idx, v -> putInt("eq_band_$idx", v) }
            apply()
        }
        _state.value = cur.copy(
            eqEnabled = !cur.bypassAllEffects && preset.name != "Flat",
            eqPreset = preset.name,
            eqBandsMb = mapped,
        )
        reconcileAllEffects()
    }

    fun resetEqualizer() {
        applyEqPreset(AirBeatsEqPresets.first())
    }

    fun setDjEnabled(enabled: Boolean) {
        updateAndSave { cur ->
            prefs?.edit()?.putBoolean("dj_enabled", enabled)?.apply()
            if (!enabled) {
                cur.copy(
                    djEnabled = false,
                    djBoostEnabled = false,
                    djBoostPercent = 100,
                    echoEnabled = false,
                    echoWetMix = 0f,
                    filterSweep = 0f,
                    flangerEnabled = false,
                    saturation = 0f,
                    tempoSpeed = 1.0f,
                    pitch = 1.0f,
                    turntableLinked = false,
                )
            } else {
                cur.copy(djEnabled = true)
            }
        }
    }

    fun setDjBoostPercent(percent: Int) {
        val clamped = percent.coerceIn(100, 200)
        updateAndSave { cur ->
            prefs?.edit()?.apply {
                putInt("dj_boost_percent", clamped)
                putBoolean("dj_boost_enabled", clamped > 100)
                apply()
            }
            cur.copy(
                djEnabled = if (clamped > 100) true else cur.djEnabled,
                djBoostEnabled = clamped > 100,
                djBoostPercent = clamped,
            )
        }
    }

    fun setEchoEnabled(enabled: Boolean) = updateDjField { it.copy(djEnabled = true, echoEnabled = enabled) }
    fun setEchoDelayMs(ms: Int) = updateDjField { it.copy(djEnabled = true, echoDelayMs = ms.coerceIn(40, 800)) }
    fun setEchoFeedback(fb: Float) = updateDjField { it.copy(djEnabled = true, echoFeedback = fb.coerceIn(0.05f, 0.85f)) }
    fun setEchoWetMix(mix: Float) = updateDjField { it.copy(djEnabled = true, echoWetMix = mix.coerceIn(0f, 1f)) }
    fun setEchoPingPong(pp: Boolean) = updateDjField { it.copy(djEnabled = true, echoPingPong = pp) }
    fun setFilterSweep(sweep: Float) = updateDjField { it.copy(djEnabled = true, filterSweep = sweep.coerceIn(-1f, 1f)) }
    fun setFlangerEnabled(on: Boolean) = updateDjField { it.copy(djEnabled = true, flangerEnabled = on) }
    fun setFlangerRate(rate: Float) = updateDjField { it.copy(djEnabled = true, flangerRate = rate.coerceIn(0.1f, 4.0f)) }
    fun setFlangerDepth(depth: Float) = updateDjField { it.copy(djEnabled = true, flangerDepth = depth.coerceIn(0f, 1f)) }
    fun setSaturation(sat: Float) = updateDjField { it.copy(djEnabled = true, saturation = sat.coerceIn(0f, 1f)) }

    fun setDjTempoAndPitch(speed: Float, pitch: Float) {
        val spd = speed.coerceIn(0.5f, 2.0f)
        val pch = pitch.coerceIn(0.5f, 2.0f)
        updateDjField {
            it.copy(
                djEnabled = true,
                tempoSpeed = spd,
                pitch = pch,
            )
        }
    }

    fun setDjTurntableLinked(linked: Boolean) {
        updateDjField { cur ->
            val nextPitch = if (linked) cur.tempoSpeed else cur.pitch
            cur.copy(turntableLinked = linked, pitch = nextPitch)
        }
    }

    fun applyDjPreset(preset: DjPreset) {
        val next = when (preset) {
            DjPreset.DEFAULT -> _state.value.copy(
                djEnabled = false,
                djBoostEnabled = false,
                djBoostPercent = 100,
                echoEnabled = false,
                echoDelayMs = 280,
                echoFeedback = 0.40f,
                echoWetMix = 0f,
                echoPingPong = false,
                filterSweep = 0f,
                flangerEnabled = false,
                flangerRate = 0.5f,
                flangerDepth = 0f,
                saturation = 0f,
                tempoSpeed = 1.0f,
                pitch = 1.0f,
                turntableLinked = false,
            )
            DjPreset.CLUB_BOOTH -> _state.value.copy(
                djEnabled = true,
                djBoostEnabled = true,
                djBoostPercent = 130,
                echoEnabled = true,
                echoDelayMs = 180,
                echoFeedback = 0.30f,
                echoWetMix = 0.35f,
                echoPingPong = true,
                filterSweep = -0.15f,
                flangerEnabled = false,
                saturation = 0.20f,
                tempoSpeed = 1.0f,
                pitch = 1.0f,
            )
            DjPreset.SLOWED_REVERB -> _state.value.copy(
                djEnabled = true,
                echoEnabled = true,
                echoDelayMs = 380,
                echoFeedback = 0.55f,
                echoWetMix = 0.50f,
                echoPingPong = true,
                filterSweep = -0.25f,
                flangerEnabled = false,
                saturation = 0.15f,
                turntableLinked = true,
                tempoSpeed = 0.85f,
                pitch = 0.85f,
            )
            DjPreset.NIGHTCORE -> _state.value.copy(
                djEnabled = true,
                echoEnabled = false,
                filterSweep = 0.20f,
                flangerEnabled = false,
                saturation = 0.10f,
                turntableLinked = true,
                tempoSpeed = 1.25f,
                pitch = 1.25f,
            )
            DjPreset.BASS_BOMB -> _state.value.copy(
                djEnabled = true,
                djBoostEnabled = true,
                djBoostPercent = 160,
                echoEnabled = false,
                filterSweep = -0.35f,
                flangerEnabled = false,
                saturation = 0.40f,
                tempoSpeed = 1.0f,
                pitch = 1.0f,
            )
            DjPreset.LOFI_VINYL -> _state.value.copy(
                djEnabled = true,
                echoEnabled = true,
                echoDelayMs = 120,
                echoFeedback = 0.25f,
                echoWetMix = 0.30f,
                echoPingPong = false,
                filterSweep = -0.50f,
                flangerEnabled = true,
                flangerRate = 0.2f,
                flangerDepth = 0.25f,
                saturation = 0.35f,
                tempoSpeed = 0.95f,
                pitch = 0.95f,
            )
            DjPreset.SPACE_ECHO -> _state.value.copy(
                djEnabled = true,
                echoEnabled = true,
                echoDelayMs = 450,
                echoFeedback = 0.65f,
                echoWetMix = 0.60f,
                echoPingPong = true,
                filterSweep = 0f,
                flangerEnabled = true,
                flangerRate = 0.4f,
                flangerDepth = 0.35f,
                saturation = 0.10f,
                tempoSpeed = 1.0f,
                pitch = 1.0f,
            )
        }
        _state.value = next
        reconcileAllEffects()
    }

    fun setNormalizationEnabled(enabled: Boolean) {
        updateAndSave { cur ->
            prefs?.edit()?.putBoolean("norm_enabled", enabled)?.apply()
            cur.copy(normalizationEnabled = enabled)
        }
        appContext?.let { SettingsStore.setBoolean(it, SettingsStore.KEY_SOUND_CHECK, enabled) }
    }

    fun setNormalizationProfile(profile: NormalizationProfile) {
        updateAndSave { cur ->
            prefs?.edit()?.putString("norm_profile", profile.name)?.apply()
            cur.copy(normalizationProfile = profile)
        }
    }

    fun setNormalizationOffsetMb(offsetMb: Int) {
        val clamped = offsetMb.coerceIn(-1200, 1200)
        updateAndSave { cur ->
            prefs?.edit()?.putInt("norm_offset_mb", clamped)?.apply()
            cur.copy(normalizationOffsetMb = clamped)
        }
    }

    fun setBypassAllEffects(bypass: Boolean) {
        updateAndSave { cur ->
            prefs?.edit()?.putBoolean("bypass_all", bypass)?.apply()
            cur.copy(bypassAllEffects = bypass)
        }
    }

    fun setBitPerfectEnabled(enabled: Boolean) {
        AudioOutputManager.setBitPerfectEnabled(enabled)
    }

    fun openSystemDolbyOrAudioEffects(context: Context): Boolean {
        val sid = sessionIdProvider?.invoke() ?: C.AUDIO_SESSION_ID_UNSET
        val candidates = listOf(
            Intent(AudioEffect.ACTION_DISPLAY_AUDIO_EFFECT_CONTROL_PANEL).apply {
                if (sid > 0) putExtra(AudioEffect.EXTRA_AUDIO_SESSION, sid)
                putExtra(AudioEffect.EXTRA_PACKAGE_NAME, context.packageName)
                putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
            },
            Intent("com.samsung.android.settings.sound.DolbyAtmosActivity"),
            Intent("miui.intent.action.DOLBY_ATMOS"),
            Intent("com.oplus.audio.effect.DOLBY"),
            Intent("com.dolby.intent.action.DAXAPPUI_MAIN"),
            Intent(Settings.ACTION_SOUND_SETTINGS),
        )
        for (intent in candidates) {
            runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return true
                }
            }
        }
        return false
    }

    fun release() {
        visualizerManager.stop()
        releaseEffects()
    }

    // ── Internal DSP Synchronization ──

    private inline fun updateDjField(transform: (AudioEngineUiState) -> AudioEngineUiState) {
        _state.value = transform(_state.value)
        reconcileAllEffects()
    }

    private inline fun updateAndSave(transform: (AudioEngineUiState) -> AudioEngineUiState) {
        _state.value = transform(_state.value)
        reconcileAllEffects()
    }

    private fun reconcileAllEffects() {
        val s = _state.value
        val locked = s.bypassAllEffects || (s.bitPerfectEnabled && s.bitPerfectActive)

        // 1. Spatial & Dolby Atmos PCM processor
        spatialProcessor.bypass = locked
        spatialProcessor.enabled = !locked && (s.dolbyAtmosEnabled || s.spatialAudioEnabled)
        spatialProcessor.widthGain = when {
            s.dolbyAtmosEnabled && s.dolbyWidthWide -> 2.75f
            s.dolbyAtmosEnabled -> 2.25f
            else -> s.spatialWidth
        }
        spatialProcessor.crossfeedEnabled = if (s.dolbyAtmosEnabled) true else s.spatialCrossfeed

        // 2. 8D 360° Orbital PCM processor
        eightDProcessor.bypass = locked
        eightDProcessor.enabled = !locked && s.eightDAudioEnabled
        eightDProcessor.level = s.eightDAudioLevel

        // 3. DJ Studio PCM processor + ExoPlayer speed/pitch
        djProcessor.bypass = locked
        djProcessor.enabled = !locked && s.djEnabled
        djProcessor.echoEnabled = s.echoEnabled
        djProcessor.echoDelayMs = s.echoDelayMs
        djProcessor.echoFeedback = s.echoFeedback
        djProcessor.echoWetMix = s.echoWetMix
        djProcessor.echoPingPong = s.echoPingPong
        djProcessor.filterSweep = s.filterSweep
        djProcessor.flangerEnabled = s.flangerEnabled
        djProcessor.flangerRate = s.flangerRate
        djProcessor.flangerDepth = s.flangerDepth
        djProcessor.saturation = s.saturation

        val effSpeed = if (locked || !s.djEnabled) 1.0f else s.tempoSpeed
        val effPitch = if (locked || !s.djEnabled) 1.0f else s.pitch
        onApplyPlaybackParams?.invoke(PlaybackParameters(effSpeed, effPitch))

        // 4. Hardware Equalizer
        val eq = hardwareEq
        val sid = sessionIdProvider?.invoke() ?: C.AUDIO_SESSION_ID_UNSET
        if (eq == null && sid > 0 && !locked && s.eqEnabled) {
            setupHardwareEqualizer(sid)
        } else if (eq != null) {
            runCatching {
                eq.enabled = !locked && s.eqEnabled
                s.eqBandsMb.forEachIndexed { i, lvl ->
                    if (i < eq.numberOfBands) {
                        eq.setBandLevel(i.toShort(), lvl.coerceIn(s.eqMinMb, s.eqMaxMb).toShort())
                    }
                }
            }
        }

        // 5. LoudnessEnhancer (Audio Normalization + Master Boost)
        val normGain = if (!locked && s.normalizationEnabled) {
            (s.normalizationProfile.baseGainMb + s.normalizationOffsetMb).coerceIn(-1200, 1500)
        } else {
            0
        }
        val boostFrac = ((s.djBoostPercent - 100) / 100f).coerceIn(0f, 1f)
        val boostGain = if (!locked && s.djEnabled && s.djBoostEnabled) (boostFrac * 1800).toInt() else 0
        val totalGainMb = (normGain + boostGain).coerceIn(-1200, 3000)

        if (loudnessEnhancer == null && sid > 0 && (totalGainMb != 0 || s.normalizationEnabled || s.djBoostEnabled)) {
            setupLoudnessEnhancer(sid)
        }
        runCatching {
            val le = loudnessEnhancer
            if (le != null) {
                val shouldEnable = !locked && (s.normalizationEnabled || (s.djEnabled && s.djBoostEnabled))
                le.setTargetGain(totalGainMb)
                le.enabled = shouldEnable && totalGainMb != 0
            }
        }

        // 6. Player Quality Pill Badge
        val badge = computePillBadge(s, locked)
        if (_state.value.activeAppliedGainMb != totalGainMb || _state.value.pillBadge != badge) {
            _state.value = _state.value.copy(
                activeAppliedGainMb = totalGainMb,
                pillBadge = badge,
            )
        }
    }

    private fun computePillBadge(s: AudioEngineUiState, locked: Boolean): PlayerPillBadge =
        resolveAudioEnginePillBadge(s, locked)

    private fun setupHardwareEqualizer(sessionId: Int) {
        runCatching {
            val eq = Equalizer(0, sessionId)
            val range = eq.bandLevelRange
            val count = eq.numberOfBands.toInt().coerceAtLeast(1)
            val cur = _state.value
            val freqs = (0 until count).map { idx -> (eq.getCenterFreq(idx.toShort()) / 1000).coerceAtLeast(20) }
            val levels = (0 until count).map { idx ->
                cur.eqBandsMb.getOrElse(idx) { 0 }.coerceIn(range[0].toInt(), range[1].toInt())
            }
            levels.forEachIndexed { idx, v -> eq.setBandLevel(idx.toShort(), v.toShort()) }
            eq.enabled = !cur.bypassAllEffects && !(cur.bitPerfectEnabled && cur.bitPerfectActive) && cur.eqEnabled
            hardwareEq = eq
            _state.value = cur.copy(
                eqHardwareAttached = true,
                eqMinMb = range[0].toInt(),
                eqMaxMb = range[1].toInt(),
                eqFrequenciesHz = freqs,
                eqBandsMb = levels,
            )
        }.onFailure {
            hardwareEq = null
        }
    }

    private fun setupLoudnessEnhancer(sessionId: Int) {
        runCatching {
            loudnessEnhancer = LoudnessEnhancer(sessionId)
        }.onFailure {
            loudnessEnhancer = null
        }
    }

    private fun releaseEffects() {
        runCatching { hardwareEq?.enabled = false; hardwareEq?.release() }
        hardwareEq = null
        runCatching { loudnessEnhancer?.enabled = false; loudnessEnhancer?.release() }
        loudnessEnhancer = null
        attachedSessionId = C.AUDIO_SESSION_ID_UNSET
    }

    private fun loadPersistedState() {
        val p = prefs ?: return
        val hasAtmosDecoder = runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.any { info ->
                !info.isEncoder && info.supportedTypes.any { it.contains("eac3", ignoreCase = true) }
            }
        }.getOrDefault(false)

        val bands = (0 until 5).map { idx -> p.getInt("eq_band_$idx", 0) }
        val profile = runCatching {
            NormalizationProfile.valueOf(p.getString("norm_profile", NormalizationProfile.STREAMING.name) ?: "")
        }.getOrDefault(NormalizationProfile.STREAMING)

        _state.value = AudioEngineUiState(
            dolbyAtmosEnabled = p.getBoolean("dolby_enabled", false),
            dolbyHardwareSupported = hasAtmosDecoder,
            dolbyWidthWide = p.getBoolean("dolby_wide", true),
            eightDAudioEnabled = p.getBoolean("eight_d_enabled", false),
            eightDAudioLevel = p.getInt("eight_d_level", 8).coerceIn(1, 16),
            spatialAudioEnabled = p.getBoolean("spatial_enabled", false),
            spatialWidth = p.getFloat("spatial_width", 2.5f).coerceIn(1.0f, 3.0f),
            spatialCrossfeed = p.getBoolean("spatial_crossfeed", true),
            eqEnabled = p.getBoolean("eq_enabled", false),
            eqPreset = p.getString("eq_preset", "Flat") ?: "Flat",
            eqBandsMb = bands,
            djEnabled = p.getBoolean("dj_enabled", false),
            djBoostEnabled = p.getBoolean("dj_boost_enabled", false),
            djBoostPercent = p.getInt("dj_boost_percent", 100).coerceIn(100, 200),
            normalizationEnabled = p.getBoolean("norm_enabled", false),
            normalizationProfile = profile,
            normalizationOffsetMb = p.getInt("norm_offset_mb", 0).coerceIn(-1200, 1200),
            bypassAllEffects = p.getBoolean("bypass_all", false),
        )
    }
}

internal fun resolveAudioEnginePillBadge(
    s: AudioEngineUiState,
    locked: Boolean = s.bypassAllEffects || (s.bitPerfectEnabled && s.bitPerfectActive),
): PlayerPillBadge {
    return when {
        s.bitPerfectActive && s.bitPerfectEnabled -> PlayerPillBadge(
            iconRes = R.drawable.ic_output_bit_perfect,
            text = "Bit-Perfect",
            isBrandedITune = false,
        )
        s.bypassAllEffects -> PlayerPillBadge(
            iconRes = R.drawable.ic_fx_bypass,
            text = "Pure Direct",
            isBrandedITune = false,
        )
        !locked && s.dolbyAtmosEnabled -> PlayerPillBadge(
            iconRes = R.drawable.ic_dolby_atmos,
            text = "Dolby Atmos",
            isBrandedITune = false,
        )
        !locked && s.eightDAudioEnabled -> PlayerPillBadge(
            iconRes = R.drawable.ic_fx_8d,
            text = "${s.eightDAudioLevel}D Audio",
            isBrandedITune = false,
        )
        !locked && s.djEnabled -> PlayerPillBadge(
            iconRes = R.drawable.ic_dj_console,
            text = "DJ Studio",
            isBrandedITune = false,
        )
        !locked && s.spatialAudioEnabled -> PlayerPillBadge(
            iconRes = R.drawable.ic_fx_spatial,
            text = "Spatial Audio",
            isBrandedITune = false,
        )
        else -> PlayerPillBadge(
            iconRes = R.drawable.graphic_eq,
            text = "iMusic • 128k",
            isBrandedITune = true,
        )
    }
}

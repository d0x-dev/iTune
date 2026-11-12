package com.darkxvenom.itune.audio

import android.Manifest
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.compose.runtime.Immutable
import androidx.core.content.ContextCompat
import com.darkxvenom.itune.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioDeviceCategory {
    BLUETOOTH_BUDS,
    WIRED_OR_USB_HEADPHONE,
    USB_DAC,
    BUILTIN_SPEAKER,
    EXTERNAL_SPEAKER,
    HEARING_AID,
}

@Immutable
data class OutputDeviceEntry(
    val id: Int,
    val name: String,
    val subtitle: String,
    val category: AudioDeviceCategory,
    @param:DrawableRes val iconRes: Int,
    val sampleRateLabel: String?,
    val batteryPercent: Int?,
    val info: AudioDeviceInfo,
)

@Immutable
data class AudioOutputUiState(
    val devices: List<OutputDeviceEntry> = emptyList(),
    val preferredDeviceId: Int? = null,
    val activeDevice: OutputDeviceEntry? = null,
    @param:DrawableRes val capsuleIconRes: Int = R.drawable.ic_airbuds,
    val activeSummary: String = "System Default",
    val dualAudioEnabled: Boolean = false,
    val dualSecondaryDeviceId: Int? = null,
    val dualSecondaryVolume: Float = 1.0f,
    val bitPerfectEnabled: Boolean = false,
    val bitPerfectActive: Boolean = false,
    val usbDevicePresent: Boolean = false,
    val hasBluetoothPermission: Boolean = true,
)

/**
 * Live audio output device manager powered by AudioManager + AudioDeviceCallback.
 * Handles AirBeats-style hardware route switching, Bluetooth battery reading, USB bit-perfect
 * DAC configuration, simultaneous multi-output mirroring, and dynamic capsule icon selection.
 */
object AudioOutputManager {
    private const val PREFS_NAME = "itune_audio_output_prefs"
    private const val KEY_BIT_PERFECT = "bit_perfect_enabled"

    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var usbBitPerfect: UsbBitPerfectOutput? = null
    private var simultaneousProcessor: SimultaneousAudioProcessor? = null
    private var onRouteDeviceChanged: ((AudioDeviceInfo?) -> Unit)? = null
    private var callbackRegistered = false
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    private var preferredDevice: AudioDeviceInfo? = null
    private var dualSecondaryDevice: AudioDeviceInfo? = null

    private val _state = MutableStateFlow(AudioOutputUiState())
    val state: StateFlow<AudioOutputUiState> = _state.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            refreshDevices()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            val currentPref = preferredDevice
            if (currentPref != null && removedDevices?.any { it.id == currentPref.id } == true) {
                preferredDevice = null
                onRouteDeviceChanged?.invoke(null)
            }
            val currentSec = dualSecondaryDevice
            if (currentSec != null && removedDevices?.any { it.id == currentSec.id } == true) {
                dualSecondaryDevice = null
                simultaneousProcessor?.setTargetDevice(null)
            }
            refreshDevices()
        }
    }

    fun init(
        context: Context,
        processor: SimultaneousAudioProcessor?,
        onApplyPreferredDevice: (AudioDeviceInfo?) -> Unit,
    ) {
        val ctx = context.applicationContext
        appContext = ctx
        simultaneousProcessor = processor
        onRouteDeviceChanged = onApplyPreferredDevice

        val mgr = ctx.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        audioManager = mgr

        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val savedBitPerfect = prefs.getBoolean(KEY_BIT_PERFECT, false)

        if (usbBitPerfect == null) {
            usbBitPerfect = UsbBitPerfectOutput(mgr).apply {
                setEnabled(savedBitPerfect)
            }
        } else {
            usbBitPerfect?.setEnabled(savedBitPerfect)
        }

        if (mgr != null && !callbackRegistered) {
            callbackRegistered = true
            runCatching { mgr.registerAudioDeviceCallback(deviceCallback, mainHandler) }
        }

        refreshDevices()
    }

    fun refreshDevices() {
        val ctx = appContext ?: return
        val mgr = audioManager ?: return

        val hasBtPerm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }

        val btBatteries = if (hasBtPerm) queryBondedBatteryMap(ctx) else emptyMap()
        val rawDevices = mgr.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            .filter { isSupportedOutputDevice(it) }
            .distinctBy { "${it.type}_${it.productName}_${it.addressCompat()}" }
            .sortedBy { sortPriority(it) }

        val entries = rawDevices.map { dev -> toEntry(dev, btBatteries) }

        val prefId = preferredDevice?.id
        val chosenPref = entries.firstOrNull { it.id == prefId }
        if (prefId != null && chosenPref == null) {
            preferredDevice = null
            onRouteDeviceChanged?.invoke(null)
        }

        // Active physical route: user override or highest-priority connected device
        val activeEntry = chosenPref ?: entries.firstOrNull()

        // Sync USB DAC bit-perfect target
        val usbDeviceInfo = entries.firstOrNull {
            it.info.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.info.type == AudioDeviceInfo.TYPE_USB_HEADSET
        }?.info
        usbBitPerfect?.setDevice(usbDeviceInfo)

        // Capsule icon policy:
        // - Bluetooth audio connected/selected -> ic_airbuds
        // - USB / wired audio jack -> ic_headphone
        // - Speaker (explicit OR System Default routing to phone speaker) -> ic_output_speaker
        // - External/HDMI -> ic_output_external_speaker, Hearing aid -> ic_output_hearing_aid
        // - No device resolved yet -> ic_airbuds (ready state)
        val capsuleIcon = resolveCapsuleIcon(
            chosenPref = chosenPref,
            activeEntry = activeEntry,
        )

        val summary = buildString {
            append(activeEntry?.name ?: "Phone Speaker")
            activeEntry?.batteryPercent?.let { append(" • $it%") }
            activeEntry?.sampleRateLabel?.let { append(" • $it") }
        }

        val bitPerfectSaved = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BIT_PERFECT, false)

        _state.value = AudioOutputUiState(
            devices = entries,
            preferredDeviceId = chosenPref?.id,
            activeDevice = activeEntry,
            capsuleIconRes = capsuleIcon,
            activeSummary = summary,
            dualAudioEnabled = simultaneousProcessor?.enabled == true,
            dualSecondaryDeviceId = dualSecondaryDevice?.id,
            dualSecondaryVolume = simultaneousProcessor?.volume ?: 1.0f,
            bitPerfectEnabled = bitPerfectSaved,
            bitPerfectActive = usbBitPerfect?.isConfigured() == true,
            usbDevicePresent = usbDeviceInfo != null,
            hasBluetoothPermission = hasBtPerm,
        )
    }

    fun selectPreferredDevice(entry: OutputDeviceEntry?) {
        preferredDevice = entry?.info
        onRouteDeviceChanged?.invoke(entry?.info)
        refreshDevices()
    }

    fun setDualAudioEnabled(enabled: Boolean) {
        simultaneousProcessor?.setEnabled(enabled)
        refreshDevices()
    }

    fun setDualAudioSecondaryDevice(entry: OutputDeviceEntry?) {
        dualSecondaryDevice = entry?.info
        simultaneousProcessor?.setTargetDevice(entry?.info)
        refreshDevices()
    }

    fun setDualAudioSecondaryVolume(volume: Float) {
        simultaneousProcessor?.setVolume(volume)
        _state.value = _state.value.copy(dualSecondaryVolume = volume.coerceIn(0f, 1f))
    }

    fun setBitPerfectEnabled(enabled: Boolean) {
        appContext?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putBoolean(KEY_BIT_PERFECT, enabled)
            ?.apply()
        usbBitPerfect?.setEnabled(enabled)
        refreshDevices()
    }

    fun openSystemMediaOutputOrBluetooth(context: Context, preferBluetoothSettings: Boolean = false) {
        val candidates = buildList {
            if (!preferBluetoothSettings) {
                add(Intent("com.android.settings.panel.action.MEDIA_OUTPUT"))
                add(Intent("com.android.systemui.action.LAUNCH_MEDIA_OUTPUT_DIALOG").putExtra("package_name", context.packageName))
            }
            add(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
            add(Intent(Settings.ACTION_SOUND_SETTINGS))
        }
        for (intent in candidates) {
            runCatching {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(context.packageManager) != null) {
                    context.startActivity(intent)
                    return
                }
            }
        }
    }

    fun release() {
        val mgr = audioManager
        if (mgr != null && callbackRegistered) {
            runCatching { mgr.unregisterAudioDeviceCallback(deviceCallback) }
        }
        callbackRegistered = false
        usbBitPerfect?.clear()
        usbBitPerfect = null
    }

    internal fun resolveCapsuleIconForCategory(
        category: AudioDeviceCategory?,
        explicitSelection: Boolean,
    ): Int = resolveOutputCapsuleIcon(category, explicitSelection)

    internal fun resolveListIconForCategory(category: AudioDeviceCategory): Int =
        resolveOutputListIcon(category)

    private fun resolveCapsuleIcon(
        chosenPref: OutputDeviceEntry?,
        activeEntry: OutputDeviceEntry?,
    ): Int {
        val effectiveCategory = (chosenPref ?: activeEntry)?.category
        // For BUILTIN_SPEAKER: treat "System Default → Phone Speaker" the same as explicit
        // speaker selection — show the phone/speaker icon, not the buds silhouette.
        val treatAsExplicit = chosenPref != null ||
            effectiveCategory == AudioDeviceCategory.BUILTIN_SPEAKER
        return resolveOutputCapsuleIcon(
            category = effectiveCategory,
            explicitSelection = treatAsExplicit,
        )
    }

    private fun toEntry(dev: AudioDeviceInfo, btBatteries: Map<String, Int>): OutputDeviceEntry {
        val name = dev.outputName()
        val category = classify(dev)
        val iconRes = resolveListIconForCategory(category)
        val subtitle = when {
            dev.isBluetoothOutput() -> "Bluetooth wireless audio"
            dev.type == AudioDeviceInfo.TYPE_USB_DEVICE || dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB-C DAC • Direct Hardware"
            dev.type == AudioDeviceInfo.TYPE_USB_HEADSET -> "USB Headset • Digital Audio"
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_LINE_ANALOG -> "Wired 3.5mm Output"
            dev.type == AudioDeviceInfo.TYPE_HEARING_AID -> "Accessibility Hearing Device"
            dev.type == AudioDeviceInfo.TYPE_HDMI || dev.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
                dev.type == AudioDeviceInfo.TYPE_LINE_DIGITAL -> "External / HDMI Receiver"
            dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Internal phone speaker"
            else -> "Audio Output"
        }

        val maxRateHz = dev.sampleRates?.maxOrNull()?.takeIf { it > 0 }
        val sampleRateLabel = maxRateHz?.let { hz ->
            if (hz % 1000 == 0) "${hz / 1000} kHz" else "%.1f kHz".format(hz / 1000f)
        }

        val battery = if (category == AudioDeviceCategory.BLUETOOTH_BUDS || category == AudioDeviceCategory.HEARING_AID) {
            btBatteries[name.lowercase()] ?: dev.addressCompat().takeIf { it.isNotBlank() }?.let { btBatteries[it.lowercase()] }
        } else {
            null
        }

        return OutputDeviceEntry(
            id = dev.id,
            name = name,
            subtitle = subtitle,
            category = category,
            iconRes = iconRes,
            sampleRateLabel = sampleRateLabel,
            batteryPercent = battery,
            info = dev,
        )
    }

    private fun classify(dev: AudioDeviceInfo): AudioDeviceCategory = when {
        dev.isBluetoothOutput() -> AudioDeviceCategory.BLUETOOTH_BUDS
        dev.type == AudioDeviceInfo.TYPE_USB_DEVICE || dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY -> AudioDeviceCategory.USB_DAC
        dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
            dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
            dev.type == AudioDeviceInfo.TYPE_LINE_ANALOG -> AudioDeviceCategory.WIRED_OR_USB_HEADPHONE
        dev.type == AudioDeviceInfo.TYPE_HEARING_AID -> AudioDeviceCategory.HEARING_AID
        dev.type == AudioDeviceInfo.TYPE_HDMI ||
            dev.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
            dev.type == AudioDeviceInfo.TYPE_LINE_DIGITAL ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER) ||
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dev.type == AudioDeviceInfo.TYPE_BLE_BROADCAST) -> AudioDeviceCategory.EXTERNAL_SPEAKER
        dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> AudioDeviceCategory.BUILTIN_SPEAKER
        else -> AudioDeviceCategory.EXTERNAL_SPEAKER
    }

    private fun isSupportedOutputDevice(dev: AudioDeviceInfo): Boolean {
        return when {
            dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                dev.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                isBleHeadsetOrSpeaker(dev) ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_USB_DEVICE ||
                dev.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY ||
                dev.type == AudioDeviceInfo.TYPE_HEARING_AID ||
                dev.type == AudioDeviceInfo.TYPE_HDMI ||
                dev.type == AudioDeviceInfo.TYPE_HDMI_ARC ||
                dev.type == AudioDeviceInfo.TYPE_LINE_ANALOG -> true
            else -> false
        }
    }

    private fun sortPriority(dev: AudioDeviceInfo): Int = when {
        dev.isBluetoothOutput() -> 0
        dev.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES || dev.type == AudioDeviceInfo.TYPE_WIRED_HEADSET -> 1
        dev.type == AudioDeviceInfo.TYPE_USB_DEVICE || dev.type == AudioDeviceInfo.TYPE_USB_HEADSET || dev.type == AudioDeviceInfo.TYPE_USB_ACCESSORY -> 2
        dev.type == AudioDeviceInfo.TYPE_HEARING_AID -> 3
        dev.type == AudioDeviceInfo.TYPE_HDMI || dev.type == AudioDeviceInfo.TYPE_HDMI_ARC -> 4
        dev.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> 5
        else -> 6
    }

    private fun queryBondedBatteryMap(context: Context): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        runCatching {
            val bm = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager ?: return@runCatching
            val adapter = bm.adapter ?: return@runCatching
            val bonded = adapter.bondedDevices ?: return@runCatching
            val getBatMethod = Class.forName("android.bluetooth.BluetoothDevice").getMethod("getBatteryLevel")
            for (bt in bonded) {
                val lvl = (getBatMethod.invoke(bt) as? Int) ?: -1
                if (lvl in 0..100) {
                    bt.name?.takeIf { it.isNotBlank() }?.let { result[it.lowercase()] = lvl }
                    bt.address?.takeIf { it.isNotBlank() }?.let { result[it.lowercase()] = lvl }
                }
            }
        }
        return result
    }
}

private fun AudioDeviceInfo.addressCompat(): String =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) address.orEmpty() else ""

internal fun resolveOutputCapsuleIcon(
    category: AudioDeviceCategory?,
    explicitSelection: Boolean,
): Int {
    val target = category ?: return R.drawable.ic_airbuds
    return when (target) {
        AudioDeviceCategory.BLUETOOTH_BUDS -> R.drawable.ic_airbuds
        AudioDeviceCategory.WIRED_OR_USB_HEADPHONE,
        AudioDeviceCategory.USB_DAC -> R.drawable.ic_headphone
        AudioDeviceCategory.HEARING_AID -> R.drawable.ic_output_hearing_aid
        AudioDeviceCategory.EXTERNAL_SPEAKER -> R.drawable.ic_output_external_speaker
        AudioDeviceCategory.BUILTIN_SPEAKER -> R.drawable.ic_output_speaker
    }
}

internal fun resolveOutputListIcon(category: AudioDeviceCategory): Int = when (category) {
    AudioDeviceCategory.BLUETOOTH_BUDS -> R.drawable.ic_airbuds
    AudioDeviceCategory.WIRED_OR_USB_HEADPHONE -> R.drawable.ic_headphone
    AudioDeviceCategory.USB_DAC -> R.drawable.ic_output_usb_dac
    AudioDeviceCategory.BUILTIN_SPEAKER -> R.drawable.ic_output_speaker
    AudioDeviceCategory.EXTERNAL_SPEAKER -> R.drawable.ic_output_external_speaker
    AudioDeviceCategory.HEARING_AID -> R.drawable.ic_output_hearing_aid
}

fun AudioDeviceInfo.isBluetoothOutput(): Boolean {
    val bleHeadset = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && type == AudioDeviceInfo.TYPE_BLE_HEADSET
    return type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
        type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
        bleHeadset
}

private fun isBleHeadsetOrSpeaker(dev: AudioDeviceInfo): Boolean {
    return (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dev.type == AudioDeviceInfo.TYPE_BLE_HEADSET) ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && dev.type == AudioDeviceInfo.TYPE_BLE_SPEAKER)
}

fun AudioDeviceInfo.outputName(): String {
    val raw = productName?.toString()?.trim().orEmpty()
    if (raw.isNotBlank() && !raw.equals(Build.MODEL, ignoreCase = true)) {
        return raw
    }
    return when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "Phone Speaker"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "Wired Headphones"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "Wired Headset"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "Bluetooth Buds / Audio"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "Bluetooth Headset"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB-C DAC"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB-C Headset"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB Audio"
        AudioDeviceInfo.TYPE_HEARING_AID -> "Hearing Aid"
        AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC -> "HDMI Audio"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "AUX / Line Out"
        else -> if (isBleHeadsetOrSpeaker(this)) "BLE Audio" else raw.ifBlank { "Audio Device" }
    }
}

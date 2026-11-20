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

package com.darkxvenom.itune.audio

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.util.Log
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Immutable
data class CastDevice(
    val id: String,
    val name: String,
    val model: String,
    val host: String = "",
    val port: Int = 8009,
    val isConnected: Boolean = false,
)

/**
 * Native Google Cast & wireless receiver discovery engine using Android's NsdManager.
 * Discovers Chromecasts, Google TVs, Nest Audios, and smart speakers on local Wi-Fi.
 */
object CastDiscoveryManager {
    private const val TAG = "CastDiscoveryManager"
    private const val SERVICE_TYPE = "_googlecast._tcp"

    private val scope = CoroutineScope(Dispatchers.IO + Job())
    private val resolveMutex = Mutex()

    private var nsdManager: NsdManager? = null
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var isRegistered = false

    private val _devices = MutableStateFlow<List<CastDevice>>(emptyList())
    val devices: StateFlow<List<CastDevice>> = _devices.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _activeCastDevice = MutableStateFlow<CastDevice?>(null)
    val activeCastDevice: StateFlow<CastDevice?> = _activeCastDevice.asStateFlow()

    private val discoveredMap = mutableMapOf<String, CastDevice>()

    fun startDiscovery(context: Context) {
        val app = context.applicationContext
        if (isRegistered) {
            _isScanning.value = true
            return
        }

        val mgr = app.getSystemService(Context.NSD_SERVICE) as? NsdManager ?: return
        nsdManager = mgr

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String?) {
                _isScanning.value = true
                Log.d(TAG, "Cast discovery started for $regType")
            }

            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType.contains("googlecast")) {
                    resolveServiceSafely(mgr, serviceInfo)
                }
            }

            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                val id = serviceInfo.serviceName
                synchronized(discoveredMap) {
                    discoveredMap.remove(id)
                    _devices.value = discoveredMap.values.toList()
                }
            }

            override fun onDiscoveryStopped(serviceType: String?) {
                _isScanning.value = false
                isRegistered = false
                Log.d(TAG, "Cast discovery stopped")
            }

            override fun onStartDiscoveryFailed(serviceType: String?, errorCode: Int) {
                _isScanning.value = false
                isRegistered = false
                Log.w(TAG, "Start discovery failed: $errorCode")
            }

            override fun onStopDiscoveryFailed(serviceType: String?, errorCode: Int) {
                isRegistered = false
                Log.w(TAG, "Stop discovery failed: $errorCode")
            }
        }

        discoveryListener = listener
        try {
            mgr.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)
            isRegistered = true
            _isScanning.value = true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start service discovery", e)
            _isScanning.value = false
            isRegistered = false
        }
    }

    private fun resolveServiceSafely(mgr: NsdManager, info: NsdServiceInfo) {
        scope.launch {
            resolveMutex.withLock {
                try {
                    mgr.resolveService(info, object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                            Log.w(TAG, "Resolve failed for ${serviceInfo.serviceName}: $errorCode")
                        }

                        override fun onServiceResolved(resolvedInfo: NsdServiceInfo) {
                            val hostAddress = resolvedInfo.host?.hostAddress.orEmpty()
                            val rawName = resolvedInfo.serviceName

                            // Read Friendly Name (fn) and Model (md) from attributes if available
                            val fn = parseAttribute(resolvedInfo, "fn")
                            val md = parseAttribute(resolvedInfo, "md")

                            val friendlyName = when {
                                !fn.isNullOrBlank() -> fn
                                rawName.isNotBlank() -> cleanServiceName(rawName)
                                else -> "Google Cast Device"
                            }
                            val modelName = when {
                                !md.isNullOrBlank() -> md
                                else -> "Chromecast / Google TV"
                            }

                            val device = CastDevice(
                                id = rawName,
                                name = friendlyName,
                                model = modelName,
                                host = hostAddress,
                                port = resolvedInfo.port,
                                isConnected = _activeCastDevice.value?.id == rawName,
                            )

                            synchronized(discoveredMap) {
                                discoveredMap[rawName] = device
                                _devices.value = discoveredMap.values.toList()
                            }
                        }
                    })
                } catch (e: Exception) {
                    Log.w(TAG, "Error resolving service: ${e.message}")
                }
                delay(200L) // Stagger sequential resolves to avoid NsdManager busy state
            }
        }
    }

    private fun parseAttribute(info: NsdServiceInfo, key: String): String? {
        return try {
            val bytes = info.attributes[key] ?: return null
            String(bytes, Charsets.UTF_8).trim()
        } catch (e: Exception) {
            null
        }
    }

    private fun cleanServiceName(raw: String): String {
        return raw.replace(Regex("-[a-f0-9]{32}.*$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("-[A-F0-9]{12,}$"), "")
            .replace(Regex("[-_]+"), " ")
            .trim()
    }

    fun stopDiscovery() {
        val mgr = nsdManager ?: return
        val listener = discoveryListener ?: return
        if (isRegistered) {
            try {
                mgr.stopServiceDiscovery(listener)
            } catch (e: Exception) {
                Log.w(TAG, "Error stopping discovery", e)
            }
        }
        isRegistered = false
        _isScanning.value = false
    }

    fun selectCastDevice(device: CastDevice?) {
        _activeCastDevice.value = device
        synchronized(discoveredMap) {
            discoveredMap.keys.forEach { key ->
                val curr = discoveredMap[key]
                if (curr != null) {
                    discoveredMap[key] = curr.copy(isConnected = curr.id == device?.id)
                }
            }
            _devices.value = discoveredMap.values.toList()
        }
    }
}

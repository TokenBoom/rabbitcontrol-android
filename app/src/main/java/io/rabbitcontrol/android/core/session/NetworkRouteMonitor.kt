package io.rabbitcontrol.android.core.session

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.util.Log
import io.rabbitcontrol.android.core.nativebridge.NativeRuntimeBootstrap
import io.rabbitcontrol.android.session.Core
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Watches Android default network route transitions (Wi-Fi <-> 5G / router handoff) and
 * notifies the Rust core runtime so LAN discovery, signaling, and active QUIC probes
 * converge immediately instead of waiting on heartbeat timeouts.
 */
internal object NetworkRouteMonitor {
    private const val DEBOUNCE_MS = 800L

    private val registered = AtomicBoolean(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var initialized = false

    @Volatile
    private var hadLoss = false

    @Volatile
    private var lastNetworkHandle: Long? = null

    @Volatile
    private var lastLinkSignature: String? = null

    private var debounceJob: Job? = null

    fun start(context: Context) {
        if (!registered.compareAndSet(false, true)) return
        val cm = context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        val handle = network.networkHandle
                        if (!initialized) {
                            initialized = true
                            lastNetworkHandle = handle
                            hadLoss = false
                            return
                        }
                        val switched = lastNetworkHandle != null && lastNetworkHandle != handle
                        val recoveredFromLoss = hadLoss
                        lastNetworkHandle = handle
                        hadLoss = false
                        if (switched || recoveredFromLoss) {
                            scheduleNotify(if (switched) "network_available_switched" else "network_recovered")
                        }
                    }

                    override fun onLost(network: Network) {
                        if (lastNetworkHandle == network.networkHandle) {
                            hadLoss = true
                        }
                    }

                    override fun onLinkPropertiesChanged(network: Network, linkProperties: LinkProperties) {
                        if (!initialized || lastNetworkHandle != network.networkHandle) return
                        val signature = buildString {
                            append(linkProperties.interfaceName.orEmpty())
                            append('|')
                            append(linkProperties.linkAddresses.joinToString(",") { it.address.hostAddress.orEmpty() })
                        }
                        val prev = lastLinkSignature
                        lastLinkSignature = signature
                        if (prev != null && prev != signature) {
                            scheduleNotify("link_properties_changed")
                        }
                    }
                },
            )
        }.onFailure { err ->
            registered.set(false)
            Log.w("RC_DIAG", "Failed to register NetworkRouteMonitor: ${err.message}")
        }
    }

    private fun scheduleNotify(reason: String) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_MS)
            if (NativeRuntimeBootstrap.isReady) {
                Log.i("RC_DIAG", "Network route change detected ($reason); triggering Core.onNetworkChanged()")
                runCatching { Core.onNetworkChanged() }
                    .onFailure { err -> Log.w("RC_DIAG", "Core.onNetworkChanged failed: ${err.message}") }
            }
        }
    }
}

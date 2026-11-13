package io.github.throughthewind.zapper.connection

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network

/**
 * Calls [onAvailable] when the phone gets a network back, so the remote reconnects without waiting
 * for its backoff (SPEC AC-12). Registered once for the process; the callback does no work itself.
 */
class NetworkMonitor(
    context: Context,
    private val onAvailable: () -> Unit,
) {
    private val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)

    private val callback =
        object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = onAvailable()
        }

    fun start() {
        connectivity?.registerDefaultNetworkCallback(callback)
    }
}

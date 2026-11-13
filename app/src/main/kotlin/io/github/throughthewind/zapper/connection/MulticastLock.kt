package io.github.throughthewind.zapper.connection

import android.content.Context
import android.net.wifi.WifiManager

/** Holds Android's Wi-Fi multicast lock while [block] runs, so SSDP answers reach the app. */
class MulticastLock(
    context: Context,
) {
    private val wifi = context.applicationContext.getSystemService(WifiManager::class.java)

    suspend fun <T> hold(block: suspend () -> T): T {
        val lock = wifi?.createMulticastLock("zapper-discovery")?.apply { setReferenceCounted(false) }
        lock?.acquire()
        try {
            return block()
        } finally {
            lock?.release()
        }
    }
}

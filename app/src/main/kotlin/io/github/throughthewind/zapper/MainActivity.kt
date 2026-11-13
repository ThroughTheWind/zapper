package io.github.throughthewind.zapper

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.throughthewind.zapper.core.designsystem.theme.ZapperTheme
import io.github.throughthewind.zapper.core.model.RemoteKey
import io.github.throughthewind.zapper.core.remote.ConnectionState

class MainActivity : ComponentActivity() {
    private val container get() = (application as ZapperApplication).container

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZapperTheme {
                ZapperApp(container)
            }
        }
    }

    /**
     * The phone's volume keys drive the TV while one is connected and the switch is on (SPEC
     * FR-4.4); otherwise they change the phone volume as usual. Android repeats a held key, and
     * each repeat sends one step, like holding the on-screen rocker.
     */
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val key =
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> RemoteKey.VOLUME_UP
                KeyEvent.KEYCODE_VOLUME_DOWN -> RemoteKey.VOLUME_DOWN
                else -> null
            }
        val forTv = container.hardwareVolumeKeys.value && container.connection.state.value == ConnectionState.Connected
        if (key == null || !forTv) return super.dispatchKeyEvent(event)
        if (event.action == KeyEvent.ACTION_DOWN) container.connection.send(key)
        // Up events are swallowed too, so the phone never shows its volume panel.
        return true
    }

    // The connection lives only while the remote is visible (SPEC FR-3.4, NFR-12).
    override fun onStart() {
        super.onStart()
        container.connection.resume()
    }

    override fun onStop() {
        container.connection.pause()
        super.onStop()
    }
}

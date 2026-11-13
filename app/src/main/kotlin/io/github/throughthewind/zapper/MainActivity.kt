package io.github.throughthewind.zapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.throughthewind.zapper.core.designsystem.theme.ZapperTheme

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

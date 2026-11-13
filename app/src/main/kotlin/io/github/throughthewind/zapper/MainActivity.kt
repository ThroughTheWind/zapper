package io.github.throughthewind.zapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import io.github.throughthewind.zapper.core.designsystem.theme.ZapperTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as ZapperApplication).container
        setContent {
            ZapperTheme {
                ZapperApp(container)
            }
        }
    }
}

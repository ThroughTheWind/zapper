package io.github.throughthewind.zapper

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import io.github.throughthewind.zapper.core.designsystem.ZapperTheme

class MainActivity : ComponentActivity() {
    private val viewModel: RemoteViewModel by viewModels {
        RemoteViewModel.factory((application as ZapperApplication).container)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ZapperTheme {
                RemoteScreen(viewModel)
            }
        }
    }
}

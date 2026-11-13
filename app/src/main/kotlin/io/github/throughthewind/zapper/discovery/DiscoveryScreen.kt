package io.github.throughthewind.zapper.discovery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.throughthewind.zapper.core.designsystem.components.Banner
import io.github.throughthewind.zapper.core.designsystem.components.BannerKind
import io.github.throughthewind.zapper.core.designsystem.components.DeviceCard
import io.github.throughthewind.zapper.core.designsystem.components.DeviceCardState
import io.github.throughthewind.zapper.core.designsystem.components.DiscoveryScanIndicator
import io.github.throughthewind.zapper.core.remote.DiscoveredTv

/**
 * Discovery (docs/design/components/OnboardingFlow step 2-3, DiscoveryScan, DeviceCard, TextField).
 * Copy says "TV", never protocol names.
 */
@Composable
fun DiscoveryScreen(
    viewModel: DiscoveryViewModel,
    brandName: (String) -> String,
    onSelect: (DiscoveredTv) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showManual by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.scan() }

    Surface(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.safeDrawingPadding().padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    DiscoveryScanIndicator(animate = state.scanning)
                    Text(
                        if (state.scanning) "Looking for TVs on your Wi-Fi…" else "Choose your TV",
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
            items(state.found, key = { it.identity }) { tv ->
                val brand = brandName(tv.adapterId)
                DeviceCard(
                    title = tv.name,
                    subtitle = listOfNotNull(tv.model, tv.host, brand).joinToString(" · "),
                    brand = brand,
                    state = DeviceCardState.Found,
                    onClick = { onSelect(tv) },
                )
            }
            if (state.nothingFound) {
                item {
                    Banner(
                        kind = BannerKind.Neutral,
                        icon = Icons.Rounded.Lightbulb,
                        title = "No TV found",
                        body =
                            "Check that the TV is on and on the same Wi-Fi as this phone, not a guest network. " +
                                "If you denied Zapper before, allow it again on the TV under Device Connection Manager.",
                    ) {
                        TextButton(onClick = viewModel::scan) { Text("Scan again") }
                    }
                }
            }
            item {
                if (showManual || state.nothingFound) {
                    ManualAddress(state, viewModel::onAddressChange) { viewModel.addByAddress(onSelect) }
                } else {
                    TextButton(onClick = { showManual = true }, modifier = Modifier.fillMaxWidth()) { Text("Not found? Add by IP address") }
                }
            }
        }
    }
}

@Composable
private fun ManualAddress(
    state: DiscoveryUiState,
    onChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 24.dp)) {
        OutlinedTextField(
            value = state.manualAddress,
            onValueChange = onChange,
            label = { Text("TV IP address") },
            singleLine = true,
            isError = state.manualError != null,
            supportingText = {
                Text(
                    when {
                        state.manualChecking -> "Checking ${state.manualAddress}…"
                        state.manualError != null -> state.manualError
                        else -> "Find it on the TV under Settings › General › Network › Network Status"
                    },
                )
            },
            trailingIcon = {
                when {
                    state.manualChecking -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    state.manualError != null -> Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                }
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(onGo = { onSubmit() }),
            modifier = Modifier.fillMaxWidth(),
        )
        FilledTonalButton(onClick = onSubmit, enabled = !state.manualChecking, modifier = Modifier.fillMaxWidth()) {
            Text("Add by IP address")
        }
    }
}

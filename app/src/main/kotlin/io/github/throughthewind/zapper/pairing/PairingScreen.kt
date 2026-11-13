package io.github.throughthewind.zapper.pairing

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.core.designsystem.components.Banner
import io.github.throughthewind.zapper.core.designsystem.components.BannerKind
import io.github.throughthewind.zapper.core.designsystem.components.PairingHero
import io.github.throughthewind.zapper.core.designsystem.components.PairingOutcome
import io.github.throughthewind.zapper.core.designsystem.components.PairingSteps
import io.github.throughthewind.zapper.core.designsystem.components.PinEntry
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteHaptic
import io.github.throughthewind.zapper.core.designsystem.theme.rememberRemoteHaptics
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.core.remote.ConnectionState
import io.github.throughthewind.zapper.core.remote.PairingRequest
import io.github.throughthewind.zapper.core.remote.RepairReason
import io.github.throughthewind.zapper.core.remote.TvError
import io.github.throughthewind.zapper.core.remote.TvException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How long the success state stays before the remote opens (PairingProgress: auto-continue after 1 s). */
private const val SUCCESS_HOLD_MS = 1_000L

/**
 * The shared pairing flow every adapter drives (docs/design/components/PairingFlow): connecting,
 * approve on the TV or type its code, then paired, denied or timed out. Copy names no protocol.
 */
@Composable
fun PairingScreen(
    device: TvDevice?,
    state: ConnectionState,
    repairReason: RepairReason?,
    onPaired: () -> Unit,
    onRetry: () -> Unit,
    onPairAgain: () -> Unit,
    onChooseAnotherTv: () -> Unit,
) {
    val haptics = rememberRemoteHaptics()
    val name = device?.name ?: "your TV"

    LaunchedEffect(state) {
        when (state) {
            ConnectionState.Connected -> {
                haptics.perform(RemoteHaptic.Success)
                delay(SUCCESS_HOLD_MS)
                onPaired()
            }

            is ConnectionState.Failed -> {
                haptics.perform(RemoteHaptic.Failure)
            }

            else -> {
                Unit
            }
        }
    }

    Surface(Modifier.fillMaxSize()) {
        Column(
            modifier =
                Modifier
                    .safeDrawingPadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            repairReason?.let {
                Banner(kind = BannerKind.Info, icon = Icons.Rounded.Info, title = it.explain())
            }
            when (state) {
                ConnectionState.Disconnected, ConnectionState.Connecting -> {
                    Progress("Connecting to $name…")
                }

                is ConnectionState.AwaitingPairing -> {
                    when (val request = state.request) {
                        is PairingRequest.ConfirmOnTv -> ConfirmOnTv(name)
                        is PairingRequest.EnterCode -> EnterCode(name, request)
                    }
                }

                ConnectionState.Connected -> {
                    Outcome(success = true, title = "Paired with $name", body = "Opening the remote…")
                }

                is ConnectionState.Failed -> {
                    Failed(state.error, onRetry, onPairAgain, onChooseAnotherTv)
                }
            }
        }
    }
}

private val confirmSteps = listOf("Connect to the TV", "Allow Zapper on the TV", "Paired")
private val codeSteps = listOf("Connect to the TV", "Type the code shown on the TV", "Paired")

@Composable
private fun Progress(title: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    PairingSteps(confirmSteps, current = 0, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ConfirmOnTv(name: String) {
    PairingHero(prompt = "Allow \"Zapper\" to connect?")
    Text("Look at $name", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        "Choose Allow with your TV's remote. The prompt closes by itself after about 30 seconds.",
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PairingSteps(confirmSteps, current = 1, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun EnterCode(
    name: String,
    request: PairingRequest.EnterCode,
) {
    val scope = rememberCoroutineScope()
    var code by remember(request) { mutableStateOf("") }
    var checking by remember(request) { mutableStateOf(false) }
    var wrong by remember(request) { mutableStateOf(false) }

    Text("Type the code on $name", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    PinEntry(
        value = code,
        length = request.length,
        hex = request.hex,
        enabled = !checking,
        onValueChange = {
            code = it
            wrong = false
        },
        onComplete = { typed ->
            checking = true
            scope.launch {
                try {
                    request.submit(typed)
                } catch (_: TvException) {
                    wrong = true
                    code = ""
                } finally {
                    checking = false
                }
            }
        },
    )
    Text(
        when {
            checking -> "Checking…"
            wrong -> "That code did not match. Try the one on the TV now."
            else -> "${request.length} characters, shown on the TV screen"
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PairingSteps(codeSteps, current = 1, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Failed(
    error: TvError,
    onRetry: () -> Unit,
    onPairAgain: () -> Unit,
    onChooseAnotherTv: () -> Unit,
) {
    val (title, body) = error.explain()
    Outcome(success = false, title = title, body = body)
    if (error == TvError.CertificateChanged) {
        Button(onClick = onPairAgain, modifier = Modifier.fillMaxWidth()) { Text("Pair again") }
    } else {
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text("Try again") }
    }
    TextButton(onClick = onChooseAnotherTv) { Text("Choose another TV") }
}

@Composable
private fun Outcome(
    success: Boolean,
    title: String,
    body: String,
) {
    Spacer(Modifier.height(24.dp))
    PairingOutcome(success = success)
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(body, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

private fun RepairReason.explain(): String =
    when (this) {
        RepairReason.TokenRejected -> "Your TV asked to approve Zapper again"
        RepairReason.IdentityChanged -> "Pairing again with a TV whose identity changed"
    }

/** Title and explanation for each failure, in plain words (docs/design/components/ErrorsAndHelp). */
private fun TvError.explain(): Pair<String, String> =
    when (this) {
        TvError.PairingRefused -> {
            "The TV said no" to
                "If you chose Deny by mistake, allow Zapper again on the TV under " +
                "Settings › General › External Device Manager › Device Connection Manager, then try again."
        }

        TvError.PairingTimedOut -> {
            "Nobody answered on the TV" to "The prompt closed before anyone chose Allow. Try again and keep an eye on the TV."
        }

        TvError.CertificateChanged -> {
            "This TV's identity changed" to
                "The TV at this address is not presenting the same identity as when you paired it. " +
                "That happens after a factory reset, but it can also mean another device is pretending to be your TV. " +
                "Pair again only if you trust your network."
        }

        is TvError.Unreachable -> {
            "Can't reach the TV" to "Check that the TV is on and on the same Wi-Fi as this phone."
        }

        else -> {
            "Couldn't connect" to message
        }
    }

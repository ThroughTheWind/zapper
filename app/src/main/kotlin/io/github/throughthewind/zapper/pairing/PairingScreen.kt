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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.throughthewind.zapper.R
import io.github.throughthewind.zapper.UiText
import io.github.throughthewind.zapper.asString
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
import io.github.throughthewind.zapper.describe
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
    val name = device?.name ?: stringResource(R.string.pairing_your_tv)

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
                Banner(kind = BannerKind.Info, icon = Icons.Rounded.Info, title = stringResource(it.explain()))
            }
            when (state) {
                ConnectionState.Disconnected, ConnectionState.Connecting -> {
                    Progress(stringResource(R.string.pairing_connecting_to, name))
                }

                is ConnectionState.AwaitingPairing -> {
                    when (val request = state.request) {
                        is PairingRequest.ConfirmOnTv -> ConfirmOnTv(name)
                        is PairingRequest.EnterCode -> EnterCode(name, request)
                    }
                }

                ConnectionState.Connected -> {
                    Outcome(
                        success = true,
                        title = stringResource(R.string.pairing_paired_with, name),
                        body = stringResource(R.string.pairing_opening_remote),
                    )
                }

                is ConnectionState.Failed -> {
                    Failed(state.error, onRetry, onPairAgain, onChooseAnotherTv)
                }
            }
        }
    }
}

private val confirmSteps = listOf(R.string.pairing_step_connect, R.string.pairing_step_allow, R.string.pairing_step_paired)
private val codeSteps = listOf(R.string.pairing_step_connect, R.string.pairing_step_type_code, R.string.pairing_step_paired)

@Composable
private fun List<Int>.strings(): List<String> = map { stringResource(it) }

@Composable
private fun Progress(title: String) {
    Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    PairingSteps(confirmSteps.strings(), current = 0, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun ConfirmOnTv(name: String) {
    PairingHero(prompt = stringResource(R.string.pairing_tv_prompt))
    Text(stringResource(R.string.pairing_look_at, name), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
    Text(
        stringResource(R.string.pairing_choose_allow),
        style = MaterialTheme.typography.bodyLarge,
        textAlign = TextAlign.Center,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PairingSteps(confirmSteps.strings(), current = 1, modifier = Modifier.fillMaxWidth())
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

    Text(stringResource(R.string.pairing_type_code_on, name), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
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
            checking -> stringResource(R.string.pairing_checking)
            wrong -> stringResource(R.string.pairing_code_wrong)
            else -> pluralStringResource(R.plurals.pairing_code_length, request.length, request.length)
        },
        style = MaterialTheme.typography.bodyMedium,
        color = if (wrong) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    )
    PairingSteps(codeSteps.strings(), current = 1, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun Failed(
    error: TvError,
    onRetry: () -> Unit,
    onPairAgain: () -> Unit,
    onChooseAnotherTv: () -> Unit,
) {
    val (title, body) = error.explain().let { (t, b) -> stringResource(t) to b.asString() }
    Outcome(success = false, title = title, body = body)
    if (error == TvError.CertificateChanged) {
        Button(onClick = onPairAgain, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.pairing_pair_again)) }
    } else {
        Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.action_try_again)) }
    }
    TextButton(onClick = onChooseAnotherTv) { Text(stringResource(R.string.pairing_choose_another_tv)) }
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

private fun RepairReason.explain(): Int =
    when (this) {
        RepairReason.TokenRejected -> R.string.pairing_repair_token_rejected
        RepairReason.IdentityChanged -> R.string.pairing_repair_identity_changed
    }

/** Title and explanation for each failure, in plain words (docs/design/components/ErrorsAndHelp). */
private fun TvError.explain(): Pair<Int, UiText> =
    when (this) {
        TvError.PairingRefused -> {
            R.string.pairing_refused_title to UiText.Res(R.string.pairing_refused_body)
        }

        TvError.PairingTimedOut -> {
            R.string.pairing_timed_out_title to UiText.Res(R.string.pairing_timed_out_body)
        }

        TvError.CertificateChanged -> {
            R.string.pairing_identity_changed_title to UiText.Res(R.string.pairing_identity_changed_body)
        }

        is TvError.Unreachable -> {
            R.string.pairing_unreachable_title to UiText.Res(R.string.tv_unreachable_body)
        }

        else -> {
            R.string.pairing_failed_title to describe()
        }
    }

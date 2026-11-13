package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.throughthewind.zapper.core.designsystem.theme.KeyType
import io.github.throughthewind.zapper.core.designsystem.theme.RemoteGrid

/** A number pad key: a digit, or one of the two Ghost keys in the bottom corners. */
sealed interface NumberPadKey {
    data class Digit(
        val value: Int,
    ) : NumberPadKey

    data object ChannelList : NumberPadKey

    data object PreviousChannel : NumberPadKey
}

private val padRows =
    listOf(
        listOf(1, 2, 3).map { NumberPadKey.Digit(it) },
        listOf(4, 5, 6).map { NumberPadKey.Digit(it) },
        listOf(7, 8, 9).map { NumberPadKey.Digit(it) },
        listOf(NumberPadKey.ChannelList, NumberPadKey.Digit(0), NumberPadKey.PreviousChannel),
    )

/**
 * A fixed 3x4 grid of digits for direct channel entry (docs/design/components/NumberPad). Digits
 * are Standard keys in the key-numeral style; channel list and previous channel are Ghost keys.
 * No repeat on hold.
 */
@Composable
fun NumberPad(
    onPress: (NumberPadKey) -> Unit,
    modifier: Modifier = Modifier,
    enabled: (NumberPadKey) -> Boolean = { true },
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(RemoteGrid.gutter)) {
        padRows.forEach { row ->
            Row(Modifier.fillMaxWidth().weight(1f), horizontalArrangement = Arrangement.spacedBy(RemoteGrid.gutter)) {
                row.forEach { key ->
                    val label =
                        when (key) {
                            is NumberPadKey.Digit -> "${key.value}"
                            NumberPadKey.ChannelList -> "Channel list"
                            NumberPadKey.PreviousChannel -> "Previous channel"
                        }
                    RemoteKey(
                        label = label,
                        onPress = { onPress(key) },
                        emphasis = if (key is NumberPadKey.Digit) KeyEmphasis.Standard else KeyEmphasis.Ghost,
                        enabled = enabled(key),
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    ) {
                        when (key) {
                            is NumberPadKey.Digit -> Text(label, style = KeyType.numeral)
                            NumberPadKey.ChannelList -> Icon(Icons.AutoMirrored.Rounded.List, contentDescription = null)
                            NumberPadKey.PreviousChannel -> Icon(Icons.Rounded.History, contentDescription = null)
                        }
                    }
                }
            }
        }
    }
}

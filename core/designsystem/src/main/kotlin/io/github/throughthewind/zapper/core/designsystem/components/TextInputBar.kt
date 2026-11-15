package io.github.throughthewind.zapper.core.designsystem.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * The bar above the phone keyboard that sends typed text to the TV
 * (docs/design/components/TextInputBar): a pill field, a round primary Send button and a status
 * line. It takes focus when shown, so the phone keyboard opens with it.
 */
@Composable
fun TextInputBar(
    value: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    status: String,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val focus = remember { FocusRequester() }
    val canSend = value.isNotEmpty()
    LaunchedEffect(Unit) { focus.requestFocus() }

    Surface(
        color = colors.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Keyboard, contentDescription = null, tint = colors.onSurfaceVariant)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    cursorBrush = SolidColor(colors.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                    modifier =
                        Modifier
                            .weight(1f)
                            .focusRequester(focus)
                            .semantics { contentDescription = "Text for the TV" },
                    decorationBox = { field ->
                        Box(
                            Modifier
                                .background(colors.surfaceContainerHighest, CircleShape)
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) { field() }
                    },
                )
                FilledIconButton(onClick = onSend, enabled = canSend) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send to the TV")
                }
            }
            Text(status, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
    }
}

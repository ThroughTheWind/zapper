package io.github.throughthewind.zapper.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.horizontalScroll
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DashboardCustomize
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.IosShare
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Vibration
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.throughthewind.zapper.core.designsystem.components.KeyEmphasis
import io.github.throughthewind.zapper.core.designsystem.components.RemoteKey
import io.github.throughthewind.zapper.core.designsystem.theme.shape
import io.github.throughthewind.zapper.core.model.AppearanceSettings
import io.github.throughthewind.zapper.core.model.ColorChoice
import io.github.throughthewind.zapper.core.model.HapticStrength
import io.github.throughthewind.zapper.core.model.KeyShapeChoice
import io.github.throughthewind.zapper.core.model.ThemeChoice
import io.github.throughthewind.zapper.core.model.TvDevice
import io.github.throughthewind.zapper.layout.rememberLayoutFilePicker
import io.github.throughthewind.zapper.layout.shareLayout
import io.github.throughthewind.zapper.remote.SavedTvRow
import kotlinx.coroutines.launch

/** The settings pages (docs/design/components/SettingsScreens). */
enum class SettingsPage(
    val title: String,
) {
    Root("Settings"),
    Tvs("TVs"),
    Layout("Layout"),
    Appearance("Appearance"),
    Feedback("Feedback"),
    Controls("Controls"),
    About("About"),
}

/**
 * Settings, as the spec lists them: TVs, Layout, Appearance, Feedback, Controls, About. Every
 * change applies at once, with no restart (SPEC AC-17).
 */
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    brandName: (String) -> String,
    onBack: () -> Unit,
    onEditLayout: () -> Unit,
    onAddTv: () -> Unit,
    onNoTvLeft: () -> Unit,
) {
    var page by rememberSaveable { mutableStateOf(SettingsPage.Root) }
    val snackbar = remember { SnackbarHostState() }
    BackHandler { if (page == SettingsPage.Root) onBack() else page = SettingsPage.Root }
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it) } }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = { if (page == SettingsPage.Root) onBack() else page = SettingsPage.Root }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                    Text(page.title, style = MaterialTheme.typography.titleLarge)
                }
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                    when (page) {
                        SettingsPage.Root -> RootPage(viewModel) { page = it }
                        SettingsPage.Tvs -> TvsPage(viewModel, brandName, onAddTv, onNoTvLeft)
                        SettingsPage.Layout -> LayoutPage(viewModel, onEditLayout)
                        SettingsPage.Appearance -> AppearancePage(viewModel)
                        SettingsPage.Feedback -> FeedbackPage(viewModel)
                        SettingsPage.Controls -> ControlsPage(viewModel)
                        SettingsPage.About -> AboutPage()
                    }
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
        }
    }
}

@Composable
private fun RootPage(
    viewModel: SettingsViewModel,
    open: (SettingsPage) -> Unit,
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val appearance by viewModel.appearance.collectAsStateWithLifecycle()
    val volumeKeys by viewModel.volumeKeys.collectAsStateWithLifecycle()
    NavItem(Icons.Rounded.Tv, "TVs", devices.joinToString { it.name }.ifEmpty { "No saved TVs" }) { open(SettingsPage.Tvs) }
    NavItem(Icons.Rounded.DashboardCustomize, "Layout", "Edit, reset, import and export") { open(SettingsPage.Layout) }
    NavItem(Icons.Rounded.Palette, "Appearance", appearance.appearanceSummary()) { open(SettingsPage.Appearance) }
    NavItem(Icons.Rounded.Vibration, "Feedback", appearance.feedbackSummary()) { open(SettingsPage.Feedback) }
    NavItem(
        Icons.Rounded.Tune,
        "Controls",
        "Volume keys ${if (volumeKeys) "on" else "off"}, ${appearance.repeatSummary()} repeat",
    ) { open(SettingsPage.Controls) }
    NavItem(Icons.Rounded.Info, "About", "Version ${versionName()} · GPL-3.0") { open(SettingsPage.About) }
}

private fun AppearanceSettings.appearanceSummary(): String =
    listOfNotNull(theme.title, "OLED black".takeIf { oledBlack }, "${color.title} colour").joinToString(" · ")

private fun AppearanceSettings.feedbackSummary(): String =
    "Haptics ${haptics.title.lowercase()}, ${if (clickSound) "click sound" else "no sound"}"

private fun AppearanceSettings.repeatSummary(): String =
    when {
        repeatSpeed < AppearanceSettings.DEFAULT_REPEAT_SPEED -> "slow"
        repeatSpeed > AppearanceSettings.DEFAULT_REPEAT_SPEED -> "fast"
        else -> "normal"
    }

@Composable
private fun TvsPage(
    viewModel: SettingsViewModel,
    brandName: (String) -> String,
    onAddTv: () -> Unit,
    onNoTvLeft: () -> Unit,
) {
    val devices by viewModel.devices.collectAsStateWithLifecycle()
    val current by viewModel.device.collectAsStateWithLifecycle()
    val defaultId by viewModel.defaultDeviceId.collectAsStateWithLifecycle()
    devices.forEach { tv ->
        val active = tv.id == current?.id
        SavedTvRow(
            tv = tv,
            active = active,
            status =
                listOfNotNull(
                    if (active) "Current" else "Saved",
                    "Default".takeIf { tv.id == defaultId },
                    brandName(tv.adapterId),
                ).joinToString(" · "),
            brand = brandName(tv.adapterId),
            onClick = {},
            onAction = { viewModel.act(tv, it, onCurrentForgotten = onNoTvLeft) },
        )
    }
    NavItem(Icons.Rounded.Add, "Add a TV", null, onClick = onAddTv)
}

@Composable
private fun LayoutPage(
    viewModel: SettingsViewModel,
    onEditLayout: () -> Unit,
) {
    val device by viewModel.device.collectAsStateWithLifecycle()
    val others by viewModel.otherDevices.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmReset by remember { mutableStateOf(false) }
    var pickSource by remember { mutableStateOf(false) }
    val pickFile = rememberLayoutFilePicker(onText = viewModel::importLayout, onError = viewModel::report)
    val tvName = device?.name ?: "this TV"

    NavItem(Icons.Rounded.Edit, "Edit layout", "Opens edit mode on the remote", onClick = onEditLayout)
    NavItem(Icons.Rounded.RestartAlt, "Reset to default", "Restore the default layout for $tvName") { confirmReset = true }
    NavItem(
        Icons.Rounded.ContentCopy,
        "Copy layout from another TV",
        if (others.isEmpty()) "No other saved TV" else "Replaces the layout of $tvName",
        enabled = others.isNotEmpty(),
    ) { pickSource = true }
    NavItem(Icons.Rounded.IosShare, "Export layout", "Share as a .json file") {
        scope.launch { shareLayout(context, viewModel.currentLayout()) }
    }
    NavItem(Icons.Rounded.Download, "Import layout", "From a .json file. Replaces the layout of $tvName", onClick = pickFile)
    Text(
        "Imported buttons this version of Zapper can't run are kept as placeholders, so nothing is lost.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset the layout?",
            body = "Every change to the layout of $tvName will be lost: its pages and buttons go back to the default.",
            confirm = "Reset",
            onConfirm = {
                confirmReset = false
                viewModel.resetLayout()
            },
            onDismiss = { confirmReset = false },
        )
    }
    if (pickSource) {
        CopyFromDialog(
            devices = others,
            onPick = {
                pickSource = false
                viewModel.copyLayoutFrom(it)
            },
            onDismiss = { pickSource = false },
        )
    }
}

@Composable
internal fun CopyFromDialog(
    devices: List<TvDevice>,
    onPick: (TvDevice) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy layout from") },
        text = {
            Column {
                devices.forEach { tv ->
                    ListItem(
                        headlineContent = { Text(tv.name) },
                        leadingContent = { Icon(Icons.Rounded.Tv, contentDescription = null) },
                        modifier = Modifier.clickable { onPick(tv) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** Confirms a destructive action (docs/design/components/Dialog): Cancel first, the verb last in error colour. */
@Composable
internal fun ConfirmDialog(
    title: String,
    body: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = { Text(title) },
        text = { Text(body) },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearancePage(viewModel: SettingsViewModel) {
    val settings by viewModel.appearance.collectAsStateWithLifecycle()
    SectionHeader("Theme")
    Segmented(ThemeChoice.entries, settings.theme, { it.title }) { choice -> viewModel.updateAppearance { it.copy(theme = choice) } }
    SwitchItem("OLED black", "Pure black background in dark theme", settings.oledBlack) { on ->
        viewModel.updateAppearance { it.copy(oledBlack = on) }
    }

    SectionHeader("Colour")
    val dynamicAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val colors = ColorChoice.entries.filter { it != ColorChoice.DYNAMIC || dynamicAvailable }
    // Without dynamic colour, the theme falls back to Indigo, so Indigo reads as chosen.
    val chosen = if (settings.color == ColorChoice.DYNAMIC && !dynamicAvailable) ColorChoice.INDIGO else settings.color
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp),
    ) {
        colors.forEach { choice ->
            ColorSwatch(choice, selected = choice == chosen) { viewModel.updateAppearance { it.copy(color = choice) } }
        }
    }
    if (dynamicAvailable) {
        Text(
            "Dynamic uses your wallpaper colours.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }

    SectionHeader("Key style")
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        KeyShapeChoice.entries.forEach { choice ->
            ShapeOption(choice, selected = choice == settings.keyShape) { viewModel.updateAppearance { it.copy(keyShape = choice) } }
        }
    }

    SectionHeader("Preview")
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        PreviewKey("Power", Icons.Rounded.PowerSettingsNew, KeyEmphasis.Power)
        PreviewKey("Home", Icons.Rounded.Home, KeyEmphasis.Standard)
        PreviewKey("OK", null, KeyEmphasis.Primary)
        PreviewKey("TV", Icons.Rounded.Tv, KeyEmphasis.Tertiary)
    }
}

@Composable
private fun PreviewKey(
    label: String,
    icon: ImageVector?,
    emphasis: KeyEmphasis,
) {
    RemoteKey(label = label, onPress = {}, emphasis = emphasis, modifier = Modifier.size(64.dp)) {
        if (icon != null) Icon(icon, contentDescription = null) else Text(label, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
private fun ColorSwatch(
    choice: ColorChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    val fills: List<Color> =
        if (choice == ColorChoice.DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val scheme = remember(context) { dynamicLightColorScheme(context) }
            listOf(scheme.primary, scheme.secondary, scheme.tertiary, scheme.primaryContainer)
        } else {
            listOf(choice.seed()?.seed ?: Color.Gray)
        }
    val ring = MaterialTheme.colorScheme.primary
    Box(
        Modifier
            .size(58.dp)
            .then(if (selected) Modifier.border(3.dp, ring, CircleShape) else Modifier)
            .padding(5.dp)
            .clip(CircleShape)
            .drawBehind {
                if (fills.size == 1) {
                    drawRect(fills[0])
                } else {
                    val half = Size(size.width / 2, size.height / 2)
                    drawRect(fills[0], Offset.Zero, half)
                    drawRect(fills[1], Offset(half.width, 0f), half)
                    drawRect(fills[2], Offset(0f, half.height), half)
                    drawRect(fills[3], Offset(half.width, half.height), half)
                }
            }.selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "${choice.title} colour" },
    )
}

@Composable
private fun ShapeOption(
    choice: KeyShapeChoice,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier.selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
    ) {
        val shape = choice.style().shape
        Box(
            Modifier
                .size(48.dp)
                .then(if (selected) Modifier.border(3.dp, colors.primary, shape) else Modifier)
                .padding(4.dp)
                .clip(shape)
                .background(if (selected) colors.secondaryContainer else colors.surfaceContainerHigh),
        )
        Text(choice.title, style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FeedbackPage(viewModel: SettingsViewModel) {
    val settings by viewModel.appearance.collectAsStateWithLifecycle()
    SectionHeader("Haptics on press")
    Segmented(HapticStrength.entries, settings.haptics, { it.title }) { choice -> viewModel.updateAppearance { it.copy(haptics = choice) } }
    SwitchItem("Click sound", "A click on every key press, if touch sounds are on for the phone", settings.clickSound) { on ->
        viewModel.updateAppearance { it.copy(clickSound = on) }
    }
    Text(
        "Keys always show when they are pressed.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun ControlsPage(viewModel: SettingsViewModel) {
    val settings by viewModel.appearance.collectAsStateWithLifecycle()
    val volumeKeys by viewModel.volumeKeys.collectAsStateWithLifecycle()
    SwitchItem("Phone volume keys control the TV", "While a TV is connected and Zapper is open", volumeKeys, viewModel::setVolumeKeys)
    SectionHeader("Repeat speed when holding a key")
    Slider(
        value = settings.repeatSpeed.toFloat(),
        onValueChange = { value -> viewModel.updateAppearance { it.copy(repeatSpeed = Math.round(value)) } },
        valueRange = 1f..AppearanceSettings.REPEAT_INTERVALS_MS.size.toFloat(),
        steps = AppearanceSettings.REPEAT_INTERVALS_MS.size - 2,
        modifier = Modifier.padding(horizontal = 24.dp).semantics { contentDescription = "Repeat speed" },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Slow", style = MaterialTheme.typography.labelMedium)
        Text("Fast", style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    InfoItem("Version", versionName())
    InfoItem("License", "GNU General Public License v3.0. Free and open source.")
    NavItem(null, "Source code", SOURCE_URL) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
    }
    InfoItem("Privacy", "Zapper talks only to TVs on your network. No account, no ads, no tracking.")
    InfoItem(
        "Open source licences",
        "Built with Kotlin, kotlinx.coroutines, kotlinx.serialization, AndroidX, Jetpack Compose and OkHttp, " +
            "all under the Apache License 2.0.",
    )
}

@Composable
private fun versionName(): String {
    val context = LocalContext.current
    return remember(context) {
        runCatching {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "?"
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun NavItem(
    icon: ImageVector?,
    title: String,
    summary: String?,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val alpha = if (enabled) 1f else 0.38f
    ListItem(
        headlineContent = { Text(title, color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)) },
        supportingContent =
            if (summary != null) {
                {
                    Text(
                        summary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha),
                    )
                }
            } else {
                null
            },
        leadingContent = if (icon != null) ({ Icon(icon, contentDescription = null) }) else null,
        trailingContent = { Icon(Icons.Rounded.ChevronRight, contentDescription = null) },
        modifier = Modifier.clickable(enabled = enabled, onClick = onClick),
    )
}

@Composable
private fun InfoItem(
    title: String,
    value: String,
) {
    ListItem(headlineContent = { Text(title) }, supportingContent = { Text(value) })
}

@Composable
private fun SwitchItem(
    title: String,
    summary: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(summary) },
        trailingContent = { Switch(checked = checked, onCheckedChange = null) },
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = onChange),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> Segmented(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
            ) { Text(label(option)) }
        }
    }
    Spacer(Modifier.height(4.dp))
}

private const val SOURCE_URL = "https://github.com/throughthewind/zapper"

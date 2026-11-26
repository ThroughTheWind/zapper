package io.github.throughthewind.zapper.settings

import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.rounded.Language
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
import androidx.compose.runtime.ReadOnlyComposable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.throughthewind.zapper.R
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
    @param:StringRes val title: Int,
) {
    Root(R.string.settings_title),
    Tvs(R.string.settings_tvs),
    Layout(R.string.settings_layout),
    Appearance(R.string.settings_appearance),
    Feedback(R.string.settings_feedback),
    Controls(R.string.settings_controls),
    About(R.string.settings_about),
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
    val resources = LocalContext.current.resources
    LaunchedEffect(viewModel) { viewModel.messages.collect { snackbar.showSnackbar(it.resolve(resources)) } }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.safeDrawingPadding()) {
            Column(Modifier.fillMaxSize()) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(4.dp)) {
                    IconButton(onClick = { if (page == SettingsPage.Root) onBack() else page = SettingsPage.Root }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                    Text(stringResource(page.title), style = MaterialTheme.typography.titleLarge)
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
    NavItem(
        Icons.Rounded.Tv,
        stringResource(R.string.settings_tvs),
        devices.joinToString { it.name }.ifEmpty { stringResource(R.string.settings_no_saved_tvs) },
    ) { open(SettingsPage.Tvs) }
    NavItem(Icons.Rounded.DashboardCustomize, stringResource(R.string.settings_layout), stringResource(R.string.settings_layout_summary)) {
        open(SettingsPage.Layout)
    }
    NavItem(Icons.Rounded.Palette, stringResource(R.string.settings_appearance), appearance.appearanceSummary()) {
        open(SettingsPage.Appearance)
    }
    NavItem(
        Icons.Rounded.Vibration,
        stringResource(R.string.settings_feedback),
        appearance.feedbackSummary(),
    ) { open(SettingsPage.Feedback) }
    NavItem(
        Icons.Rounded.Tune,
        stringResource(R.string.settings_controls),
        listOf(
            stringResource(if (volumeKeys) R.string.settings_volume_keys_on else R.string.settings_volume_keys_off),
            appearance.repeatSummary(),
        ).joinToString(", "),
    ) { open(SettingsPage.Controls) }
    LanguageItem()
    NavItem(Icons.Rounded.Info, stringResource(R.string.settings_about), stringResource(R.string.settings_about_summary, versionName())) {
        open(SettingsPage.About)
    }
}

/**
 * The app's language (Android 13+): opens the system's per-app language page, which lists the
 * languages Zapper ships. Older Android versions follow the phone's language only.
 */
@Composable
private fun LanguageItem() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val chosen = remember(context) { context.getSystemService(LocaleManager::class.java)?.applicationLocales?.get(0) }
    NavItem(
        Icons.Rounded.Language,
        stringResource(R.string.settings_language),
        chosen?.let {
            it.getDisplayName(it).replaceFirstChar { c ->
                c.titlecase(it)
            }
        } ?: stringResource(R.string.settings_language_system),
    ) {
        context.startActivity(Intent(Settings.ACTION_APP_LOCALE_SETTINGS, Uri.fromParts("package", context.packageName, null)))
    }
}

@Composable
@ReadOnlyComposable
private fun AppearanceSettings.appearanceSummary(): String =
    listOfNotNull(
        theme.title(),
        stringResource(R.string.settings_oled_black).takeIf { oledBlack },
        stringResource(R.string.settings_colour_summary, color.title()),
    ).joinToString(" · ")

@Composable
@ReadOnlyComposable
private fun AppearanceSettings.feedbackSummary(): String =
    listOf(
        stringResource(
            when (haptics) {
                HapticStrength.OFF -> R.string.settings_haptics_summary_off
                HapticStrength.LIGHT -> R.string.settings_haptics_summary_light
                HapticStrength.STRONG -> R.string.settings_haptics_summary_strong
            },
        ),
        stringResource(if (clickSound) R.string.settings_click_sound_summary_on else R.string.settings_click_sound_summary_off),
    ).joinToString(", ")

@Composable
@ReadOnlyComposable
private fun AppearanceSettings.repeatSummary(): String =
    stringResource(
        when {
            repeatSpeed < AppearanceSettings.DEFAULT_REPEAT_SPEED -> R.string.settings_repeat_summary_slow
            repeatSpeed > AppearanceSettings.DEFAULT_REPEAT_SPEED -> R.string.settings_repeat_summary_fast
            else -> R.string.settings_repeat_summary_normal
        },
    )

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
                    stringResource(if (active) R.string.tv_status_current else R.string.tv_status_saved),
                    stringResource(R.string.tv_status_default).takeIf { tv.id == defaultId },
                    brandName(tv.adapterId),
                ).joinToString(" · "),
            brand = brandName(tv.adapterId),
            onClick = {},
            onAction = { viewModel.act(tv, it, onCurrentForgotten = onNoTvLeft) },
        )
    }
    NavItem(Icons.Rounded.Add, stringResource(R.string.add_a_tv), null, onClick = onAddTv)
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
    val tvName = device?.name ?: stringResource(R.string.settings_this_tv)

    NavItem(
        Icons.Rounded.Edit,
        stringResource(R.string.edit_layout),
        stringResource(R.string.settings_edit_layout_summary),
        onClick = onEditLayout,
    )
    NavItem(
        Icons.Rounded.RestartAlt,
        stringResource(R.string.layout_reset_to_default),
        stringResource(R.string.settings_reset_summary, tvName),
    ) {
        confirmReset = true
    }
    NavItem(
        Icons.Rounded.ContentCopy,
        stringResource(R.string.layout_copy_from_other),
        if (others.isEmpty()) {
            stringResource(
                R.string.settings_no_other_tv,
            )
        } else {
            stringResource(R.string.settings_replaces_layout_of, tvName)
        },
        enabled = others.isNotEmpty(),
    ) { pickSource = true }
    NavItem(Icons.Rounded.IosShare, stringResource(R.string.export_layout), stringResource(R.string.settings_export_summary)) {
        scope.launch { shareLayout(context, viewModel.currentLayout()) }
    }
    NavItem(
        Icons.Rounded.Download,
        stringResource(R.string.import_layout),
        stringResource(R.string.settings_import_summary, tvName),
        onClick = pickFile,
    )
    Text(
        stringResource(R.string.settings_import_placeholders_note),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )

    if (confirmReset) {
        ConfirmDialog(
            title = stringResource(R.string.settings_reset_title),
            body = stringResource(R.string.settings_reset_body, tvName),
            confirm = stringResource(R.string.action_reset),
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
        title = { Text(stringResource(R.string.copy_layout_from_title)) },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirm, color = MaterialTheme.colorScheme.error) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppearancePage(viewModel: SettingsViewModel) {
    val settings by viewModel.appearance.collectAsStateWithLifecycle()
    SectionHeader(stringResource(R.string.settings_theme))
    Segmented(ThemeChoice.entries, settings.theme, { it.title() }) { choice -> viewModel.updateAppearance { it.copy(theme = choice) } }
    SwitchItem(
        stringResource(R.string.settings_oled_black),
        stringResource(R.string.settings_oled_black_summary),
        settings.oledBlack,
    ) { on ->
        viewModel.updateAppearance { it.copy(oledBlack = on) }
    }

    SectionHeader(stringResource(R.string.settings_colour))
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
            stringResource(R.string.settings_dynamic_note),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
    }

    SectionHeader(stringResource(R.string.settings_key_style))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        KeyShapeChoice.entries.forEach { choice ->
            ShapeOption(choice, selected = choice == settings.keyShape) { viewModel.updateAppearance { it.copy(keyShape = choice) } }
        }
    }

    SectionHeader(stringResource(R.string.settings_preview))
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
        PreviewKey(stringResource(R.string.key_power), Icons.Rounded.PowerSettingsNew, KeyEmphasis.Power)
        PreviewKey(stringResource(R.string.key_home), Icons.Rounded.Home, KeyEmphasis.Standard)
        PreviewKey(stringResource(R.string.key_ok), null, KeyEmphasis.Primary)
        PreviewKey(stringResource(R.string.settings_preview_tv), Icons.Rounded.Tv, KeyEmphasis.Tertiary)
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
    val description = stringResource(R.string.settings_colour_summary, choice.title())
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
            .semantics { contentDescription = description },
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
        Text(choice.title(), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun FeedbackPage(viewModel: SettingsViewModel) {
    val settings by viewModel.appearance.collectAsStateWithLifecycle()
    SectionHeader(stringResource(R.string.settings_haptics))
    Segmented(
        HapticStrength.entries,
        settings.haptics,
        { it.title() },
    ) { choice -> viewModel.updateAppearance { it.copy(haptics = choice) } }
    SwitchItem(
        stringResource(R.string.settings_click_sound),
        stringResource(R.string.settings_click_sound_summary),
        settings.clickSound,
    ) { on ->
        viewModel.updateAppearance { it.copy(clickSound = on) }
    }
    Text(
        stringResource(R.string.settings_keys_always_show),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun ControlsPage(viewModel: SettingsViewModel) {
    val settings by viewModel.appearance.collectAsStateWithLifecycle()
    val volumeKeys by viewModel.volumeKeys.collectAsStateWithLifecycle()
    SwitchItem(
        stringResource(R.string.settings_volume_keys),
        stringResource(R.string.settings_volume_keys_summary),
        volumeKeys,
        viewModel::setVolumeKeys,
    )
    SectionHeader(stringResource(R.string.settings_repeat_speed_header))
    val repeatDescription = stringResource(R.string.settings_repeat_speed)
    Slider(
        value = settings.repeatSpeed.toFloat(),
        onValueChange = { value -> viewModel.updateAppearance { it.copy(repeatSpeed = Math.round(value)) } },
        valueRange = 1f..AppearanceSettings.REPEAT_INTERVALS_MS.size.toFloat(),
        steps = AppearanceSettings.REPEAT_INTERVALS_MS.size - 2,
        modifier = Modifier.padding(horizontal = 24.dp).semantics { contentDescription = repeatDescription },
    )
    Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.settings_slow), style = MaterialTheme.typography.labelMedium)
        Text(stringResource(R.string.settings_fast), style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
private fun AboutPage() {
    val context = LocalContext.current
    InfoItem(stringResource(R.string.about_version), versionName())
    InfoItem(stringResource(R.string.about_license), stringResource(R.string.about_license_body))
    NavItem(null, stringResource(R.string.about_source_code), SOURCE_URL) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)))
    }
    InfoItem(stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_body))
    InfoItem(stringResource(R.string.about_licences), stringResource(R.string.about_licences_body))
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
    label: @Composable (T) -> String,
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

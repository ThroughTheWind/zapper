package io.github.throughthewind.zapper.layout

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import io.github.throughthewind.zapper.core.model.LayoutFile
import io.github.throughthewind.zapper.core.model.RemoteLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException

/**
 * Exports a layout through the Android share sheet as a `.json` file (SPEC FR-12.1). The file is
 * written to the app's cache and shared read-only through a FileProvider; nothing leaves the phone
 * unless the user picks where to send it.
 */
fun shareLayout(
    context: Context,
    layout: RemoteLayout,
) {
    val dir = File(context.cacheDir, "layouts").apply { mkdirs() }
    dir.listFiles()?.forEach { it.delete() }
    val file = File(dir, fileName(layout.name))
    file.writeText(LayoutFile.encode(layout))
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send =
        Intent(Intent.ACTION_SEND)
            .setType(LayoutFile.MIME_TYPE)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "Zapper layout: ${layout.name}")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(file.name, uri)
    context.startActivity(Intent.createChooser(send, "Export layout"))
}

/** `Living room` → `zapper-living-room.json`. */
internal fun fileName(name: String): String {
    val slug =
        name
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifEmpty { "layout" }
    return "zapper-$slug.json"
}

/**
 * Remembers a launcher that lets the user pick a layout file and hands its text to [onText], or
 * calls [onError] when it cannot be read (SPEC FR-12.2).
 */
@Composable
fun rememberLayoutFilePicker(
    onText: (String) -> Unit,
    onError: (String) -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
            if (uri == null) return@rememberLauncherForActivityResult
            scope.launch {
                val text = withContext(Dispatchers.IO) { readText(context, uri) }
                if (text == null) onError("This file could not be read") else onText(text)
            }
        }
    // Many file managers label .json files as plain text or as unknown binary data.
    return { launcher.launch(arrayOf(LayoutFile.MIME_TYPE, "text/*", "application/octet-stream")) }
}

private fun readText(
    context: Context,
    uri: Uri,
): String? =
    try {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8 * 1024)
            while (out.size() <= MAX_FILE_BYTES) {
                val read = input.read(buffer)
                if (read < 0) break
                out.write(buffer, 0, read)
            }
            if (out.size() > MAX_FILE_BYTES) null else out.toByteArray().decodeToString()
        }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

/** A layout is a few kilobytes; anything near this is not one. */
private const val MAX_FILE_BYTES = 1_000_000

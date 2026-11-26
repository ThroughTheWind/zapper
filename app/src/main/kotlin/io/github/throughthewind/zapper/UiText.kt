package io.github.throughthewind.zapper

import android.content.res.Resources
import androidx.annotation.PluralsRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalContext

/**
 * Copy a ViewModel hands to the screen without resolving it, so it is read in the language the
 * screen shows rather than the one the ViewModel was created in. Resolve with [resolve] or
 * [asString].
 */
sealed interface UiText {
    fun resolve(resources: Resources): String

    data class Res(
        @param:StringRes val id: Int,
        val args: List<Any> = emptyList(),
    ) : UiText {
        override fun resolve(resources: Resources): String = resources.getString(id, *args.map { it.resolved(resources) }.toTypedArray())
    }

    data class Plural(
        @param:PluralsRes val id: Int,
        val count: Int,
        val args: List<Any> = listOf(count),
    ) : UiText {
        override fun resolve(resources: Resources): String =
            resources.getQuantityString(id, count, *args.map { it.resolved(resources) }.toTypedArray())
    }

    /** Text that is not the app's own copy, such as a TV's name. */
    data class Raw(
        val text: String,
    ) : UiText {
        override fun resolve(resources: Resources): String = text
    }

    /** Several pieces read one after the other, separated by a space. */
    data class Joined(
        val parts: List<UiText>,
    ) : UiText {
        override fun resolve(resources: Resources): String = parts.joinToString(" ") { it.resolve(resources) }
    }
}

/** A [UiText] argument is resolved too, so copy can nest. */
private fun Any.resolved(resources: Resources): Any = if (this is UiText) resolve(resources) else this

@Composable
@ReadOnlyComposable
fun UiText.asString(): String = resolve(LocalContext.current.resources)

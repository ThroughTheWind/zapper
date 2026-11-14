package io.github.throughthewind.zapper.core.model

/**
 * What edit mode can undo (SPEC FR-8.6): the states before each edit, newest last. Bounded so a
 * long session cannot grow it without end.
 */
data class EditHistory<T>(
    val current: T,
    private val past: List<T> = emptyList(),
    private val limit: Int = DEFAULT_LIMIT,
) {
    val canUndo: Boolean get() = past.isNotEmpty()

    /** Whether anything changed since the history started. */
    val changed: Boolean get() = past.isNotEmpty() && past.first() != current

    /** Records [next] as the current state; an edit that changes nothing is not recorded. */
    fun edit(next: T): EditHistory<T> = if (next == current) this else copy(current = next, past = (past + current).takeLast(limit))

    /** Goes back one edit, or stays put when there is nothing to undo. */
    fun undo(): EditHistory<T> = if (past.isEmpty()) this else copy(current = past.last(), past = past.dropLast(1))

    companion object {
        const val DEFAULT_LIMIT = 50
    }
}

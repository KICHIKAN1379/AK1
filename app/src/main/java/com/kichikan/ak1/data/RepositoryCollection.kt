package com.kichikan.ak1.data

/**
 * A read-only List query surface with explicit copy-on-write commands.
 *
 * The backing MutableList never escapes this adapter. Queries and iterators use snapshots;
 * a command edits a detached copy and commits only when the command returns successfully.
 */
internal class RepositoryCollection<T>(
    private val backing: MutableList<T>
) : AbstractList<T>() {
    override val size: Int
        get() = backing.size

    override fun get(index: Int): T = backing[index]

    override fun iterator(): Iterator<T> = snapshot().iterator()

    override fun listIterator(): ListIterator<T> = snapshot().listIterator()

    override fun listIterator(index: Int): ListIterator<T> = snapshot().listIterator(index)

    override fun subList(fromIndex: Int, toIndex: Int): List<T> =
        snapshot().subList(fromIndex, toIndex)

    fun snapshot(): List<T> = backing.toList()

    fun <R> update(command: MutableList<T>.() -> R): R {
        val working = backing.toMutableList()
        val result = working.command()
        backing.clear()
        backing.addAll(working)
        return result
    }

    fun add(element: T): Boolean = update { add(element) }

    fun add(index: Int, element: T) {
        update { add(index, element) }
    }

    fun addAll(elements: Collection<T>): Boolean =
        if (elements.isEmpty()) false else update { addAll(elements) }

    fun addAll(index: Int, elements: Collection<T>): Boolean =
        if (elements.isEmpty()) false else update { addAll(index, elements) }

    fun remove(element: T): Boolean = update { remove(element) }

    fun removeAll(predicate: (T) -> Boolean): Boolean = update { removeAll(predicate) }

    fun removeAll(elements: Collection<T>): Boolean = update { removeAll(elements.toSet()) }

    fun retainAll(elements: Collection<T>): Boolean = update { retainAll(elements.toSet()) }

    fun clear() {
        if (backing.isNotEmpty()) update { clear() }
    }

    fun removeAt(index: Int): T = update { removeAt(index) }

    operator fun set(index: Int, element: T) {
        update { this[index] = element }
    }

    operator fun plusAssign(element: T) {
        add(element)
    }

    operator fun plusAssign(elements: Iterable<T>) {
        addAll(elements.toList())
    }
}

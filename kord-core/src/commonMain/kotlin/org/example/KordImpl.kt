package org.example

/**
 * Rope Data Structure.
 */
internal class KordImpl internal constructor(
    private val root: KordNode? = null,
    private val randomProvider: () -> Int,
): Kord {
    override val length: Int
        get() = root.length

    private fun checkIndexing(at: Int, exclusive: Boolean = true) {
        val validEnd = if (exclusive) root.length else root.length - 1

        if (at !in 0..validEnd) {
            throw IndexOutOfBoundsException("Kord is ${root.length} bytes long, cannot index at $at")
        }
    }

    override fun get(index: Int): Char {
        checkIndexing(index)

        return root!!.get(index)
    }

    override fun isEmpty(): Boolean = root == null

    override val lines: Int get() = (root?.newlines ?: 0) + 1

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        TODO("Not yet implemented")
    }

    override fun insert(at: Int, contents: String): KordImpl {
        checkIndexing(at)

        if (contents.isEmpty()) return this

        val node = KordNode(contents, randomProvider())
        val (left, right) = root?.split(at) ?: return KordImpl(node, randomProvider)

        val merged = KordNode.merge(
            KordNode.merge(
                left,
                node,
            ),
            right,
        )

        return KordImpl(merged, randomProvider)
    }

    override fun chunks(charRange: IntRange): Iterable<KordChunk> {
        TODO("Not yet implemented")
    }

    override fun lines(lineRange: IntRange): Iterable<KordLine> {
        TODO("Not yet implemented")
    }

    override fun lineRange(lineIdx: Int): IntRange {
        TODO("Not yet implemented")
    }

    fun split(at: Int): Pair<KordImpl, KordImpl> {
        checkIndexing(at)

        val (lhs, rhs) = root?.split(at) ?: return Pair(this, this)

        return Pair(
            KordImpl(lhs, randomProvider),
            KordImpl(rhs, randomProvider),
        )
    }
}
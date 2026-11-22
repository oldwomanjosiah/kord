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

        return root!![index]
    }

    override fun isEmpty(): Boolean = root == null

    override val lines: Int get() = (root?.newlines ?: 0) + 1

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        TODO("Not yet implemented")
    }

    override fun insert(contents: String): Kord = insert(length, contents)

    override fun insert(at: Int, contents: String): KordImpl {
        checkIndexing(at, true)

        if (contents.isEmpty()) return this

        val node = KordNode(contents, randomProvider())

        val merged = when (at) {
            0 -> KordNode.merge(node, root)
            length -> KordNode.merge(root, node)
            else -> {
                val (left, right) = root?.split(at) ?: return KordImpl(node, randomProvider)

                KordNode.merge(
                    KordNode.merge(
                        left,
                        node,
                    ),
                    right,
                )
            }
        }

        return KordImpl(merged, randomProvider)
    }

    override fun replace(charRange: IntRange, contents: String): Kord {
        if (contents.isEmpty()) return remove(charRange)
        if (charRange.isEmpty()) return insert(charRange.first, contents)

        checkIndexing(charRange.first)
        checkIndexing(charRange.last)

        val node = KordNode(contents, randomProvider())

        val (left, middle) = root!!.split(charRange.first)
        val (_, right) = middle!!.split(charRange.last + 1 - charRange.first)

        return KordImpl(KordNode.merge(KordNode.merge(left, node), right), randomProvider)
    }

    override fun remove(charRange: IntRange): Kord {
        checkIndexing(charRange.first)
        checkIndexing(charRange.last)

        val (left, middle) = root!!.split(charRange.first)
        val (_, right) = middle!!.split(charRange.last + 1 - charRange.first)

        return KordImpl(KordNode.merge(left, right), randomProvider)
    }

    override fun chunks(charRange: IntRange): Iterable<KordChunk> {
        checkIndexing(charRange.first)
        checkIndexing(charRange.last)

        if (charRange.isEmpty()) return emptyList()

        return root!!.view(charRange)
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
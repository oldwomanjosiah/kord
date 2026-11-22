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

    /** Checks that [index] names a char: `0 until length`. */
    private fun checkIndex(index: Int) {
        if (index !in indices) {
            throw IndexOutOfBoundsException("Kord is $length chars long, cannot index at $index")
        }
    }

    /** Checks that [at] is a position between chars, where text can be inserted or cut: `0..length`. */
    private fun checkPosition(at: Int) {
        if (at !in 0..length) {
            throw IndexOutOfBoundsException("Kord is $length chars long, cannot cut at $at")
        }
    }

    /** Checks that [range] lies inside the kord. An empty range only needs a valid start position. */
    private fun checkRange(range: IntRange) {
        if (range.isEmpty()) {
            checkPosition(range.first)
        } else if (range.first < 0 || range.last >= length) {
            throw IndexOutOfBoundsException("Kord is $length chars long, cannot access $range")
        }
    }

    /**
     * Cuts the tree into the text before, inside and after a non-empty, in-bounds [range].
     *
     * The end is cut first, so that both cuts are positions in the original text. Cutting the
     * start first would shift the end by one whenever the start is nudged before a CR.
     */
    private fun cut(range: IntRange): Triple<KordNode?, KordNode?, KordNode?> {
        val (rest, right) = root!!.split(range.last + 1)
        val (left, middle) = rest?.split(range.first) ?: (null to null)

        return Triple(left, middle, right)
    }

    override fun get(index: Int): Char {
        checkIndex(index)

        return root!![index]
    }

    override fun isEmpty(): Boolean = root == null

    override val lines: Int get() = (root?.newlines ?: 0) + 1

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        TODO("Not yet implemented")
    }

    override fun insert(contents: String) = insert(length, contents)

    override fun insert(at: Int, contents: String): KordImpl {
        checkPosition(at)

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

    override fun replace(charRange: IntRange, contents: String): KordImpl {
        checkRange(charRange)

        if (contents.isEmpty()) return remove(charRange)
        if (charRange.isEmpty()) return insert(charRange.first, contents)

        val node = KordNode(contents, randomProvider())
        val (left, _, right) = cut(charRange)

        return KordImpl(KordNode.merge(KordNode.merge(left, node), right), randomProvider)
    }

    override fun remove(charRange: IntRange): KordImpl {
        checkRange(charRange)

        if (charRange.isEmpty()) return this

        val (left, _, right) = cut(charRange)

        return KordImpl(KordNode.merge(left, right), randomProvider)
    }

    override fun chunks(charRange: IntRange): Iterable<KordChunk> {
        checkRange(charRange)

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
        checkPosition(at)

        val (lhs, rhs) = root?.split(at) ?: return Pair(this, this)

        return Pair(
            KordImpl(lhs, randomProvider),
            KordImpl(rhs, randomProvider),
        )
    }

    override fun edit(
        charRange: IntRange,
        block: (Kord) -> Kord,
    ): KordImpl {
        val (left, region, right) = cut(charRange)

        val new = block(
            EditKord(
                offset = left?.length ?: 0,
                lineOffset = left?.newlines ?: 0,
                outerLength = length,
                delegate = KordImpl(region, randomProvider),
            )
        )

        val newRegionRoot = when (new) {
            is EditKord -> new.delegate.root
            is KordImpl -> new.root

            else -> error("edit's block must return a valid Kord")
        }

        return KordImpl(
            KordNode.merge(KordNode.merge(left, newRegionRoot), right),
            randomProvider,
        )
    }
}

package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.Kord
import com.oldwomanjosiah.kord.KordChunk
import com.oldwomanjosiah.kord.KordConfig
import com.oldwomanjosiah.kord.KordLine

/**
 * Rope Data Structure.
 */
internal class KordImpl internal constructor(
    private val root: KordNode? = null,
    private val config: KordConfig,
    private val randomProvider: () -> Int,
): Kord {
    override val length: Int
        get() = root.length

    /** Checks that [index] names a char: `0 until length`. */
    private fun checkIndex(index: Int) {
        if (index !in 0 until length) {
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

    private fun withRoot(root: KordNode?) = KordImpl(root, config, randomProvider)

    /**
     * Cuts the tree into the text before, inside and after an in-bounds [range].
     *
     * The end is cut first, so that both cuts are positions in the original text. Cutting the
     * start first would shift the end by one whenever the start is nudged before a CR.
     */
    private fun cut(range: IntRange): Triple<KordNode?, KordNode?, KordNode?> {
        val (rest, right) = root?.split(range.last + 1) ?: return Triple(null, null, null)
        val (left, middle) = rest?.split(range.first) ?: (null to null)

        return Triple(left, middle, right)
    }

    override fun get(index: Int): Char {
        checkIndex(index)

        return root!![index]
    }

    override fun isEmpty(): Boolean = root == null

    override val lines: Int get() = (root?.newlines ?: 0) + 1

    override fun charSequence(charRange: IntRange): CharSequence {
        checkRange(charRange)

        return KordCharSequence(this, if (charRange.isEmpty()) charRange.first until charRange.first else charRange)
    }

    override fun insert(contents: String) = insert(length, contents)

    override fun insert(at: Int, contents: String): KordImpl {
        checkPosition(at)

        if (contents.isEmpty()) return this

        val merged = when (at) {
            0 -> KordNode.merge(
                KordNode.merge(randomProvider, config, null, contents),
                root,
            )

            length -> KordNode.merge(randomProvider, config, root, contents)

            else -> {
                // Will have root bc adding at 0 (only legal place in empty list) was already covered
                val (left, right) = root!!.split(at)

                KordNode.merge(
                    KordNode.merge(
                        randomProvider,
                        config,
                        left,
                        contents,
                    ),
                    right,
                )
            }
        }

        return withRoot(merged)
    }

    override fun replace(charRange: IntRange, contents: String): KordImpl {
        checkRange(charRange)

        if (contents.isEmpty()) return remove(charRange)
        if (charRange.isEmpty()) return insert(charRange.first, contents)

        val (left, _, right) = cut(charRange)

        return withRoot(KordNode.merge(KordNode.merge(randomProvider, config, left, contents), right))
    }

    override fun remove(charRange: IntRange): KordImpl {
        checkRange(charRange)

        if (charRange.isEmpty()) return this

        val (left, _, right) = cut(charRange)

        return withRoot(KordNode.merge(left, right))
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
            withRoot(lhs),
            withRoot(rhs),
        )
    }

    override fun edit(
        charRange: IntRange,
        block: Kord.(offset: Int) -> Kord,
    ): KordImpl {
        checkRange(charRange)

        val (left, region, right) = cut(charRange)
        val new = withRoot(region).block(left.length)

        require(new is KordImpl) { "edit's block must return a Kord created by this library, got ${new::class}" }

        return withRoot(KordNode.merge(KordNode.merge(left, new.root), right))
    }
}

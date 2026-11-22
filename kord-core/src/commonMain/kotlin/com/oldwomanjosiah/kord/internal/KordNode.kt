package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.KordChunk

internal class KordNode(
    val contents: String,

    /** Random Priority for balancing */
    val prio: Int,
    val chunkNewlines: Int = contents.newlines,

    val left: KordNode? = null,
    val right: KordNode? = null,
): Iterable<KordChunkImpl> {
    init {
        // Runs before the property initializers below, which rely on non-empty contents.
        check(contents.isNotEmpty()) { "KordNode chunks must not be empty" }
    }

    /** Length of entire tree */
    val length = left.length + right.length + contents.length

    val firstIndex: Int = left?.length ?: 0
    val endIndexExclusive: Int = firstIndex + contents.length

    val firstChar: Char = left?.firstChar ?: contents.first()
    val lastChar: Char = right?.lastChar ?: contents.last()

    /** Count of newlines in entire tree */
    val newlines = calculateNewlines()

    fun calculateNewlines(): Int {
        var newlines = left.newlines + right.newlines + chunkNewlines

        // Correct for split newlines
        if (left?.lastChar == '\r' && contents.first() == '\n') {
            newlines -= 1
        }

        // Correct for split newlines
        if (right?.firstChar == '\n' && contents.last() == '\r') {
            newlines -= 1
        }

        return newlines
    }

    companion object {
        fun merge(left: KordNode?, right: KordNode?): KordNode? {
            return when {
                left == null -> right
                right == null -> left

                left.prio >= right.prio -> left.with(left.left, merge(left.right, right))
                else                    -> right.with(merge(left, right.left), right.right)
            }
        }
    }

    fun with(left: KordNode? = this.left, right: KordNode? = this.right) = KordNode(
        contents,
        prio,
        chunkNewlines,
        left,
        right,
    )

    fun split(at: Int): Pair<KordNode?, KordNode?> {
        return when {
            at == firstIndex -> when {
                left?.lastChar == '\r' && contents.first() == '\n' -> split(at - 1)
                else -> left to with(left = null)
            }

            at == endIndexExclusive -> when {
                right?.firstChar == '\n' && contents.last() == '\r' -> split(at - 1)
                else -> with(right = null) to right
            }

            at < firstIndex -> {
                // Split in left subtree
                val (ll, lr) = left?.split(at) ?: (null to null)

                ll to with(lr, right)
            }

            at > endIndexExclusive -> {
                // Split in right subtree
                val (rl, rr) = right?.split(at - endIndexExclusive) ?: (null to null)

                with(left, rl) to rr
            }

            else -> {
                // Split in this node
                var splitPoint = at - firstIndex

                // Nudge split point to before newline if user attempts to edit in the middle of one.
                if (contents[splitPoint] == '\n' && splitPoint >= 1 && contents[splitPoint - 1] == '\r') {
                    splitPoint -= 1
                }

                val cl = contents.substring(0, splitPoint)
                val cr = contents.substring(splitPoint)

                when {
                    cl.isEmpty() -> left to with(left = null)

                    else -> KordNode(
                        contents = cl,
                        prio = prio,
                        left = left,
                        right = null,
                    ) to KordNode(
                        contents = cr,
                        prio = prio,
                        left = null,
                        right = right,
                    )
                }
            }
        }
    }

    operator fun get(at: Int): Char = if (at < firstIndex) {
        left!![at]
    } else if (at >= endIndexExclusive) {
        right!![at - endIndexExclusive]
    } else {
        contents[at - firstIndex]
    }

    fun view(viewRange: IntRange, offset: Int = 0): KordNodeView = if (viewRange.last < firstIndex) {
        left!!.view(viewRange, offset)
    } else if (viewRange.first >= endIndexExclusive) {
        right!!.view(viewRange.first - endIndexExclusive..viewRange.last - endIndexExclusive, offset + endIndexExclusive)
    } else {
        KordNodeView(this, offset, viewRange)
    }

    fun asChunk(
        currentOffset: Int,
        startIdx: Int = 0,
        endIdx: Int = contents.length,
    ) = KordChunkImpl(
        this,
        currentOffset,
        startIdx,
        endIdx,
    )

    /** Calls [block] on this node, then its left child, then that child's left child, and so on. */
    inline fun forEachLeftSpine(block: (KordNode) -> Unit) {
        var current: KordNode? = this

        while (current != null) {
            block(current)
            current = current.left
        }
    }

    override fun iterator(): Iterator<KordChunkImpl> {
        class Iter(
            val stack: MutableList<KordNode>,
            var currentOffset: Int = 0,
        ): Iterator<KordChunkImpl> {
            override fun hasNext(): Boolean {
                return stack.isNotEmpty()
            }

            override fun next(): KordChunkImpl {
                val item = stack.removeLastOrNull() ?: throw NoSuchElementException("Iter Exhausted")

                item.right?.forEachLeftSpine { stack.add(it) }
                val offset = currentOffset

                currentOffset += item.contents.length

                return item.asChunk(offset)
            }
        }

        val stack = mutableListOf<KordNode>()
        forEachLeftSpine { stack.add(it) }

        return Iter(stack)
    }
}

internal data class KordChunkImpl(
    val node: KordNode,
    val nodeCharOffset: Int,
    val startIdx: Int,
    val endIdx: Int,
) : KordChunk {
    override val contents: String
        get() = node.contents.substring(startIdx, endIdx)

    override val contentsRange: IntRange
        get() = startIdx + nodeCharOffset until endIdx + nodeCharOffset
}

/**
 * A view over [viewRangeInNode] of the subtree rooted at [node].
 *
 * @param nodeCharOffset absolute char offset of the start of [node]'s subtree.
 * @param viewRangeInNode range of chars to view, relative to the start of [node]'s subtree.
 */
internal class KordNodeView(
    val node: KordNode,
    val nodeCharOffset: Int,
    val viewRangeInNode: IntRange,
): Iterable<KordChunkImpl> {
    override fun iterator(): Iterator<KordChunkImpl> = iterator {
        val viewStart = viewRangeInNode.first
        val viewEndExclusive = viewRangeInNode.last + 1

        if (viewStart >= viewEndExclusive) return@iterator

        // Chunks from node.iterator() have nodeCharOffset relative to the start of node's subtree.
        for (chunk in node) {
            val chunkStart = chunk.nodeCharOffset + chunk.startIdx
            val chunkEndExclusive = chunk.nodeCharOffset + chunk.endIdx

            // Before the view; keep going
            if (chunkEndExclusive <= viewStart) continue

            // Past the view; done
            if (chunkStart >= viewEndExclusive) break

            yield(
                chunk.copy(
                    nodeCharOffset = chunk.nodeCharOffset + nodeCharOffset,
                    startIdx = maxOf(chunk.startIdx, viewStart - chunk.nodeCharOffset),
                    endIdx = minOf(chunk.endIdx, viewEndExclusive - chunk.nodeCharOffset),
                )
            )

            if (chunkEndExclusive >= viewEndExclusive) break
        }
    }
}

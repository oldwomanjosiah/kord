package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.KordChunk
import com.oldwomanjosiah.kord.KordConfig

internal class KordNode(
    val contents: NodeBuffer.View,

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

        fun createBulk(
            randomProvider: () -> Int,
            vararg contents: NodeBuffer.View,
        ): KordNode? {
            if (contents.isEmpty()) return null

            val rightSpineStack = mutableListOf<KordNode>()
            val currentNode = { rightSpineStack.lastOrNull() }

            for (contents in contents) {
                val prio = randomProvider()
                var leftChild: KordNode? = null

                while (true) {
                    val currentNode = currentNode()?.takeIf { it.prio < prio } ?: break
                    rightSpineStack.removeLast()

                    leftChild = currentNode.with(right = leftChild)
                }

                rightSpineStack.add(KordNode(contents, prio, left = leftChild))
            }

            var result = rightSpineStack.removeLast()

            while (true) {
                val next = rightSpineStack.removeLastOrNull() ?: break
                result = next.with(right = result)
            }

            return result
        }

        /**
         * Add the entirety of `contents` to the end of `left`. If only part of `contents` may be admitted,
         * `adjustContents` will be called to allow for scanning forwards to find the next clean breakpoint.
         *
         * - `-1` may be returned if there is no clean breakpoint.
         *     - When combining into an existing node, this will cancel the combination.
         *     - When creating a new node, this will force the creation of the largest possible node.
         * - No adjustment will be offered for the final segment of contents, it will just be put into a node.
         * - The returned value should be the length of the current segment, not an absolute offset
         */
        fun addAtEnd(
            randomProvider: () -> Int,
            config: KordConfig,

            left: KordNode?,
            contents: String,
            adjustContents: (currentOffset: Int, capacity: Int) -> Int,
        ): KordNode? {
            val comboStack = mutableListOf<KordNode>()
            if (left != null) comboStack.add(left)

            while (true) comboStack.add(
                comboStack.lastOrNull()?.right ?: break
            )

            val comboSegment = comboStack.lastOrNull()
            var currentOffset = 0

            var out = if (comboSegment != null) {
                val lengthToAdd = when {
                    comboSegment.contents.remainingCapacity >= contents.length -> contents.length
                    else -> minOf(
                        adjustContents(0, comboSegment.contents.remainingCapacity),
                        comboSegment.contents.remainingCapacity,
                    )
                }

                // TODO should I double check for newline-straddling here?
                if (lengthToAdd <= 0) {
                    left
                } else {
                    val newView = comboSegment.contents.tryGrow(contents, 0, lengthToAdd)!!

                    currentOffset += lengthToAdd

                    comboStack.removeLast()

                    comboStack.asReversed().fold(comboSegment.with(newView)) { right, old ->
                        old.with(right = right)
                    }
                }
            } else left

            while (currentOffset < contents.length) {
                val remainingLength = contents.length - currentOffset

                var nextNodeLength = when {
                    remainingLength <= config.maxChunkSize -> remainingLength

                    else -> minOf(
                        adjustContents(currentOffset, config.maxChunkSize),
                        config.maxChunkSize,
                    )
                }

                if (nextNodeLength <= 0) {
                    nextNodeLength = minOf(remainingLength, config.maxChunkSize)
                }

                val nextNode = KordNode(
                    NodeBuffer(config, contents, currentOffset, currentOffset + nextNodeLength).view(),
                    randomProvider()
                )

                out = merge(out, nextNode)
                currentOffset += nextNodeLength
            }

            return out
        }
    }

    fun with(left: KordNode? = this.left, right: KordNode? = this.right) = KordNode(
        contents,
        prio,
        chunkNewlines,
        left,
        right,
    )

    fun with(contents: NodeBuffer.View) = KordNode(
        contents,
        prio,
        left = left,
        right = right,
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

                val cl = contents.subSequence(0, splitPoint)
                val cr = contents.subSequence(splitPoint, contents.length)

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
    override val contents: CharSequence
        get() = node.contents.subSequence(startIdx, endIdx)

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

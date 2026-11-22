package org.example

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
        left!!.view(viewRange)
    } else if (viewRange.first >= endIndexExclusive) {
        right!!.view(viewRange.first - endIndexExclusive..viewRange.last - endIndexExclusive, endIndexExclusive)
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

    inline fun <R> foldLeft(
        acc: R,
        block: (R, KordNode) -> R): R {
        var current: KordNode? = this
        var acc = acc

        while (current != null) {
            acc = block(acc, current)
            current = current.left
        }

        return acc
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
                val item = stack.removeLastOrNull() ?: throw IllegalStateException("Iter Exhausted")

                item.right?.foldLeft(Unit) { _, it -> stack.add(it) }
                val offset = currentOffset

                currentOffset += item.contents.length

                return item.asChunk(offset)
            }
        }

        val list = foldLeft(mutableListOf<KordNode>()) { acc, it ->
            acc.add(it)
            acc
        }

        return Iter(
            list
        )
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

internal class KordNodeView(
    val node: KordNode,
    val nodeCharOffset: Int,
    val viewRangeInNode: IntRange,
): Iterable<KordChunkImpl> {
    override fun iterator(): Iterator<KordChunkImpl> {
        val firstChar = viewRangeInNode.first
        val lastCharExclusive = viewRangeInNode.last + 1

        class Iter(
            var delegate: Iterator<KordChunkImpl>?,
            var tmp: KordChunkImpl?
        ): Iterator<KordChunkImpl> {
            override fun hasNext(): Boolean {
                if (tmp != null) return true

                val delegate = delegate ?: return false

                if (!delegate.hasNext()) return false
                var next = delegate.next()

                while (delegate.hasNext() && next.endIdx <= firstChar) {
                    next = delegate.next()
                }

                return if (next.endIdx <= firstChar) {
                    // we are past the end (endIdx is exclusive)

                    this.delegate = null

                    false
                } else if (next.startIdx < firstChar) {
                    // we are looking at the first item, and possibly the last

                    tmp = next.copy(
                        startIdx = firstChar - next.nodeCharOffset,
                        endIdx = minOf(next.endIdx, lastCharExclusive - next.nodeCharOffset)
                    )

                    if (tmp?.endIdx != next.endIdx) this.delegate = null

                    true
                } else if (next.endIdx > lastCharExclusive) {
                    // We are looking at the last item

                    tmp = next.copy(
                        // Not possible for the start to be different, since that would have been caught by the first run
                        endIdx = lastCharExclusive - next.nodeCharOffset,
                    )

                    this.delegate = null

                    true
                } else {
                    // We are looking at some item in the middle
                    tmp = next

                    true
                }
            }

            override fun next(): KordChunkImpl {
                val res = tmp
                tmp = null
                check(res != null) { "Iter Exhausted" }
                return res
            }
        }

        return Iter(node.iterator(), null)
    }
}

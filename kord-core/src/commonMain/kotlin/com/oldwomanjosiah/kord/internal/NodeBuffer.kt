package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.KordConfig
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class NodeBuffer private constructor(
    internal val configuration: KordConfig,
    internal val buffer: CharArray,
    internal val _size: AtomicInt,
) {
    class View(
        internal val buffer: NodeBuffer,
        internal val startIndex: Int,
        internal val endIndexExclusive: Int
    ): CharSequence {
        override val length: Int = endIndexExclusive - startIndex

        val remainingCapacity: Int get() = buffer.configuration.maxChunkSize - length

        override operator fun get(index: Int): Char {
            if (index !in indices)
                throw IndexOutOfBoundsException("Cannot get char at $index, NodeBuffer.View is length $length")

            return buffer[index + startIndex]
        }

        override fun toString(): String {
            return buffer.toString(startIndex, endIndexExclusive)
        }

        override fun subSequence(startIndex: Int, endIndex: Int): View {
            if (startIndex < 0 || endIndex > length || startIndex > endIndex)
                throw IndexOutOfBoundsException("Cannot get subSequence [$startIndex, $endIndex), NodeBuffer.View is length $length")

            return View(
                buffer,
                startIndex + this.startIndex,
                endIndex + this.startIndex,
            )
        }

        /**
         * Attempt to create a new view with the additional [contents] added to the end of this one.
         *
         * If possible, this re-uses the underlying buffer.
         */
        fun tryGrow(contents: String, startIndex: Int = 0, endIndexExclusive: Int = contents.length): View? {
            if (startIndex !in 0..endIndexExclusive || endIndexExclusive > contents.length)
                throw IndexOutOfBoundsException("Cannot grow from slice [$startIndex, $endIndexExclusive) of string with length ${contents.length}")

            val subsectionLength = endIndexExclusive - startIndex

            val newLength = length + subsectionLength
            if (newLength > buffer.configuration.maxChunkSize) return null

            return if (buffer.tryGrow(this.endIndexExclusive, contents, startIndex, endIndexExclusive)) {
                View(buffer, this.startIndex, this.endIndexExclusive + subsectionLength)
            } else {
                val newBuffer = buffer.copy(newLength)

                check(newBuffer.tryGrow(0, this)) { "Buffer was pre-allocated with space" }
                check(newBuffer.tryGrow(length, contents, startIndex, endIndexExclusive)) { "Buffer was pre-allocated with space" }

                View(newBuffer, 0, length + subsectionLength)
            }
        }

        override fun equals(other: Any?): Boolean {
            val other = other as? View ?: return false

            if (other === this) return true
            if (other.length != length) return false
            if (other.buffer === buffer && other.startIndex == startIndex) return true

            // ContentEquals for applicable regions
            for (i in indices) {
                if (buffer.buffer[i + startIndex] != other.buffer.buffer[i + other.startIndex]) return false
            }

            return true
        }

        override fun hashCode(): Int = hashInto(0)

        /**
         * Continues a `31 * hash + char` hash from [seed] over this view's chars, so a whole kord
         * can be hashed chunk by chunk without copying.
         */
        internal fun hashInto(seed: Int): Int {
            var hash = seed

            for (i in startIndex until endIndexExclusive) {
                hash = 31 * hash + buffer.buffer[i].code
            }

            return hash
        }
    }

    companion object {
        fun KordConfig.bufferForSize(size: Int) = CharArray(
            minOf(
                maxOf(
                    initialChunkSize,
                    size.nextPowerOf2,
                ),
                maxChunkSize,
            )
        )
    }

    constructor(configuration: KordConfig, initialContent: String) : this(configuration, initialContent.length) {
        _size.store(initialContent.length)
        initialContent.toCharArray(buffer)
    }

    constructor(configuration: KordConfig, initialContent: String, startIndex: Int, endIndexExclusive: Int) : this(configuration, endIndexExclusive - startIndex) {
        val copySize = endIndexExclusive - startIndex

        if (startIndex !in 0..endIndexExclusive || endIndexExclusive > initialContent.length)
            throw IndexOutOfBoundsException("Cannot copy [$startIndex, $endIndexExclusive) from String with length ${initialContent.length}")

        require(copySize <= buffer.size) { "Span [$startIndex, $endIndexExclusive) ($copySize chars) is too big to fit within configured maximum of ${configuration.maxChunkSize}" }

        _size.store(copySize)
        initialContent.toCharArray(buffer, 0, startIndex, endIndexExclusive)
    }

    constructor(configuration: KordConfig, initialCapacity: Int) : this(
        configuration,
        configuration.bufferForSize(initialCapacity),
        AtomicInt(0)
    ) {
        require(initialCapacity <= buffer.size) { "Contents too large to fit into NodeBuffer" }
    }

    val size: Int get() = _size.load()

    fun toString(
        startIndex: Int,
        endIndex: Int,
    ) = buffer.concatToString(startIndex, endIndex)

    fun copy(contents: String): NodeBuffer = NodeBuffer(configuration, contents)
    fun copy(newCapacity: Int): NodeBuffer = NodeBuffer(configuration, newCapacity)

    operator fun get(index: Int): Char = buffer[index]

    operator fun get(indices: IntRange) = View(this, indices.first, indices.last + 1)

    fun view() = get(0..<size)

    private fun tryGrow(end: Int, other: View): Boolean {
        if (end != size) return false
        val newLength = end + other.length
        if (buffer.size < newLength) return false

        val alloced = _size.compareAndExchange(end, newLength) == end

        if (alloced) other.buffer.buffer.copyInto(
            destination = buffer,
            destinationOffset = end,
            startIndex = other.startIndex,
            endIndex = other.endIndexExclusive,
        )

        return alloced
    }

    private fun tryGrow(end: Int, contents: String, startIndex: Int = 0, endIndexExclusive: Int = contents.length): Boolean {
        val copyLength = endIndexExclusive - startIndex

        if (startIndex < 0 || copyLength > contents.length)
            throw IndexOutOfBoundsException("Cannot copy from slice [$startIndex, $endIndexExclusive) of string with length ${contents.length}")

        if (end != size) return false
        val newLength = end + copyLength
        if (buffer.size < newLength) return false

        val alloced = _size.compareAndExchange(end, newLength) == end

        if (alloced) contents.toCharArray(buffer, end, startIndex, endIndexExclusive)

        return alloced
    }
}
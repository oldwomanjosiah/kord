package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.internal.length
import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class NodeBuffer private constructor(
    private val configuration: KordConfig,
    private val buffer: CharArray,
    private val _size: AtomicInt,
) {
    class View(
        internal val buffer: NodeBuffer,
        internal val startIndex: Int,
        internal val endIndexExclusive: Int
    ): CharSequence {
        override val length: Int = endIndexExclusive - startIndex

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
        fun tryGrow(contents: String): View? {
            val newLength = length + contents.length
            if (newLength > buffer.configuration.maxChunkSize) return null

            return if (buffer.tryGrow(endIndexExclusive, contents)) {
                View(buffer, startIndex, endIndexExclusive + contents.length)
            } else {
                val newBuffer = buffer.copy(newLength + 1)

                check(newBuffer.tryGrow(0, this)) { "Buffer was pre-allocated with space" }
                check(newBuffer.tryGrow(length, contents)) { "Buffer was pre-allocated with space" }

                View(newBuffer, 0, length + contents.length)
            }
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

    private fun tryGrow(end: Int, contents: String): Boolean {
        if (end != size) return false
        val newLength = end + contents.length
        if (buffer.size < newLength) return false

        val alloced = _size.compareAndExchange(end, newLength) == end

        if (alloced) contents.toCharArray(buffer, end)

        return alloced
    }
}
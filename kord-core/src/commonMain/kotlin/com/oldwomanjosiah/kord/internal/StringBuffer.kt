package com.oldwomanjosiah.kord.internal

import kotlin.concurrent.atomics.AtomicInt
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Internal only storage mechanism which supports growing in place when
 * attempting to add contents that fit into the existing backing buffer.
 *
 * This does not discard the backing buffer when sub-slicing.
 */
@ConsistentCopyVisibility
internal data class StringBufferSlice internal constructor(
    private val buffer: StringBuffer,
    private val startOffset: Int = 0,
    private val endOffsetExclusive: Int = buffer.length,
): CharSequence {
    override val length: Int = endOffsetExclusive - startOffset

    override fun get(index: Int): Char {
        if(index >= length) throw IndexOutOfBoundsException("Cannot get char at $index, StringBufferSlice has length $length")

        return buffer[index - startOffset]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): StringBufferSlice {
        if (startIndex >= length || endIndex > length || startIndex > endIndex)
            throw IndexOutOfBoundsException("Cannot slice to [$startIndex, $endIndex), StringBufferSlice has length $length")

        return StringBufferSlice(buffer, startOffset + startIndex, startOffset + endIndex)
    }

    override fun toString(): String = buffer.toString(startOffset, endOffsetExclusive)

    /**
     * Try to insert content into this slice, potentially splitting it into up to three segments.
     *
     * - Will return one segment if contents fits at end
     * - Will return two segments if contents does not fit, or attempting to insert at beginning of slice
     * - Will return three segments if attempting to insert in middle of slice
     */
    fun tryInsert(at: Int, contents: String): Triple<StringBufferSlice, StringBufferSlice?, StringBufferSlice?> {
        return when (at) {
            0 -> Triple(StringBufferSlice(buffer.newCopyWith(contents)), this, null)

            length -> {
                when {
                    buffer.tryGrow(at + startOffset, contents) -> Triple(
                        StringBufferSlice(buffer, startOffset, endOffsetExclusive + contents.length),
                        null,
                        null,
                    )

                    else -> Triple(
                        this,
                        StringBufferSlice(buffer.newCopyWith(contents)),
                        null,
                    )
                }
            }

            else -> Triple(
                subSequence(0, at),
                StringBufferSlice(buffer.newCopyWith(contents)),
                subSequence(at, length),
            )
        }
    }
}

/**
 * Internal only storage mechanism which supports growing in place when
 * attempting to add contents that fit into the existing backing buffer.
 */
@OptIn(ExperimentalAtomicApi::class)
class StringBuffer(
    bufferSize: Int,
    initContent: String,
) {
    internal val backingBuffer = CharArray(bufferSize)
    private val _length = AtomicInt(initContent.length)

    internal val length get() = _length.load()

    init {
        require(bufferSize >= initContent.length) { "Cannot create a StiringBuffer smaller than its init content" }

        initContent.toCharArray(backingBuffer)
    }

    operator fun get(index: Int): Char = backingBuffer[index]

    fun toString(
        startIndex: Int,
        endIndexExclusive: Int,
    ): String = backingBuffer.concatToString(startIndex, endIndexExclusive)

    fun newCopyWith(contents: String): StringBuffer = StringBuffer(backingBuffer.size, contents)

    /**
     * Try to grow in-place, if there is space available. Otherwise
     */
    fun tryGrow(at: Int, contents: String): Boolean {
        val growLength = contents.length
        val newLength = at + growLength

        if (newLength > backingBuffer.size) {
            return false
        }

        // If the current length matches the expected value, then we have reserved more space at the end
        val didGrow = _length.compareAndExchange(at, newLength) == at

        if (didGrow) contents.toCharArray(
            backingBuffer,
            at,
        )

        return didGrow
    }
}
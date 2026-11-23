package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.internal.NodeBuffer.View

internal val Char.isNewline: Boolean inline get() = when (this) {
    '\n', '\r', '\u000B', '\u000C', '\u0085', '\u2028', '\u2029' -> true
    else -> false
}

internal val CharSequence.newlines: Int get() = newlines()

internal fun CharSequence.newlines(start: Int = 0, end: Int = length): Int {
    if (start !in 0..length || end > length)
        throw IndexOutOfBoundsException("Cannot count newlines in span [$start, $end) of CharSequence with length $length")

    var idx = start
    var count = 0

    while (idx < end) {
        val curr = get(idx)
        val next = getOrNull(idx + 1)

        idx += when {
            curr == '\r' && next == '\n' -> {
                count += 1

                2
            }

            curr.isNewline -> {
                count += 1

                1
            }

            else -> 1
        }
    }

    return count
}

internal val KordNode?.length: Int inline get() = this?.length ?: 0
internal val KordNode?.newlines: Int inline get() = this?.newlines ?: 0

internal val Int.nextPowerOf2: Int
    inline get() {
        val nearest = takeHighestOneBit()

        return when {
            nearest < this -> nearest.shl(1)
            else -> nearest
        }
    }

/**
 * True when char [index] of this view and char [otherIndex] of [other] are the same slot
 * of the same buffer. The chars from there on are then identical for as long as both
 * views last, with no need to compare them.
 */
internal fun NodeBuffer.View.sharesStorageAt(index: Int, other: View, otherIndex: Int): Boolean =
    buffer === other.buffer && startIndex + index == other.startIndex + otherIndex

/** Compares [count] chars from [index] in this view with [count] chars from [otherIndex] in [other]. */
internal fun NodeBuffer.View.regionMatches(index: Int, other: View, otherIndex: Int, count: Int): Boolean {
    val a = buffer.buffer
    val b = other.buffer.buffer
    val aStart = startIndex + index
    val bStart = other.startIndex + otherIndex

    for (k in 0 until count) {
        if (a[aStart + k] != b[bStart + k]) return false
    }

    return true
}

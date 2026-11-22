package com.oldwomanjosiah.kord.internal

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
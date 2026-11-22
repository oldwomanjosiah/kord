package com.oldwomanjosiah.kord

/** A [CharSequence] over [range] of [kord], indexed from the start of the range. */
internal class KordCharSequence(
    private val kord: Kord,
    private val range: IntRange,
) : CharSequence {
    override val length: Int get() = range.last - range.first + 1

    override fun get(index: Int): Char {
        if (index !in indices) {
            throw IndexOutOfBoundsException("CharSequence is $length chars long, cannot index at $index")
        }

        return kord[range.first + index]
    }

    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence {
        if (startIndex < 0 || endIndex > length || startIndex > endIndex) {
            throw IndexOutOfBoundsException("CharSequence is $length chars long, cannot take $startIndex until $endIndex")
        }

        return KordCharSequence(kord, range.first + startIndex until range.first + endIndex)
    }

    override fun toString(): String = kord.chunks(range).joinToString("") { it.contents }
}

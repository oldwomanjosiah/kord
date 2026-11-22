package com.oldwomanjosiah.kord

interface Kord {
    val length: Int

    operator fun get(index: Int): Char

    val lines: Int

    fun isEmpty(): Boolean

    fun replace(charRange: IntRange, contents: String): Kord

    fun insert(at: Int, contents: String): Kord

    fun insert(contents: String): Kord

    fun remove(charRange: IntRange): Kord

    fun lineRange(lineIdx: Int): IntRange

    fun lines(lineRange: IntRange = 0 until lines): Iterable<KordLine>
    fun chunks(charRange: IntRange = 0 until length): Iterable<KordChunk>

    /**
     * A read-only [CharSequence] over [charRange], indexed from the start of the range.
     *
     * It reads from this kord, which is immutable, so later edits never show through it.
     */
    fun charSequence(charRange: IntRange = 0 until length): CharSequence

    /**
     * Perform multiple edits within [charRange] without creating intermediate `Kord`s for the whole structure.
     *
     * [block] runs on a kord holding only the region, indexed from 0, and whatever it returns replaces the
     * region. Its `offset` argument is where the region starts in this kord, so a position `at` in this kord
     * is `at - offset` inside the block. The region may start one char before [charRange] if
     * [charRange] starts between a CR and an LF, since a CRLF pair is never split; `offset` accounts for that.
     *
     * [block] must return a kord created by this library.
     */
    fun edit(
        charRange: IntRange,
        block: Kord.(offset: Int) -> Kord,
    ): Kord
}
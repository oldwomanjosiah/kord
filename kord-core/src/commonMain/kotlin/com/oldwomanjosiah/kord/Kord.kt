package com.oldwomanjosiah.kord

interface Kord {
    /**
     * Total char length of this `Kord`
     */
    val length: Int

    /**
     * Is the kord empty?
     */
    fun isEmpty(): Boolean

    /**
     * Total count of lines in this `Kord`.
     */
    val lines: Int

    /**
     * Get the `Char` at `index`.
     */
    operator fun get(index: Int): Char

    /**
     * Remove and contents at `charRange` and replace it with `contents`.
     *
     * ```
     * val kord = kordOf("One", "Two", "Three")
     *
     * assertEq(
     *     // Chained call removes "Two" and inserts "One" in its place
     *     kord.remove(3..<6).insert(3, "One"),
     *
     *     // Single call to do both operations at once
     *     kord.replace(3..<6, "One"),
     * )
     * ```
     *
     * By combining the [remove] and [insert] operations with this function
     * is more efficient than calling them in sequence.
     */
    fun replace(charRange: IntRange, contents: String): Kord

    /**
     * Insert `contents` at `at`.
     */
    fun insert(at: Int, contents: String): Kord

    /**
     * Insert `contents` at the end of this `Kord`.
     */
    fun insert(contents: String): Kord

    /**
     * Remove the chars within `charRange`.
     */
    fun remove(charRange: IntRange): Kord

    /**
     * Get the char range for the line with `lineIdx`. This is zero based.
     */
    fun lineRange(lineIdx: Int): IntRange

    /**
     * Iterate over full lines contained in this buffer.
     */
    fun lines(lineRange: IntRange = 0 until lines): Iterable<KordLine>

    /**
     * Iterator over the current stored chunks. These directly correspond to the internal structure,
     * but provide no guarantees about that structure other than that it is iterable here.
     *
     * ```
     * val kord = kordOf(
     *     "Chunk One\n",
     *     "Chunk Two\n",
     *     "Chunk Three\n",
     *     "Chunk Four\n",
     * )
     *
     * val fullString = kord.chunks()
     *    .joinTo(StringBuffer(kord.length)) { it.contents }
     * ```
     */
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
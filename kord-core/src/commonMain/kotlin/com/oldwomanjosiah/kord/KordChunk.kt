package com.oldwomanjosiah.kord

/** One stored chunk of a [Kord], or the part of one that falls inside the requested range. */
interface KordChunk {
    /**
     * The chunk's text, read directly from the kord's storage without copying.
     *
     * This is not a `String`, so `contents == "text"` is always false. Compare with
     * [contentEquals], or call [toString] to get a copy.
     */
    val contents: CharSequence

    /** Where [contents] sits in the kord, as absolute char indices. */
    val contentsRange: IntRange
}

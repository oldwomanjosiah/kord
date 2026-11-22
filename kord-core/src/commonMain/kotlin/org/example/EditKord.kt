package org.example

internal class EditKord(
    val offset: Int,
    val lineOffset: Int,
    val outerLength: Int,
    val delegate: KordImpl,
): Kord {
    override val lines: Int
        get() = delegate.lines

    override fun isEmpty(): Boolean {
        return delegate.isEmpty()
    }

    val Int.fix get() = this - offset
    val Int.unFix get() = this + offset

    val Int.lineFix get() = this - lineOffset
    val Int.lineUnFix get() = this + lineOffset

    val IntRange.fix get() = IntRange(start.fix, endInclusive.fix)
    val IntRange.unFix get() = IntRange(start.unFix, endInclusive.unFix)

    val IntRange.lineFix get() = IntRange(start.lineFix, endInclusive.lineFix)

    val KordLine.unFix get() = object : KordLine {
        val delegate = this@unFix

        override val lineIdx: Int get() = delegate.lineIdx.lineUnFix
        override val contents: String get() = delegate.contents
        override val contentsRange: IntRange get() = delegate.contentsRange.unFix
    }

    val KordChunk.unFix get() = object : KordChunk {
        val delegate = this@unFix

        override val contents: String get() = delegate.contents
        override val contentsRange: IntRange get() = delegate.contentsRange.unFix
    }

    fun mapped(block: KordImpl.() -> KordImpl) = EditKord(offset, lineOffset, outerLength, block(delegate))

    override fun replace(charRange: IntRange, contents: String): Kord = mapped { replace(charRange.fix, contents) }
    override fun insert(at: Int, contents: String): Kord = mapped { insert(at.fix, contents) }
    override fun insert(contents: String): Kord = mapped { insert(contents) }
    override fun remove(charRange: IntRange): Kord = mapped { remove(charRange.fix) }
    override fun lineRange(lineIdx: Int): IntRange = lineRange(lineIdx.lineFix).unFix
    override fun lines(lineRange: IntRange): Iterable<KordLine> = delegate.lines(lineRange.lineFix).asSequence().map { it.unFix }.asIterable()
    override fun chunks(charRange: IntRange): Iterable<KordChunk> = delegate.chunks(charRange.fix).asSequence().map { it.unFix }.asIterable()
    override fun edit(charRange: IntRange, block: (Kord) -> Kord): Kord = mapped { edit(charRange.fix, block) }
    override val length: Int get() = outerLength + delegate.length
    override fun get(index: Int): Char = delegate[index.fix]
    override fun subSequence(startIndex: Int, endIndex: Int): CharSequence = delegate.subSequence(startIndex.fix, endIndex.fix)
}
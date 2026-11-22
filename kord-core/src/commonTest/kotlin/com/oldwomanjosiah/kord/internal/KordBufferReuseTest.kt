package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.Kord
import com.oldwomanjosiah.kord.KordConfig
import com.oldwomanjosiah.kord.kordOf
import io.kotest.assertions.withClue
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test

/**
 * Chunking of long text and in-place growth of node buffers.
 *
 * A tiny [KordConfig] makes small inputs span many chunks, so these paths run on every case.
 */
class KordBufferReuseTest {
    private val tiny = KordConfig(maxChunkSize = 8, initialChunkSize = 2)

    private fun tinyKord(vararg parts: String, rng: Random = Random(0)): Kord =
        kordOf(*parts, config = tiny, randomProvider = rng::nextInt)

    private fun Kord.assertChunksBounded() {
        chunks().forEach { chunk ->
            withClue("chunk ${chunk.contents.toString().debug()} at ${chunk.contentsRange}") {
                chunk.contents.length shouldBeLessThanOrEqual tiny.maxChunkSize
            }
        }
    }

    private val longPlain = chunkOf(plainChars, 0..60)
    private val longCrlf = chunkOf(crlfChars, 0..60)

    @Test
    fun `text longer than a chunk is split into bounded chunks`() = runTest {
        checkAll(longCrlf, seed) { s, sd ->
            val kord = tinyKord(s, rng = Random(sd))

            assertKordMatches(kord, s)
            kord.assertChunksBounded()
        }
    }

    @Test
    fun `long inserts at any position match the model`() = runTest {
        checkAll(longCrlf, longCrlf, Arb.int(0..Int.MAX_VALUE), seed) { base, ins, pos, sd ->
            val kord = tinyKord(base, rng = Random(sd))
            val at = pos % (base.length + 1)

            val edited = kord.insert(at, ins)
            val cut = base.effectiveCut(at)

            assertKordMatches(edited, base.substring(0, cut) + ins + base.substring(cut))
            edited.assertChunksBounded()
        }
    }

    @Test
    fun `typing one char at a time grows chunks up to the limit`() {
        val text = "the quick brown fox\r\njumps over\r\nthe lazy dog"
        var kord = tinyKord()

        for (c in text) kord = kord.insert(c.toString())

        assertKordMatches(kord, text)
        kord.assertChunksBounded()

        // Appends fill the last chunk in place, so this is far fewer than one chunk per char.
        kord.chunks().count() shouldBeLessThanOrEqual (text.length + tiny.maxChunkSize - 1) / tiny.maxChunkSize + 1
    }

    @Test
    fun `appending to an older version leaves newer versions untouched`() {
        val base = tinyKord("abc")
        val first = base.insert("XYZ")
        val second = base.insert("123")

        assertKordMatches(base, "abc")
        assertKordMatches(first, "abcXYZ")
        assertKordMatches(second, "abc123")

        // And the other way around: growing the newer version does not leak back.
        val third = first.insert("!")
        assertKordMatches(first, "abcXYZ")
        assertKordMatches(second, "abc123")
        assertKordMatches(third, "abcXYZ!")
    }

    @Test
    fun `inserting into both halves of a split chunk keeps versions apart`() {
        val base = tinyKord("abcdef")
        val left = base.insert(3, "L")
        val right = base.insert(6, "R")
        val both = left.insert(7, "R")

        assertKordMatches(base, "abcdef")
        assertKordMatches(left, "abcLdef")
        assertKordMatches(right, "abcdefR")
        assertKordMatches(both, "abcLdefR")
    }

    @Test
    fun `removing the end of a chunk and retyping does not overwrite older versions`() {
        val typed = tinyKord("abcd")
        val backspaced = typed.remove(3..3)
        val retyped = backspaced.insert("X")

        assertKordMatches(typed, "abcd")
        assertKordMatches(backspaced, "abc")
        assertKordMatches(retyped, "abcX")
    }

    private sealed interface Op {
        fun apply(k: Kord): Kord
        fun apply(s: String): String
    }

    private data class Ins(val at: Int, val text: String) : Op {
        override fun apply(k: Kord) = k.insert(at, text)
        override fun apply(s: String): String {
            val cut = s.effectiveCut(at)
            return s.substring(0, cut) + text + s.substring(cut)
        }
    }

    private data class Rem(val range: IntRange) : Op {
        override fun apply(k: Kord) = k.remove(range)
        override fun apply(s: String): String {
            val end = s.effectiveCut(range.last + 1)
            val start = s.effectiveCut(range.first)
            return s.substring(0, start) + s.substring(end)
        }
    }

    private data class Rep(val range: IntRange, val text: String) : Op {
        override fun apply(k: Kord) = k.replace(range, text)
        override fun apply(s: String): String {
            val end = s.effectiveCut(range.last + 1)
            val start = s.effectiveCut(range.first)
            return s.substring(0, start) + text + s.substring(end)
        }
    }

    private fun op(e: Edit, kind: Int, len: Int): Op = when {
        len == 0 || kind % 3 == 0 -> Ins(e.position(len), e.text)
        kind % 3 == 1 -> Rem(e.range(len))
        else -> Rep(e.range(len), e.text)
    }

    /**
     * Applies random edits, each to a randomly chosen earlier version, and checks after every step
     * that every version ever made still has its own text. Any buffer write that leaks between
     * versions shows up here.
     */
    private suspend fun branchingHistory(chars: Arb<Char>) {
        val steps = Arb.list(
            Arb.bind(Arb.int(0..Int.MAX_VALUE), Arb.int(0..Int.MAX_VALUE), editOps(chunkOf(chars, 0..12), 1..1)) { pick, kind, e ->
                Triple(pick, kind, e.single())
            },
            1..30,
        )

        checkAll(steps, seed) { ops, sd ->
            val history = mutableListOf(tinyKord(rng = Random(sd)) to "")

            for ((pick, kind, e) in ops) {
                val (kord, text) = history[pick % history.size]
                val o = op(e, kind, text.length)

                history += withClue("$o on ${text.debug()}") { o.apply(kord) to o.apply(text) }

                history.forEachIndexed { i, (k, t) ->
                    withClue("version $i after $o") {
                        assertKordMatches(k, t)
                    }
                }
            }

            history.forEach { (k, _) -> k.assertChunksBounded() }
        }
    }

    @Test
    fun `branching edit history keeps every version intact`() = runTest { branchingHistory(plainChars) }

    @Test
    fun `branching CRLF-heavy edit history keeps every version intact`() = runTest { branchingHistory(crlfChars) }

    @Test
    fun `default config handles text far larger than a chunk`() {
        val line = "0123456789abcdef\r\n"
        val text = line.repeat(1000) // 18,000 chars, about 18 default chunks

        val kord = kordOf(text, randomProvider = Random(1)::nextInt)
        assertKordMatches(kord, text)

        val mid = text.length / 2
        val edited = kord.insert(mid, text)
        val cut = text.effectiveCut(mid)
        assertKordMatches(edited, text.substring(0, cut) + text + text.substring(cut))
    }
}

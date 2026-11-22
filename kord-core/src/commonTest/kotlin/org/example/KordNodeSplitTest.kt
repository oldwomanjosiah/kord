package org.example

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test

/** Tree-level split: exact shapes, heap order and cached aggregates of both halves. */
class KordNodeSplitTest {
    @Test
    fun `split at 0 of a single chunk puts everything on the right`() {
        val (l, r) = leaf("ab", 0).split(0)
        l.text() shouldBe ""
        r.text() shouldBe "ab"
    }

    @Test
    fun `split at end of a single chunk puts everything on the left`() {
        val (l, r) = leaf("ab", 0).split(2)
        l.text() shouldBe "ab"
        r.text() shouldBe ""
    }

    @Test
    fun `split at a chunk boundary with children keeps order`() {
        val root = node("b", 10, left = leaf("a", 1), right = leaf("c", 1))

        val (l1, r1) = root.split(1)
        l1.text() shouldBe "a"
        r1.text() shouldBe "bc"

        val (l2, r2) = root.split(2)
        l2.text() shouldBe "ab"
        r2.text() shouldBe "c"
    }

    @Test
    fun `split next to whitespace keeps the whitespace on the correct side`() {
        assertSoftly {
            leaf("a b", 0).split(1).let { (l, r) -> l.text() shouldBe "a"; r.text() shouldBe " b" }
            leaf("a b", 0).split(2).let { (l, r) -> l.text() shouldBe "a "; r.text() shouldBe "b" }
            leaf(" ab", 0).split(1).let { (l, r) -> l.text() shouldBe " "; r.text() shouldBe "ab" }
            leaf("ab\t", 0).split(2).let { (l, r) -> l.text() shouldBe "ab"; r.text() shouldBe "\t" }
        }
    }

    @Test
    fun `split inside a whitespace chunk with children keeps order`() {
        val root = node("  ", 10, left = leaf("a", 1), right = leaf("b", 1))
        val (l, r) = root.split(2) // "a " | " b"
        l.text() shouldBe "a "
        r.text() shouldBe " b"
    }

    @Test
    fun `split yields prefix and suffix at every position`() = runTest {
        checkAll(Arb.list(plainChunk, 1..12), seed) { chunks, sd ->
            val tree = build(chunks, Random(sd))!!
            val text = chunks.joinToString("")

            for (at in 0..text.length) {
                val (l, r) = tree.split(at)
                withClue("split($at) of ${chunks.map { it.debug() }}") {
                    l.text() shouldBe text.substring(0, at)
                    r.text() shouldBe text.substring(at)
                    l.isHeapOrdered().shouldBeTrue()
                    r.isHeapOrdered().shouldBeTrue()
                    l.assertAggregates()
                    r.assertAggregates()
                }
            }
        }
    }

    @Test
    fun `split inside a CRLF chunk moves the cut before the CR`() {
        val (l, r) = leaf("a\r\nb", 0).split(2)
        l.text() shouldBe "a"
        r.text() shouldBe "\r\nb"
    }

    @Test
    fun `split inside a lone CRLF chunk with children keeps order`() {
        val root = node("\r\n", 10, left = leaf("a", 1), right = leaf("b", 1))
        val (l, r) = root.split(2) // between CR and LF, nudged to 1
        l.text() shouldBe "a"
        r.text() shouldBe "\r\nb"
    }

    @Test
    fun `split between a CR chunk and an LF chunk moves the cut before the CR`() {
        val root = node("\nb", 10, left = leaf("a\r", 1))
        val (l, r) = root.split(2)
        l.text() shouldBe "a"
        r.text() shouldBe "\r\nb"
    }

    @Test
    fun `split never separates CR from LF and preserves text`() = runTest {
        checkAll(Arb.list(crlfChunk, 1..12), seed) { chunks, sd ->
            val tree = build(chunks, Random(sd))!!
            val text = chunks.joinToString("")

            for (at in 0..text.length) {
                val (l, r) = tree.split(at)
                val lt = l.text()
                val rt = r.text()
                withClue("split($at) of ${chunks.map { it.debug() }}") {
                    lt + rt shouldBe text
                    lt.length shouldBe text.effectiveCut(at)
                    (lt.endsWith('\r') && rt.startsWith('\n')).shouldBeFalse()
                    l.isHeapOrdered().shouldBeTrue()
                    r.isHeapOrdered().shouldBeTrue()
                    l.assertAggregates()
                    r.assertAggregates()
                }
            }
        }
    }
}

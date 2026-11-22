package com.oldwomanjosiah.kord.internal

import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** [KordNode.createBulk]: building a treap from in-order chunks in one pass. */
class KordNodeCreateBulkTest {
    /** Hands out [prios] in order, failing if asked for more than there are. */
    private class Prios(private val prios: List<Int>) : () -> Int {
        var calls = 0
            private set

        override fun invoke(): Int = prios[calls++]
    }

    private fun bulk(chunks: List<String>, prios: List<Int>): KordNode =
        KordNode.createBulk(Prios(prios), *chunks.map { view(it) }.toTypedArray())!!

    /** Tree shape with priorities, so two trees can be compared structurally. */
    private fun KordNode?.shape(): String =
        if (this == null) "." else "(${left.shape()} ${contents}@$prio ${right.shape()})"

    /** The same chunks and priorities, built with one merge per chunk. */
    private fun viaMerge(chunks: List<String>, prios: List<Int>): KordNode? =
        chunks.zip(prios).fold(null as KordNode?) { acc, (s, p) -> KordNode.merge(acc, leaf(s, p)) }

    private val chunks = Arb.list(plainChunk, 1..40)
    private val crlfChunks = Arb.list(crlfChunk, 1..40)

    /** Narrow range so ties are common. */
    private fun prios(n: Int) = Arb.list(Arb.int(0..3), n..n)

    @Test
    fun `single view becomes a leaf`() {
        val v = view("abc")
        val root = KordNode.createBulk({ 7 }, v)!!

        root.left shouldBe null
        root.right shouldBe null
        root.prio shouldBe 7
        root.contents shouldBeSameInstanceAs v
        root.assertAggregates()
    }

    @Test
    fun `in-order chunks are exactly the inputs`() = runTest {
        checkAll(chunks, seed) { cs, sd ->
            val rng = kotlin.random.Random(sd)
            val root = bulk(cs, List(cs.size) { rng.nextInt() })

            root.text() shouldBe cs.joinToString("")
            root.chunkTexts() shouldBe cs
        }
    }

    @Test
    fun `result is a max-heap on priority - matching merge`() = runTest {
        checkAll(chunks, seed) { cs, sd ->
            val rng = kotlin.random.Random(sd)
            val root = bulk(cs, List(cs.size) { rng.nextInt() })

            root.isHeapOrdered().shouldBeTrue()
        }
    }

    @Test
    fun `shape matches building the same chunks with merge - including ties`() = runTest {
        checkAll(chunks) { cs ->
            checkAll(10, prios(cs.size)) { ps ->
                withClue("prios $ps") {
                    bulk(cs, ps).shape() shouldBe viaMerge(cs, ps).shape()
                }
            }
        }
    }

    @Test
    fun `aggregates are correct - including CRLF pairs split across chunks`() = runTest {
        checkAll(crlfChunks, seed) { cs, sd ->
            val rng = kotlin.random.Random(sd)
            val root = bulk(cs, List(cs.size) { rng.nextInt() })

            root.assertAggregates()
        }
    }

    @Test
    fun `each view gets the priority drawn for it - in order`() = runTest {
        checkAll(chunks, seed) { cs, sd ->
            val rng = kotlin.random.Random(sd)
            val ps = List(cs.size) { rng.nextInt() }
            val provider = Prios(ps)
            val views = cs.map { view(it) }

            val root = KordNode.createBulk(provider, *views.toTypedArray())

            provider.calls shouldBe cs.size
            root.allNodes().map { it.prio } shouldBe ps
        }
    }

    @Test
    fun `views are used as given - not copied`() {
        val whole = view("hello, world")
        val parts = listOf(whole.subSequence(0, 5), whole.subSequence(5, 7), whole.subSequence(7, 12))

        val root = KordNode.createBulk(Prios(listOf(1, 3, 2)), *parts.toTypedArray())

        root.allNodes().map { it.contents }.zip(parts).forEach { (actual, expected) ->
            actual shouldBeSameInstanceAs expected
        }
        root.text() shouldBe "hello, world"
    }

    @Test
    fun `monotonic priorities build a valid chain`() {
        val cs = List(500) { "c$it " }

        for (ps in listOf(List(cs.size) { it }, List(cs.size) { -it }, List(cs.size) { 0 })) {
            val root = bulk(cs, ps)

            root.chunkTexts() shouldBe cs
            root.isHeapOrdered().shouldBeTrue()
            root.length shouldBe cs.sumOf { it.length }
        }
    }

    @Test
    fun `result can be split at every position`() = runTest {
        checkAll(Arb.list(plainChunk, 1..10), seed) { cs, sd ->
            val rng = kotlin.random.Random(sd)
            val root = bulk(cs, List(cs.size) { rng.nextInt() })
            val text = cs.joinToString("")

            for (at in 0..text.length) {
                val (l, r) = root.split(at)
                l.text() shouldBe text.substring(0, at)
                r.text() shouldBe text.substring(at)
            }
        }
    }

    @Test
    fun `two bulk trees merge into their concatenation`() = runTest {
        checkAll(chunks, chunks, seed) { a, b, sd ->
            val rng = kotlin.random.Random(sd)
            val merged = KordNode.merge(
                bulk(a, List(a.size) { rng.nextInt() }),
                bulk(b, List(b.size) { rng.nextInt() }),
            )

            merged.chunkTexts() shouldBe a + b
            merged.isHeapOrdered().shouldBeTrue()
            merged.assertAggregates()
        }
    }
}

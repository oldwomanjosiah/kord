package org.example

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test

// ---------------------------------------------------------------------------
// Reference model
// ---------------------------------------------------------------------------

/**
 * Independent oracle for line breaks: CRLF counts once, and every other Unicode line
 * terminator (LF, VT, FF, CR, NEL, LS, PS) counts once each.
 */
private val lineBreak = Regex("\r\n|[\n\u000B\u000C\r\u0085  ]")
private fun String.refNewlines(): Int = lineBreak.findAll(this).count()

/**
 * Where an insert or split at `at` should land: the implementation nudges a cut that falls
 * between CR and LF to just before the CR, so a CRLF pair is never separated.
 */
private fun CharSequence.effectiveCut(at: Int): Int =
    if (at in 1 until length && this[at - 1] == '\r' && this[at] == '\n') at - 1 else at

// ---------------------------------------------------------------------------
// Tree helpers
// ---------------------------------------------------------------------------

/** In-order concatenation of every chunk: the text the tree represents. */
private fun KordNode?.text(): String = buildString { appendInOrder(this@text) }

private fun StringBuilder.appendInOrder(node: KordNode?) {
    if (node == null) return
    appendInOrder(node.left)
    append(node.contents)
    appendInOrder(node.right)
}

private fun KordNode?.chunks(): List<String> =
    if (this == null) emptyList() else left.chunks() + contents + right.chunks()

private fun KordNode?.allNodes(): List<KordNode> =
    if (this == null) emptyList() else left.allNodes() + this + right.allNodes()

/** Max-heap on priority: no child outranks its parent. */
private fun KordNode?.isHeapOrdered(): Boolean {
    if (this == null) return true
    val l = left
    val r = right
    return (l == null || l.prio <= prio) &&
        (r == null || r.prio <= prio) &&
        l.isHeapOrdered() && r.isHeapOrdered()
}

/**
 * Every node's cached aggregates are correct for the text of its own subtree. This is checked
 * against the oracle rather than "sum of children", so it holds for any correct design,
 * including one that corrects for CRLF pairs split across chunks.
 */
private fun KordNode?.assertAggregates() {
    for (node in allNodes()) {
        val t = node.text()
        withClue("subtree ${t.debug()}") {
            node.length shouldBe t.length
            node.newlines shouldBe t.refNewlines()
        }
    }
}

private fun leaf(s: String, prio: Int) = KordNode(contents = s, prio = prio)

/** Builds a treap whose in-order text is chunks joined, via merge only. */
private fun build(chunks: List<String>, rng: Random): KordNode? =
    chunks.fold(null as KordNode?) { acc, s -> KordNode.merge(acc, leaf(s, rng.nextInt())) }

/** Hand-built node with explicit children, for exact shapes. */
private fun node(s: String, prio: Int, left: KordNode? = null, right: KordNode? = null) =
    KordNode(contents = s, prio = prio, left = left, right = right)

/**
 * Mirrors the body of [KordImpl.insert] at node level. KordImpl keeps its root private and has
 * no way to read its text back, so content checks have to go through KordNode directly.
 */
private fun KordNode?.insert(at: Int, s: String, prio: Int): KordNode {
    val node = leaf(s, prio)
    val (l, r) = this?.split(at) ?: return node
    return KordNode.merge(KordNode.merge(l, node), r)!!
}

private fun String.debug() = buildString {
    append('"')
    for (c in this@debug) when (c) {
        '\n' -> append("\\n")
        '\r' -> append("\\r")
        '\t' -> append("\\t")
        else -> if (c.code < 0x20 || c.code in 0x7F..0xA0 || c == ' ' || c == ' ') {
            append("\\u").append(c.code.toString(16).padStart(4, '0'))
        } else append(c)
    }
    append('"')
}

// ---------------------------------------------------------------------------
// Generators
// ---------------------------------------------------------------------------

/** No CR, so cut positions are never nudged. Includes whitespace and other line breaks. */
private val plainChars = Arb.element('a', 'b', 'x', ' ', '\t', '\n', '\u000B', '\u0085', ' ', ' ')

/** Heavy on CR and LF so that CRLF pairs, including ones spanning chunks, are common. */
private val crlfChars = Arb.element('a', 'b', ' ', '\r', '\n', '\r', '\n', ' ')

private fun chunkOf(chars: Arb<Char>, sizes: IntRange) =
    Arb.list(chars, sizes).map { it.joinToString("") }

private val plainChunk = chunkOf(plainChars, 1..8)
private val crlfChunk = chunkOf(crlfChars, 1..8)
private val anyString = chunkOf(Arb.element('a', ' ', '\r', '\n', '\u000B', '\u000C', '\u0085', ' ', ' '), 0..24)

private data class Insert(val posSeed: Int, val text: String) {
    fun position(len: Int) = posSeed % (len + 1)
}

private fun insertOps(chunk: Arb<String>) =
    Arb.list(Arb.bind(Arb.int(0..Int.MAX_VALUE), chunk) { p, s -> Insert(p, s) }, 1..40)

private val plainOps = insertOps(plainChunk)
private val crlfOps = insertOps(crlfChunk)
private val seed = Arb.long()

// ---------------------------------------------------------------------------
// Newline counting
// ---------------------------------------------------------------------------

class NewlineCountTest {
    @Test
    fun `fixed cases`() {
        val cases = listOf(
            "" to 0,
            "abc" to 0,
            "a\nb\n" to 2,
            "\n" to 1,
            "\r" to 1,
            "\r\n" to 1,
            "\n\r" to 2,
            "\n\n" to 2,
            "\r\r" to 2,
            "\r\r\n" to 2,
            "\r\n\r\n" to 2,
            "\r\n\n" to 2,
            "\u000B" to 1,
            "\u000C" to 1,
            "\u0085" to 1,
            " " to 1,
            " " to 1,
            " \n" to 2,
            "\u0085\n" to 2,
            "a\r\nb\rc\nd" to 3,
        )
        assertSoftly {
            for ((s, expected) in cases) withClue(s.debug()) { s.newlines shouldBe expected }
        }
    }

    @Test
    fun `isNewline accepts exactly the Unicode line terminators`() {
        val terminators = setOf('\n', '\u000B', '\u000C', '\r', '\u0085', ' ', ' ')
        assertSoftly {
            for (code in 0..0xFFFF) {
                val c = code.toChar()
                withClue("U+${code.toString(16).padStart(4, '0')}") {
                    c.isNewline shouldBe (c in terminators)
                }
            }
        }
    }

    @Test
    fun `string count matches reference`() = runTest {
        checkAll(anyString) { s -> withClue(s.debug()) { s.newlines shouldBe s.refNewlines() } }
    }

    @Test
    fun `node count matches reference`() = runTest {
        checkAll(anyString.filter { it.isNotEmpty() }) { s -> leaf(s, 0).newlines shouldBe s.refNewlines() }
    }

    @Test
    fun `empty chunks are rejected`() {
        shouldThrow<IllegalStateException> { leaf("", 0) }
        shouldThrow<IllegalStateException> { node("", 0, left = leaf("a", -1), right = leaf("b", -1)) }
    }

    @Test
    fun `CRLF split across two chunks counts once`() {
        val tree = node("\nb", 0, left = leaf("a\r", -1))
        tree.text() shouldBe "a\r\nb"
        tree.newlines shouldBe 1
    }

    @Test
    fun `CRLF split across chunks counts once in every tree shape`() = runTest {
        checkAll(seed) { sd -> build(listOf("a\r", "\n", "b\r", "\nc"), Random(sd)).newlines shouldBe 2 }
    }

    @Test
    fun `inserting LF after a trailing CR does not add a line`() {
        val tree = leaf("a\r", 10).insert(2, "\n", 1)
        tree.text() shouldBe "a\r\n"
        tree.newlines shouldBe 1
    }

    @Test
    fun `inserting CR before a leading LF does not add a line`() {
        val tree = leaf("\nb", 10).insert(0, "\r", 1)
        tree.text() shouldBe "\r\nb"
        tree.newlines shouldBe 1
    }

    @Test
    fun `merged trees count CRLF pairs once`() = runTest {
        checkAll(Arb.list(crlfChunk, 0..12), seed) { chunks, sd ->
            val tree = build(chunks, Random(sd))
            withClue("chunks ${chunks.map { it.debug() }}") { tree.assertAggregates() }
        }
    }
}

// ---------------------------------------------------------------------------
// Merge
// ---------------------------------------------------------------------------

class MergeTest {
    @Test
    fun `merge concatenates text and keeps the heap ordered`() = runTest {
        checkAll(Arb.list(plainChunk, 0..10), Arb.list(plainChunk, 0..10), seed) { a, b, sd ->
            val rng = Random(sd)
            val merged = KordNode.merge(build(a, rng), build(b, rng))

            merged.text() shouldBe (a + b).joinToString("")
            merged.chunks() shouldBe a + b
            merged.isHeapOrdered().shouldBeTrue()
            merged.assertAggregates()
        }
    }

    @Test
    fun `merge with null is identity`() {
        val t = leaf("ab", 0)
        KordNode.merge(t, null) shouldBe t
        KordNode.merge(null, t) shouldBe t
        KordNode.merge(null, null) shouldBe null
    }
}

// ---------------------------------------------------------------------------
// Split
// ---------------------------------------------------------------------------

class SplitTest {
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
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Insert
// ---------------------------------------------------------------------------

class InsertTest {
    @Test
    fun `insert at start, middle and end`() {
        assertSoftly {
            leaf("ab", 0).insert(0, "X", 1).text() shouldBe "Xab"
            leaf("ab", 0).insert(1, "X", 1).text() shouldBe "aXb"
            leaf("ab", 0).insert(2, "X", 1).text() shouldBe "abX"
        }
    }

    @Test
    fun `insert next to whitespace`() {
        assertSoftly {
            leaf("a b", 0).insert(1, "X", 1).text() shouldBe "aX b"
            leaf("a b", 0).insert(2, "X", 1).text() shouldBe "a Xb"
        }
    }

    @Test
    fun `insert between CR and LF lands before the CR`() {
        leaf("a\r\nb", 0).insert(2, "X", 1).text() shouldBe "aX\r\nb"
    }

    @Test
    fun `random plain inserts match a StringBuilder model`() = runTest {
        checkAll(plainOps, seed) { ops, sd -> checkAgainstModel(ops, Random(sd)) }
    }

    @Test
    fun `random CRLF-heavy inserts match a StringBuilder model`() = runTest {
        checkAll(crlfOps, seed) { ops, sd -> checkAgainstModel(ops, Random(sd)) }
    }

    private fun checkAgainstModel(ops: List<Insert>, rng: Random) {
        val model = StringBuilder()
        var tree: KordNode? = null

        ops.forEachIndexed { i, op ->
            val at = op.position(model.length)
            model.insert(model.effectiveCut(at), op.text)
            tree = tree.insert(at, op.text, rng.nextInt())

            withClue("after op #$i insert($at, ${op.text.debug()}) -> ${model.toString().debug()}") {
                tree.text() shouldBe model.toString()
                tree.isHeapOrdered().shouldBeTrue()
                tree.chunks() shouldNotContain ""
                tree.assertAggregates()
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Kord, built through the public factory
// ---------------------------------------------------------------------------

/**
 * Builds a rope through the public `Kord(vararg of)` factory. The `Kord` interface has no members
 * yet, so reading anything back (length, split, insert) still needs a cast to the internal impl.
 */
private fun kordOf(vararg parts: String): KordImpl = Kord(*parts) as KordImpl
private fun kordOf(parts: List<String>): KordImpl = kordOf(*parts.toTypedArray())

class KordTest {
    @Test
    fun `empty kord has length 0`() {
        kordOf().length shouldBe 0
    }

    @Test
    fun `factory concatenates its arguments`() {
        assertSoftly {
            kordOf("ab").length shouldBe 2
            kordOf("ab", "cd", "e").length shouldBe 5
            kordOf("a\r", "\nb").length shouldBe 4
        }
    }

    @Test
    fun `factory with empty strings has length 0`() {
        assertSoftly {
            kordOf("").length shouldBe 0
            kordOf("", "").length shouldBe 0
            kordOf("", "ab", "").length shouldBe 2
        }
    }

    @Test
    fun `factory length is the sum of its parts`() = runTest {
        checkAll(Arb.list(chunkOf(crlfChars, 0..8), 0..20)) { parts ->
            kordOf(parts).length shouldBe parts.sumOf { it.length }
        }
    }

    @Test
    fun `length tracks inserted text`() = runTest {
        checkAll(Arb.list(crlfChunk, 0..5), crlfOps) { initial, ops ->
            var k = kordOf(initial)
            var expected = initial.sumOf { it.length }
            for (op in ops) {
                k = k.insert(op.position(k.length), op.text)
                expected += op.text.length
                k.length shouldBe expected
            }
        }
    }

    @Test
    fun `insert of empty text is a no-op`() {
        val k = kordOf("abc")
        k.insert(1, "") shouldBe k
    }

    @Test
    fun `split lengths add up`() = runTest {
        checkAll(Arb.list(plainChunk, 0..12)) { parts ->
            val k = kordOf(parts)
            for (at in 0..k.length) {
                val (l, r) = k.split(at)
                withClue("split($at) of ${parts.map { it.debug() }}") {
                    l.length shouldBe at
                    r.length shouldBe k.length - at
                }
            }
        }
    }

    @Test
    fun `split lengths add up with CRLF nudging`() = runTest {
        checkAll(Arb.list(crlfChunk, 0..12)) { parts ->
            val k = kordOf(parts)
            val text = parts.joinToString("")
            for (at in 0..k.length) {
                val (l, r) = k.split(at)
                withClue("split($at) of ${parts.map { it.debug() }}") {
                    l.length shouldBe text.effectiveCut(at)
                    l.length + r.length shouldBe k.length
                }
            }
        }
    }

    @Test
    fun `split inside a CRLF across factory parts lands before the CR`() {
        val (l, r) = kordOf("a\r", "\nb").split(2)
        l.length shouldBe 1
        r.length shouldBe 3
    }

    @Test
    fun `split after inserts keeps lengths consistent`() = runTest {
        checkAll(Arb.list(crlfChunk, 0..5), crlfOps) { initial, ops ->
            var k = kordOf(initial)
            for (op in ops) k = k.insert(op.position(k.length), op.text)

            for (at in 0..k.length) {
                val (l, r) = k.split(at)
                withClue("split($at) of length ${k.length}") {
                    l.length shouldBeInRange (at - 1)..at
                    l.length + r.length shouldBe k.length
                }
            }
        }
    }

    @Test
    fun `out of range indices throw`() {
        val k = kordOf("abc")
        assertSoftly {
            shouldThrow<IndexOutOfBoundsException> { k.insert(-1, "x") }
            shouldThrow<IndexOutOfBoundsException> { k.insert(4, "x") }
            shouldThrow<IndexOutOfBoundsException> { k.split(-1) }
            shouldThrow<IndexOutOfBoundsException> { k.split(4) }
            shouldThrow<IndexOutOfBoundsException> { kordOf().split(1) }
            shouldThrow<IndexOutOfBoundsException> { kordOf().insert(1, "x") }
        }
    }

    @Test
    fun `splitting an empty kord gives two empty kords`() {
        val (l, r) = kordOf().split(0)
        l.length shouldBe 0
        r.length shouldBe 0
    }
}

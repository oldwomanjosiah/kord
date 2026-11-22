package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.Kord
import com.oldwomanjosiah.kord.KordNode
import com.oldwomanjosiah.kord.kordOf
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import kotlin.random.Random
import kotlin.text.iterator

// ---------------------------------------------------------------------------
// Reference model
// ---------------------------------------------------------------------------

/**
 * Independent oracle for line breaks: CRLF counts once, and every other Unicode line
 * terminator (LF, VT, FF, CR, NEL, LS, PS) counts once each.
 */
private val lineBreak = Regex("\r\n|[\n\u000B\u000C\r\u0085  ]")
internal fun String.refNewlines(): Int = lineBreak.findAll(this).count()

/**
 * Where a cut at `at` should land: the implementation nudges a cut that falls between CR and
 * LF to just before the CR, so a CRLF pair is never separated.
 */
internal fun CharSequence.effectiveCut(at: Int): Int =
    if (at in 1 until length && this[at - 1] == '\r' && this[at] == '\n') at - 1 else at

internal fun String.debug() = buildString {
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
// Public API helpers
// ---------------------------------------------------------------------------

/** Reads a Kord's text back through [get] only, independent of [chunks]. */
internal fun Kord.text(): String = buildString { for (i in 0 until this@text.length) append(this@text[i]) }

internal fun kordOf(parts: List<String>): Kord = kordOf(*parts.toTypedArray())

/** Checks everything the public API can observe about [kord] against the expected [text]. */
internal fun assertKordMatches(kord: Kord, text: String) {
    withClue("kord expected to be ${text.debug()}") {
        kord.text() shouldBe text
        kord.charSequence().toString() shouldBe text
        kord.length shouldBe text.length
        kord.isEmpty() shouldBe text.isEmpty()
        kord.lines shouldBe text.refNewlines() + 1
    }
}

// ---------------------------------------------------------------------------
// Node helpers (for tests of internal structure)
// ---------------------------------------------------------------------------

/** In-order concatenation of every chunk: the text the tree represents. */
internal fun KordNode?.text(): String = buildString { appendInOrder(this@text) }

private fun StringBuilder.appendInOrder(node: KordNode?) {
    if (node == null) return
    appendInOrder(node.left)
    append(node.contents)
    appendInOrder(node.right)
}

internal fun KordNode?.chunkTexts(): List<String> =
    if (this == null) emptyList() else left.chunkTexts() + contents + right.chunkTexts()

internal fun KordNode?.allNodes(): List<KordNode> =
    if (this == null) emptyList() else left.allNodes() + listOf(this) + right.allNodes()

/** Max-heap on priority: no child outranks its parent. */
internal fun KordNode?.isHeapOrdered(): Boolean {
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
internal fun KordNode?.assertAggregates() {
    for (node in allNodes()) {
        val t = node.text()
        withClue("subtree ${t.debug()}") {
            node.length shouldBe t.length
            node.newlines shouldBe t.refNewlines()
            node.firstChar shouldBe t.first()
            node.lastChar shouldBe t.last()
        }
    }
}

internal fun leaf(s: String, prio: Int) = KordNode(contents = s, prio = prio)

/** Hand-built node with explicit children, for exact shapes. */
internal fun node(s: String, prio: Int, left: KordNode? = null, right: KordNode? = null) =
    KordNode(contents = s, prio = prio, left = left, right = right)

/** Builds a treap whose in-order chunks are [chunks], via merge only. */
internal fun build(chunks: List<String>, rng: Random): KordNode? =
    chunks.fold(null as KordNode?) { acc, s -> KordNode.merge(acc, leaf(s, rng.nextInt())) }

// ---------------------------------------------------------------------------
// Generators
// ---------------------------------------------------------------------------

/** No CR, so cut positions are never nudged. Includes whitespace and other line breaks. */
internal val plainChars = Arb.element('a', 'b', 'x', ' ', '\t', '\n', '\u000B', '\u0085', ' ', ' ')

/** Heavy on CR and LF so that CRLF pairs, including ones spanning chunks, are common. */
internal val crlfChars = Arb.element('a', 'b', ' ', '\r', '\n', '\r', '\n', ' ')

internal fun chunkOf(chars: Arb<Char>, sizes: IntRange) =
    Arb.list(chars, sizes).map { it.joinToString("") }

internal val plainChunk = chunkOf(plainChars, 1..8)
internal val crlfChunk = chunkOf(crlfChars, 1..8)
internal val anyString =
    chunkOf(Arb.element('a', ' ', '\r', '\n', '\u000B', '\u000C', '\u0085', ' ', ' '), 0..24)

internal val seed = Arb.long()

/** An edit whose position (and range, for removes and replaces) is chosen relative to the current length. */
internal data class Edit(val seedA: Int, val seedB: Int, val text: String) {
    /** An insertion point in `0..len`. */
    fun position(len: Int) = seedA % (len + 1)

    /** A non-empty range inside `0 until len`; requires `len > 0`. */
    fun range(len: Int): IntRange {
        val first = seedA % len
        val last = first + seedB % (len - first)
        return first..last
    }
}

internal fun editOps(chunk: Arb<String>, sizes: IntRange = 1..40) = Arb.list(
    Arb.bind(Arb.int(0..Int.MAX_VALUE), Arb.int(0..Int.MAX_VALUE), chunk) { a, b, s -> Edit(a, b, s) },
    sizes,
)

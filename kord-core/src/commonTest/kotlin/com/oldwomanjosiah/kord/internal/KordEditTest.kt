package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.Kord
import com.oldwomanjosiah.kord.kordOf
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/**
 * [Kord.edit]: the block runs on a kord holding only the region, indexed from 0, with the region's
 * start passed as `offset`. Whatever it returns replaces the region.
 */
class KordEditTest {
    /** Runs [check] on the block's receiver and offset, then returns the receiver unchanged. */
    private fun Kord.inspect(range: IntRange, check: Kord.(offset: Int) -> Unit) = edit(range) { offset ->
        check(offset)
        this
    }

    @Test
    fun `returning the region unchanged is a no-op`() {
        assertKordMatches(kordOf("ab", "cd", "ef").edit(1..4) { this }, "abcdef")
    }

    @Test
    fun `the block sees only the region`() {
        kordOf("ab", "cd", "ef").inspect(1..4) { offset ->
            assertKordMatches(this, "bcde")
            offset shouldBe 1
        }
    }

    @Test
    fun `chunks inside the block are relative to the region`() {
        kordOfChunks("ab", "cd", "ef").inspect(1..4) {
            chunks().map { it.contents.toString() to it.contentsRange } shouldBe listOf("b" to 0..0, "cd" to 1..2, "e" to 3..3)
        }
    }

    @Test
    fun `edits inside the block are relative to the region`() {
        assertSoftly {
            assertKordMatches(kordOf("abcdef").edit(2..3) { insert(1, "X") }, "abcXdef")
            assertKordMatches(kordOf("abcdef").edit(2..3) { remove(0..0) }, "abdef")
            assertKordMatches(kordOf("abcdef").edit(2..3) { replace(0..1, "XY") }, "abXYef")
            assertKordMatches(kordOf("abcdef").edit(1..2) { insert("X") }, "abcXdef")
        }
    }

    @Test
    fun `offset converts whole-kord positions`() {
        assertKordMatches(kordOf("abcdef").edit(2..4) { offset -> insert(4 - offset, "X") }, "abcdXef")
    }

    @Test
    fun `several edits in one block apply in order`() {
        val result = kordOf("abcdef").edit(1..4) {
            insert(0, "X")              // "Xbcde"
                .remove(2..2)           // "Xbde"
                .replace(3..3, "YZ")    // "XbdYZ"
        }
        assertKordMatches(result, "aXbdYZf")
    }

    @Test
    fun `edits past the region throw`() {
        assertSoftly {
            shouldThrow<IndexOutOfBoundsException> { kordOf("abcdef").edit(2..3) { insert(-1, "X") } }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abcdef").edit(2..3) { insert(3, "X") } }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abcdef").edit(2..3) { remove(1..2) } }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abcdef").edit(2..3) { get(2); this } }
        }
    }

    @Test
    fun `edit range out of bounds throws`() {
        assertSoftly {
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").edit(-1..1) { this } }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").edit(1..3) { this } }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").edit(4 until 4) { this } }
            shouldThrow<IndexOutOfBoundsException> { kordOf().edit(0..0) { this } }
        }
    }

    @Test
    fun `edit of an empty range inserts at that position`() {
        assertSoftly {
            assertKordMatches(kordOf("abc").edit(1 until 1) { offset -> offset shouldBe 1; insert(0, "X") }, "aXbc")
            assertKordMatches(kordOf("abc").edit(3 until 3) { insert(0, "X") }, "abcX")
            assertKordMatches(kordOf().edit(0 until 0) { offset -> offset shouldBe 0; insert(0, "X") }, "X")
        }
    }

    @Test
    fun `returning a different kord replaces the region`() {
        assertKordMatches(kordOf("abcdef").edit(1..4) { kordOf("XY") }, "aXYf")
        assertKordMatches(kordOf("abcdef").edit(1..4) { kordOf() }, "af")
    }

    @Test
    fun `returning a kord from another implementation throws`() {
        val foreign = object : Kord by kordOf("x") {}
        shouldThrow<IllegalArgumentException> { kordOf("abc").edit(0..1) { foreign } }
    }

    @Test
    fun `edit does not modify the original`() {
        val k = kordOf("abcdef")
        k.edit(1..4) { remove(0..3) }
        assertKordMatches(k, "abcdef")
    }

    @Test
    fun `nested edits are relative to the enclosing region`() {
        val result = kordOf("abcdefgh").edit(1..6) { outer ->          // "bcdefg"
            outer shouldBe 1
            edit(2..3) { inner ->                                     // "de"
                inner shouldBe 2
                assertKordMatches(this, "de")
                insert(1, "X")
            }
        }
        assertKordMatches(result, "abcdXefgh")
    }

    @Test
    fun `region starting between CR and LF is nudged and offset accounts for it`() {
        val result = kordOf("a\r\nbc").edit(2..3) { offset ->
            offset shouldBe 1
            assertKordMatches(this, "\r\nb")
            insert(0, "X")
        }
        assertKordMatches(result, "aX\r\nbc")
    }

    @Test
    fun `region ending between CR and LF stops before the CR`() {
        kordOf("ab\r\nc").inspect(0..2) { assertKordMatches(this, "ab") }
    }

    @Test
    fun `edits forming a CRLF across the region boundary count it once`() {
        assertKordMatches(kordOf("a\r", "bc").edit(2..2) { replace(0..0, "\n") }, "a\r\nc")
        assertKordMatches(kordOf("ab", "\nc").edit(1..1) { replace(0..0, "\r") }, "a\r\nc")
    }

    @Test
    fun `random edits in a block match the same edits on the whole kord`() = runTest {
        checkAll(
            Arb.list(plainChunk, 1..8),
            Arb.int(0..Int.MAX_VALUE),
            Arb.int(0..Int.MAX_VALUE),
            editOps(plainChunk, 1..10),
        ) { initial, startSeed, lenSeed, ops ->
            val kord = kordOf(initial)
            val start = startSeed % (kord.length + 1)
            val region = start until start + lenSeed % (kord.length - start + 1)

            var direct = kord
            val edited = kord.edit(region) { offset ->
                offset shouldBe region.first
                var current: Kord = this

                ops.forEachIndexed { i, op ->
                    val kind = op.seedB % 3
                    if (kind == 0 || current.length == 0) {
                        val at = op.position(current.length)
                        current = current.insert(at, op.text)
                        direct = direct.insert(at + offset, op.text)
                    } else {
                        val range = op.range(current.length)
                        val shifted = range.first + offset..range.last + offset
                        if (kind == 1) {
                            current = current.remove(range)
                            direct = direct.remove(shifted)
                        } else {
                            current = current.replace(range, op.text)
                            direct = direct.replace(shifted, op.text)
                        }
                    }
                    withClue("after op #$i in region $region") {
                        current.text() shouldBe direct.text().substring(offset, direct.length - (kord.length - region.last - 1))
                    }
                }
                current
            }

            withClue("edit($region) of ${initial.map { it.debug() }}") { assertKordMatches(edited, direct.text()) }
        }
    }

    @Test
    fun `random CRLF-heavy replacements keep cached counts consistent`() = runTest {
        checkAll(
            Arb.list(crlfChunk, 1..8),
            Arb.int(0..Int.MAX_VALUE),
            Arb.int(0..Int.MAX_VALUE),
            crlfChunk,
        ) { initial, startSeed, lenSeed, text ->
            val kord = kordOf(initial)
            val original = kord.text()
            val start = startSeed % (kord.length + 1)
            val region = start until start + lenSeed % (kord.length - start + 1)
            val cutStart = original.effectiveCut(region.first)
            val cutEnd = original.effectiveCut(region.last + 1)

            val edited = kord.edit(region) { offset ->
                offset shouldBe cutStart
                kordOf(text)
            }
            withClue("edit($region) of ${initial.map { it.debug() }} with ${text.debug()}") {
                assertKordMatches(edited, original.substring(0, cutStart) + text + original.substring(cutEnd))
            }
        }
    }
}

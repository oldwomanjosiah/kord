package org.example

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class KordRemoveTest {
    @Test
    fun `remove from start middle and end`() {
        assertSoftly {
            assertKordMatches(kordOf("abcd").remove(0..0), "bcd")
            assertKordMatches(kordOf("abcd").remove(1..2), "ad")
            assertKordMatches(kordOf("abcd").remove(3..3), "abc")
        }
    }

    @Test
    fun `remove everything gives an empty kord`() {
        assertKordMatches(kordOf("ab", "cd").remove(0..3), "")
    }

    @Test
    fun `remove across parts`() {
        assertKordMatches(kordOf("ab", "cd", "ef").remove(1..4), "af")
    }

    @Test
    fun `remove of an empty range is a no-op`() {
        assertSoftly {
            assertKordMatches(kordOf("abc").remove(1 until 1), "abc")
            assertKordMatches(kordOf("abc").remove(0 until 0), "abc")
            assertKordMatches(kordOf().remove(0 until 0), "")
        }
    }

    @Test
    fun `remove does not modify the original`() {
        val k = kordOf("abc")
        k.remove(0..1)
        assertKordMatches(k, "abc")
    }

    @Test
    fun `remove a whole CRLF pair`() {
        assertKordMatches(kordOf("a\r\nb").remove(1..2), "ab")
        assertKordMatches(kordOf("a\r", "\nb").remove(1..2), "ab")
    }

    @Test
    fun `remove starting between CR and LF removes the whole pair and the rest of the range`() {
        // The start is nudged to before the CR, so 'b' (index 3) is still inside the removed range.
        assertKordMatches(kordOf("a\r\nbc").remove(2..3), "ac")
    }

    @Test
    fun `remove joining a CR and an LF counts them as one line break`() {
        assertKordMatches(kordOf("a\rx\nb").remove(2..2), "a\r\nb")
    }

    @Test
    fun `remove out of range throws`() {
        assertSoftly {
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").remove(-1..0) }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").remove(0..3) }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").remove(3..3) }
            shouldThrow<IndexOutOfBoundsException> { kordOf().remove(0..0) }
        }
    }

    @Test
    fun `random removes match a StringBuilder model`() = runTest {
        checkAll(Arb.list(plainChunk, 1..10), editOps(plainChunk, 1..10)) { initial, ops ->
            checkAgainstModel(initial, ops)
        }
    }

    @Test
    fun `random removes from CRLF-heavy text keep CRLF pairs together`() = runTest {
        checkAll(Arb.list(crlfChunk, 1..10), editOps(crlfChunk, 1..10)) { initial, ops ->
            checkAgainstModel(initial, ops)
        }
    }

    private fun checkAgainstModel(initial: List<String>, ops: List<Edit>) {
        val model = StringBuilder(initial.joinToString(""))
        var kord = kordOf(initial)

        ops.forEachIndexed { i, op ->
            if (model.isEmpty()) return
            val range = op.range(model.length)
            model.deleteRange(model.effectiveCut(range.first), model.effectiveCut(range.last + 1))
            kord = kord.remove(range)

            withClue("after op #$i remove($range)") { assertKordMatches(kord, model.toString()) }
        }
    }
}

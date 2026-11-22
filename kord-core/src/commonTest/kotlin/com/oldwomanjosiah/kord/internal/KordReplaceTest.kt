package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.kordOf
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class KordReplaceTest {
    @Test
    fun `replace at start middle and end`() {
        assertSoftly {
            assertKordMatches(kordOf("abcd").replace(0..0, "XY"), "XYbcd")
            assertKordMatches(kordOf("abcd").replace(1..2, "X"), "aXd")
            assertKordMatches(kordOf("abcd").replace(3..3, "XY"), "abcXY")
        }
    }

    @Test
    fun `replace everything`() {
        assertKordMatches(kordOf("ab", "cd").replace(0..3, "X"), "X")
    }

    @Test
    fun `replace across parts`() {
        assertKordMatches(kordOf("ab", "cd", "ef").replace(1..4, "X"), "aXf")
    }

    @Test
    fun `replace with empty text removes`() {
        assertKordMatches(kordOf("abcd").replace(1..2, ""), "ad")
    }

    @Test
    fun `replace of an empty range inserts`() {
        assertSoftly {
            assertKordMatches(kordOf("abc").replace(1 until 1, "X"), "aXbc")
            assertKordMatches(kordOf("abc").replace(3 until 3, "X"), "abcX")
            assertKordMatches(kordOf().replace(0 until 0, "X"), "X")
        }
    }

    @Test
    fun `replace does not modify the original`() {
        val k = kordOf("abc")
        k.replace(0..1, "X")
        assertKordMatches(k, "abc")
    }

    @Test
    fun `replace starting between CR and LF replaces the whole pair and the rest of the range`() {
        // The start is nudged to before the CR, so 'b' (index 3) is still inside the replaced range.
        assertKordMatches(kordOf("a\r\nbc").replace(2..3, "X"), "aXc")
    }

    @Test
    fun `replace producing a CRLF across chunks counts it once`() {
        assertKordMatches(kordOf("a\rxb").replace(2..2, "\n"), "a\r\nb")
    }

    @Test
    fun `replace out of range throws`() {
        assertSoftly {
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").replace(-1..0, "x") }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").replace(0..3, "x") }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").replace(3..3, "x") }
            shouldThrow<IndexOutOfBoundsException> { kordOf().replace(0..0, "x") }
        }
    }

    @Test
    fun `random replaces match a StringBuilder model`() = runTest {
        checkAll(Arb.list(plainChunk, 1..10), editOps(plainChunk, 1..20)) { initial, ops ->
            checkAgainstModel(initial, ops)
        }
    }

    @Test
    fun `random replaces in CRLF-heavy text keep CRLF pairs together`() = runTest {
        checkAll(Arb.list(crlfChunk, 1..10), editOps(crlfChunk, 1..20)) { initial, ops ->
            checkAgainstModel(initial, ops)
        }
    }

    private fun checkAgainstModel(initial: List<String>, ops: List<Edit>) {
        val model = StringBuilder(initial.joinToString(""))
        var kord = kordOf(initial)

        ops.forEachIndexed { i, op ->
            val range = op.range(model.length)
            model.setRange(model.effectiveCut(range.first), model.effectiveCut(range.last + 1), op.text)
            kord = kord.replace(range, op.text)

            withClue("after op #$i replace($range, ${op.text.debug()})") { assertKordMatches(kord, model.toString()) }
        }
    }
}

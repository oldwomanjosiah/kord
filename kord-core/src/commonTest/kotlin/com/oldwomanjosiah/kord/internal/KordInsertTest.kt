package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.kordOf
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class KordInsertTest {
    @Test
    fun `insert at start middle and end`() {
        assertSoftly {
            assertKordMatches(kordOf("ab").insert(0, "X"), "Xab")
            assertKordMatches(kordOf("ab").insert(1, "X"), "aXb")
            assertKordMatches(kordOf("ab").insert(2, "X"), "abX")
        }
    }

    @Test
    fun `insert without a position appends`() {
        assertKordMatches(kordOf("ab").insert("cd"), "abcd")
        assertKordMatches(kordOf().insert("cd"), "cd")
    }

    @Test
    fun `insert into an empty kord`() {
        assertKordMatches(kordOf().insert(0, "abc"), "abc")
    }

    @Test
    fun `insert next to whitespace`() {
        assertSoftly {
            assertKordMatches(kordOf("a b").insert(1, "X"), "aX b")
            assertKordMatches(kordOf("a b").insert(2, "X"), "a Xb")
        }
    }

    @Test
    fun `insert between CR and LF lands before the CR`() {
        assertKordMatches(kordOf("a\r\nb").insert(2, "X"), "aX\r\nb")
        assertKordMatches(kordOf("a\r", "\nb").insert(2, "X"), "aX\r\nb")
    }

    @Test
    fun `insert of empty text is a no-op`() {
        val k = kordOf("abc")
        k.insert(1, "") shouldBe k
    }

    @Test
    fun `insert does not modify the original`() {
        val k = kordOf("abc")
        k.insert(1, "X")
        assertKordMatches(k, "abc")
    }

    @Test
    fun `insert out of range throws`() {
        assertSoftly {
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").insert(-1, "x") }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").insert(4, "x") }
            shouldThrow<IndexOutOfBoundsException> { kordOf().insert(1, "x") }
        }
    }

    @Test
    fun `random plain inserts match a StringBuilder model`() = runTest {
        checkAll(Arb.list(plainChunk, 0..5), editOps(plainChunk)) { initial, ops -> checkAgainstModel(initial, ops) }
    }

    @Test
    fun `random CRLF-heavy inserts match a StringBuilder model`() = runTest {
        checkAll(Arb.list(crlfChunk, 0..5), editOps(crlfChunk)) { initial, ops -> checkAgainstModel(initial, ops) }
    }

    private fun checkAgainstModel(initial: List<String>, ops: List<Edit>) {
        val model = StringBuilder(initial.joinToString(""))
        var kord = kordOf(initial)

        ops.forEachIndexed { i, op ->
            val at = op.position(model.length)
            model.insert(model.effectiveCut(at), op.text)
            kord = kord.insert(at, op.text)

            withClue("after op #$i insert($at, ${op.text.debug()})") { assertKordMatches(kord, model.toString()) }
        }
    }
}

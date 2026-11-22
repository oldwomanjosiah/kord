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

class KordCharSequenceTest {
    @Test
    fun `whole kord by default`() {
        val cs = kordOf("ab", "cd").charSequence()
        cs.length shouldBe 4
        cs.toString() shouldBe "abcd"
    }

    @Test
    fun `a range is indexed from its start`() {
        val cs = kordOf("ab", "cd", "ef").charSequence(1..4)
        assertSoftly {
            cs.length shouldBe 4
            cs[0] shouldBe 'b'
            cs[3] shouldBe 'e'
            cs.toString() shouldBe "bcde"
        }
    }

    @Test
    fun `subSequence is relative to the sequence`() {
        val cs = kordOf("abcdef").charSequence(1..4) // "bcde"
        assertSoftly {
            cs.subSequence(1, 3).toString() shouldBe "cd"
            cs.subSequence(0, 4).toString() shouldBe "bcde"
            cs.subSequence(2, 2).toString() shouldBe ""
            cs.subSequence(1, 3).subSequence(1, 2).toString() shouldBe "d"
        }
    }

    @Test
    fun `empty sequences`() {
        assertSoftly {
            kordOf().charSequence().length shouldBe 0
            kordOf().charSequence().toString() shouldBe ""
            kordOf("abc").charSequence(1 until 1).toString() shouldBe ""
            kordOf("abc").charSequence(3 until 3).length shouldBe 0
        }
    }

    @Test
    fun `out of range throws`() {
        val cs = kordOf("abcdef").charSequence(1..4)
        assertSoftly {
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").charSequence(-1..1) }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").charSequence(1..3) }
            shouldThrow<IndexOutOfBoundsException> { cs[-1] }
            shouldThrow<IndexOutOfBoundsException> { cs[4] }
            shouldThrow<IndexOutOfBoundsException> { cs.subSequence(-1, 2) }
            shouldThrow<IndexOutOfBoundsException> { cs.subSequence(0, 5) }
            shouldThrow<IndexOutOfBoundsException> { cs.subSequence(3, 2) }
        }
    }

    @Test
    fun `later edits do not show through`() {
        val k = kordOf("abc")
        val cs = k.charSequence()
        k.insert(1, "X")
        cs.toString() shouldBe "abc"
    }

    @Test
    fun `works with standard CharSequence functions`() {
        val cs = kordOf("foo ", "barbar", " baz").charSequence(4..9)
        Regex("(bar)+").matchEntire(cs)?.value shouldBe "barbar"
        cs.indexOf('r') shouldBe 2
    }

    @Test
    fun `every range matches substring`() = runTest {
        checkAll(Arb.list(crlfChunk, 1..5)) { parts ->
            val k = kordOf(parts)
            val text = parts.joinToString("")
            for (first in text.indices) for (last in first until text.length) {
                val cs = k.charSequence(first..last)
                val expected = text.substring(first, last + 1)
                withClue("charSequence($first..$last) of ${text.debug()}") {
                    cs.toString() shouldBe expected
                    cs.length shouldBe expected.length
                    for (i in expected.indices) cs[i] shouldBe expected[i]
                }
            }
        }
    }
}

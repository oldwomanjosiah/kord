package org.example

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test

/** Line-break counting on strings, and the newline counts cached in tree nodes. */
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
    fun `merged trees count CRLF pairs once`() = runTest {
        checkAll(Arb.list(crlfChunk, 0..12), seed) { chunks, sd ->
            val tree = build(chunks, Random(sd))
            withClue("chunks ${chunks.map { it.debug() }}") { tree.assertAggregates() }
        }
    }
}

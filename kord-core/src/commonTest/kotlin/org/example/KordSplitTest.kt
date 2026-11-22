package org.example

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** [KordImpl.split] is not on the public interface, so these go through a cast. */
class KordSplitTest {
    private fun implOf(parts: List<String>) = kordOf(parts) as KordImpl
    private fun implOf(vararg parts: String) = implOf(parts.toList())

    @Test
    fun `split yields prefix and suffix`() = runTest {
        checkAll(Arb.list(plainChunk, 0..12)) { parts ->
            val k = implOf(parts)
            val text = parts.joinToString("")
            for (at in 0..k.length) {
                val (l, r) = k.split(at)
                withClue("split($at) of ${parts.map { it.debug() }}") {
                    assertKordMatches(l, text.substring(0, at))
                    assertKordMatches(r, text.substring(at))
                }
            }
        }
    }

    @Test
    fun `split never separates CR from LF`() = runTest {
        checkAll(Arb.list(crlfChunk, 0..12)) { parts ->
            val k = implOf(parts)
            val text = parts.joinToString("")
            for (at in 0..k.length) {
                val (l, r) = k.split(at)
                val cut = text.effectiveCut(at)
                withClue("split($at) of ${parts.map { it.debug() }}") {
                    assertKordMatches(l, text.substring(0, cut))
                    assertKordMatches(r, text.substring(cut))
                }
            }
        }
    }

    @Test
    fun `split inside a CRLF across factory parts lands before the CR`() {
        val (l, r) = implOf("a\r", "\nb").split(2)
        assertKordMatches(l, "a")
        assertKordMatches(r, "\r\nb")
    }

    @Test
    fun `splitting an empty kord gives two empty kords`() {
        val (l, r) = implOf().split(0)
        assertKordMatches(l, "")
        assertKordMatches(r, "")
    }

    @Test
    fun `split out of range throws`() {
        assertSoftly {
            shouldThrow<IndexOutOfBoundsException> { implOf("abc").split(-1) }
            shouldThrow<IndexOutOfBoundsException> { implOf("abc").split(4) }
            shouldThrow<IndexOutOfBoundsException> { implOf().split(1) }
        }
    }
}

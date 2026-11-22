package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.kordOf
import io.kotest.assertions.assertSoftly
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** [com.oldwomanjosiah.kord.Kord.lines] through the public API, especially CRLF pairs formed or split by edits. */
class KordLineCountTest {
    @Test
    fun `line count is one more than the line breaks`() {
        assertSoftly {
            kordOf().lines shouldBe 1
            kordOf("abc").lines shouldBe 1
            kordOf("a\nb").lines shouldBe 2
            kordOf("a\n").lines shouldBe 2
            kordOf("a\r\nb\rc ").lines shouldBe 4
        }
    }

    @Test
    fun `CRLF split across factory parts counts once`() {
        kordOf("a\r", "\nb").lines shouldBe 2
        kordOf("a\r", "\n", "b\r", "\nc").lines shouldBe 3
    }

    @Test
    fun `inserting LF after a trailing CR does not add a line`() {
        assertKordMatches(kordOf("a\r").insert(2, "\n"), "a\r\n")
    }

    @Test
    fun `inserting CR before a leading LF does not add a line`() {
        assertKordMatches(kordOf("\nb").insert(0, "\r"), "\r\nb")
    }

    @Test
    fun `line count tracks random inserts`() = runTest {
        checkAll(Arb.list(crlfChunk, 0..5), editOps(crlfChunk)) { initial, ops ->
            var kord = kordOf(initial)
            for (op in ops) {
                kord = kord.insert(op.position(kord.length), op.text)
                kord.lines shouldBe kord.text().refNewlines() + 1
            }
        }
    }
}

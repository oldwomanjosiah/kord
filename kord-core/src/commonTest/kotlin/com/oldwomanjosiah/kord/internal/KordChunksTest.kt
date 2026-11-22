package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.Kord
import com.oldwomanjosiah.kord.kordOf
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class KordChunksTest {
    private fun Kord.chunkPairs(range: IntRange = 0 until length) = chunks(range).map { it.contents.toString() to it.contentsRange }

    @Test
    fun `chunks of the whole kord follow node boundaries`() {
        kordOfChunks("ab", "cd", "ef").chunkPairs() shouldBe listOf("ab" to 0..1, "cd" to 2..3, "ef" to 4..5)
    }

    @Test
    fun `chunks of a sub range are clipped with absolute ranges`() {
        assertSoftly {
            kordOfChunks("ab", "cd", "ef").chunkPairs(1..4) shouldBe listOf("b" to 1..1, "cd" to 2..3, "e" to 4..4)
            kordOfChunks("ab", "cd", "ef").chunkPairs(3..3) shouldBe listOf("d" to 3..3)
            kordOfChunks("ab", "cd", "ef").chunkPairs(4..5) shouldBe listOf("ef" to 4..5)
        }
    }

    @Test
    fun `chunks of an empty kord is empty`() {
        kordOf().chunks().toList().shouldBeEmpty()
    }

    @Test
    fun `chunks of an empty range is empty`() {
        assertSoftly {
            kordOf("abc").chunks(0 until 0).toList().shouldBeEmpty()
            kordOf("abc").chunks(2 until 2).toList().shouldBeEmpty()
            kordOf("abc").chunks(3 until 3).toList().shouldBeEmpty()
        }
    }

    @Test
    fun `chunks out of range throws`() {
        assertSoftly {
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").chunks(-1..1) }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").chunks(1..3) }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc").chunks(3..3) }
        }
    }

    @Test
    fun `chunks tile every range of an edited kord`() = runTest {
        checkAll(Arb.list(crlfChunk, 1..4), editOps(crlfChunk, 0..4)) { initial, ops ->
            var kord = kordOf(initial)
            for (op in ops) kord = kord.insert(op.position(kord.length), op.text)
            val text = kord.text()

            for (first in text.indices) for (last in first until text.length) {
                val chunks = kord.chunks(first..last).toList()
                withClue("chunks($first..$last) of ${text.debug()}") {
                    chunks.joinToString("") { it.contents } shouldBe text.substring(first, last + 1)
                    chunks.forEach { it.contents.isNotEmpty() shouldBe true }
                    chunks.first().contentsRange.first shouldBe first
                    chunks.last().contentsRange.last shouldBe last
                    chunks.zipWithNext().forEach { (a, b) -> b.contentsRange.first shouldBe a.contentsRange.last + 1 }
                    chunks.forEach { it.contentsRange.count() shouldBe it.contents.length }
                }
            }
        }
    }
}

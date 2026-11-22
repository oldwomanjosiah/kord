package com.oldwomanjosiah.kord.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test

/** In-order chunk iteration over a whole subtree. */
class KordNodeIteratorTest {
    @Test
    fun `iterates chunks in order with subtree-relative offsets`() {
        val root = node("cd", 0, left = node("b", -1, left = leaf("a", -2)), right = node("e", -1, right = leaf("fgh", -2)))

        root.map { it.contents } shouldBe listOf("a", "b", "cd", "e", "fgh")
        root.map { it.contentsRange } shouldBe listOf(0..0, 1..1, 2..3, 4..4, 5..7)
    }

    @Test
    fun `chunks cover the whole node contents`() {
        for (chunk in node("cd", 0, left = leaf("ab", -1))) {
            chunk.startIdx shouldBe 0
            chunk.endIdx shouldBe chunk.node.contents.length
        }
    }

    @Test
    fun `iteration matches the in-order chunks of any tree shape`() = runTest {
        checkAll(Arb.list(crlfChunk, 1..16), seed) { chunks, sd ->
            val tree = build(chunks, Random(sd))!!
            val iterated = tree.toList()

            withClue("chunks ${chunks.map { it.debug() }}") {
                iterated.map { it.contents } shouldBe chunks

                var offset = 0
                for (chunk in iterated) {
                    chunk.nodeCharOffset shouldBe offset
                    chunk.contentsRange shouldBe (offset until offset + chunk.contents.length)
                    offset += chunk.contents.length
                }
                offset shouldBe tree.length
            }
        }
    }

    @Test
    fun `an exhausted iterator throws NoSuchElementException`() {
        val iter = leaf("a", 0).iterator()
        iter.next()
        iter.hasNext() shouldBe false
        shouldThrow<NoSuchElementException> { iter.next() }
    }
}

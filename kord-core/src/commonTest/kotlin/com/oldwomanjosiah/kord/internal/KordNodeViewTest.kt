package com.oldwomanjosiah.kord.internal

import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test

/** Clipped chunk iteration over a range of a tree, reported with absolute offsets. */
class KordNodeViewTest {
    // "ab" "cd" "ef" with "cd" at the root
    private val root = node("cd", 0, left = leaf("ab", -1), right = leaf("ef", -1))

    private fun KordNode.viewOf(range: IntRange) = view(range).map { it.contents.toString() to it.contentsRange }

    @Test
    fun `view inside one chunk is clipped on both sides`() {
        root.viewOf(2..2) shouldBe listOf("c" to 2..2)
        root.viewOf(0..0) shouldBe listOf("a" to 0..0)
        root.viewOf(5..5) shouldBe listOf("f" to 5..5)
    }

    @Test
    fun `view spanning chunks clips only the outer ones`() {
        root.viewOf(1..4) shouldBe listOf("b" to 1..1, "cd" to 2..3, "e" to 4..4)
    }

    @Test
    fun `view of the whole tree yields every chunk unclipped`() {
        root.viewOf(0..5) shouldBe listOf("ab" to 0..1, "cd" to 2..3, "ef" to 4..5)
    }

    @Test
    fun `view on chunk boundaries yields whole chunks`() {
        root.viewOf(2..3) shouldBe listOf("cd" to 2..3)
        root.viewOf(4..5) shouldBe listOf("ef" to 4..5)
    }

    @Test
    fun `view entirely inside a deep right subtree reports absolute offsets`() {
        val deep = node("ab", 0, right = node("cd", -1, right = node("ef", -2, right = leaf("gh", -3))))
        deep.viewOf(6..7) shouldBe listOf("gh" to 6..7)
        deep.viewOf(5..6) shouldBe listOf("f" to 5..5, "g" to 6..6)
    }

    @Test
    fun `view entirely inside a deep left subtree reports absolute offsets`() {
        val deep = node("gh", 0, left = node("ef", -1, left = node("cd", -2, left = leaf("ab", -3))))
        deep.viewOf(1..2) shouldBe listOf("b" to 1..1, "c" to 2..2)
        deep.viewOf(4..5) shouldBe listOf("ef" to 4..5)
    }

    @Test
    fun `view matches substring for every range of any tree shape`() = runTest {
        checkAll(Arb.list(crlfChunk, 1..6), seed) { chunks, sd ->
            val tree = build(chunks, Random(sd))!!
            val text = chunks.joinToString("")

            for (first in text.indices) for (last in first until text.length) {
                val view = tree.view(first..last).toList()
                withClue("view($first..$last) of ${chunks.map { it.debug() }}") {
                    view.joinToString("") { it.contents } shouldBe text.substring(first, last + 1)
                    view.forEach { it.contents.isNotEmpty() shouldBe true }
                    view.first().contentsRange.first shouldBe first
                    view.last().contentsRange.last shouldBe last
                    view.zipWithNext().forEach { (a, b) -> b.contentsRange.first shouldBe a.contentsRange.last + 1 }
                    view.forEach { text.substring(it.contentsRange.first, it.contentsRange.last + 1) shouldBe it.contents.toString() }
                }
            }
        }
    }
}

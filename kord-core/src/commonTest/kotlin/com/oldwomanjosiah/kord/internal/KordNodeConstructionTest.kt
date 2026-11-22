package com.oldwomanjosiah.kord.internal

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import kotlin.test.Test

/** Invariants enforced and cached when a node is built. */
class KordNodeConstructionTest {
    @Test
    fun `empty chunks are rejected`() {
        shouldThrow<IllegalStateException> { leaf("", 0) }
        shouldThrow<IllegalStateException> { node("", 0, left = leaf("a", -1), right = leaf("b", -1)) }
    }

    @Test
    fun `indices of the node's own chunk account for the left subtree`() {
        val root = node("cd", 0, left = leaf("ab", -1), right = leaf("e", -1))
        root.firstIndex shouldBe 2
        root.endIndexExclusive shouldBe 4
        root.length shouldBe 5
    }

    @Test
    fun `first and last char come from the outermost chunks`() {
        val root = node("m", 0, left = node("b", -1, left = leaf("a", -2)), right = node("y", -1, right = leaf("z", -2)))
        root.firstChar shouldBe 'a'
        root.lastChar shouldBe 'z'
    }

    @Test
    fun `get indexes across subtrees`() {
        val root = node("cd", 0, left = leaf("ab", -1), right = leaf("ef", -1))
        "abcdef".forEachIndexed { i, c -> root[i] shouldBe c }
    }
}

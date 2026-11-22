package org.example

import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test

class KordNodeMergeTest {
    @Test
    fun `merge concatenates text and keeps the heap ordered`() = runTest {
        checkAll(Arb.list(plainChunk, 0..10), Arb.list(plainChunk, 0..10), seed) { a, b, sd ->
            val rng = Random(sd)
            val merged = KordNode.merge(build(a, rng), build(b, rng))

            merged.text() shouldBe (a + b).joinToString("")
            merged.chunkTexts() shouldBe a + b
            merged.isHeapOrdered().shouldBeTrue()
            merged.assertAggregates()
        }
    }

    @Test
    fun `merge with null is identity`() {
        val t = leaf("ab", 0)
        KordNode.merge(t, null) shouldBe t
        KordNode.merge(null, t) shouldBe t
        KordNode.merge(null, null) shouldBe null
    }
}

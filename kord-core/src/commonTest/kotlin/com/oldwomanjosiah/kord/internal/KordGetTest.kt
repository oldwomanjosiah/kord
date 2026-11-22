package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.kordOf
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class KordGetTest {
    @Test
    fun `get returns every char across parts`() = runTest {
        checkAll(Arb.list(plainChunk, 1..12)) { parts ->
            val k = kordOf(parts)
            val text = parts.joinToString("")
            for (i in text.indices) k[i] shouldBe text[i]
        }
    }

    @Test
    fun `get out of range throws`() {
        assertSoftly {
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc")[-1] }
            shouldThrow<IndexOutOfBoundsException> { kordOf("abc")[3] }
            shouldThrow<IndexOutOfBoundsException> { kordOf()[0] }
        }
    }
}

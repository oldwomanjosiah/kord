package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.kordOf
import io.kotest.assertions.assertSoftly
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

class KordFactoryTest {
    @Test
    fun `empty factory gives an empty kord`() {
        assertKordMatches(kordOf(), "")
    }

    @Test
    fun `factory concatenates its arguments`() {
        assertSoftly {
            assertKordMatches(kordOf("ab"), "ab")
            assertKordMatches(kordOf("ab", "cd", "e"), "abcde")
            assertKordMatches(kordOf("a\r", "\nb"), "a\r\nb")
        }
    }

    @Test
    fun `empty arguments are ignored`() {
        assertSoftly {
            assertKordMatches(kordOf(""), "")
            assertKordMatches(kordOf("", ""), "")
            assertKordMatches(kordOf("", "ab", ""), "ab")
        }
    }

    @Test
    fun `factory matches the concatenation of any parts`() = runTest {
        checkAll(Arb.list(chunkOf(crlfChars, 0..8), 0..20)) { parts ->
            assertKordMatches(kordOf(parts), parts.joinToString(""))
        }
    }
}

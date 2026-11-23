package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.kordOf
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

/** [NodeBuffer.View] equality is by content, and its hash agrees. */
class NodeBufferViewEqualityTest {
    @Test
    fun `views with the same text in different buffers are equal with equal hashes`() = runTest {
        checkAll(anyString) { s ->
            val a = view(s)
            val b = view(s)

            a shouldBe b
            a.hashCode() shouldBe b.hashCode()
        }
    }

    @Test
    fun `views at different offsets with the same text are equal`() {
        val whole = view("abcabc")
        val first = whole.subSequence(0, 3)
        val second = whole.subSequence(3, 6)

        first shouldBe second
        first.hashCode() shouldBe second.hashCode()
        first shouldBe view("abc")
    }

    @Test
    fun `views with different text are not equal`() {
        view("abc") shouldNotBe view("abd")
        view("abc") shouldNotBe view("ab")
        view("abcd").subSequence(0, 2) shouldNotBe view("abcd").subSequence(2, 4)
    }

    @Test
    fun `empty views are equal`() {
        val a = view("abc").subSequence(1, 1)
        val b = view("xyz").subSequence(3, 3)

        a shouldBe b
        a.hashCode() shouldBe b.hashCode()
    }

    @Test
    fun `a view never equals a String but contentEquals works`() {
        val v = view("abc")

        (v as Any).equals("abc").shouldBeFalse()
        v.contentEquals("abc").shouldBeTrue()
        "abc".contentEquals(v).shouldBeTrue()
    }

    @Test
    fun `growing a buffer does not change the equality or hash of existing views`() {
        val original = view("abc")
        val hash = original.hashCode()

        val grown = original.tryGrow("def")!!

        original shouldBe view("abc")
        original.hashCode() shouldBe hash
        grown shouldBe view("abcdef")
    }

    @Test
    fun `views work as set and map keys`() {
        val set = hashSetOf(view("abc"), view("xyz"))

        (view("abc") in set).shouldBeTrue()
        (view("abcabc").subSequence(3, 6) in set).shouldBeTrue()
        (view("abd") in set).shouldBeFalse()

        val map = hashMapOf(view("key") to 1)
        map[view("key")] shouldBe 1
    }

    @Test
    fun `chunk contents compare to strings with contentEquals`() {
        val chunk = kordOf("hello").chunks().single()

        chunk.contents.contentEquals("hello").shouldBeTrue()
        chunk.contents.toString() shouldBe "hello"
    }
}

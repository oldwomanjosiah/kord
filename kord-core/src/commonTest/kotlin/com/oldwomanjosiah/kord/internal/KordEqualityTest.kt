package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.Kord
import com.oldwomanjosiah.kord.KordConfig
import com.oldwomanjosiah.kord.kordOf
import io.kotest.assertions.withClue
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test

/** Content [equals] and [hashCode] for whole kords. */
class KordEqualityTest {
    private val tiny = KordConfig(maxChunkSize = 4, initialChunkSize = 1)
    private val small = KordConfig(maxChunkSize = 16, initialChunkSize = 2)

    /** Splits [s] into parts at cut points chosen by [rng], so the same text gets different chunks. */
    private fun partsOf(s: String, rng: Random): List<String> {
        val cuts = (0..s.length).filter { rng.nextInt(3) == 0 }.sorted()
        return (listOf(0) + cuts + s.length).distinct().sorted().zipWithNext { a, b -> s.substring(a, b) }
    }

    private fun kordIn(config: KordConfig, parts: List<String>, rng: Random): Kord =
        kordOf(*parts.toTypedArray(), config = config, randomProvider = rng::nextInt)

    private fun assertEqualKords(a: Kord, b: Kord) {
        a shouldBe b
        b shouldBe a
        a.hashCode() shouldBe b.hashCode()
    }

    @Test
    fun `same text is equal however it is chunked`() = runTest {
        checkAll(chunkOf(crlfChars, 0..60), seed) { s, sd ->
            val rng = Random(sd)
            val a = kordIn(tiny, partsOf(s, rng), rng)
            val b = kordIn(small, partsOf(s, rng), rng)
            val c = kordOf(s)

            assertEqualKords(a, b)
            assertEqualKords(a, c)
        }
    }

    @Test
    fun `different text is not equal`() = runTest {
        checkAll(chunkOf(plainChars, 0..30), chunkOf(plainChars, 0..30), seed) { s, t, sd ->
            val rng = Random(sd)
            val a = kordIn(tiny, partsOf(s, rng), rng)
            val b = kordIn(tiny, partsOf(t, rng), rng)

            (a == b) shouldBe (s == t)
            (b == a) shouldBe (s == t)
        }
    }

    @Test
    fun `texts that differ only in their last char are not equal`() {
        val a = kordIn(tiny, listOf("abcdefgh", "ij"), Random(1))
        val b = kordIn(tiny, listOf("abcdefg", "hik"), Random(2))

        a shouldNotBe b
        b shouldNotBe a
    }

    @Test
    fun `a prefix is not equal`() {
        kordOf("abc") shouldNotBe kordOf("abcd")
        kordOf("abcd") shouldNotBe kordOf("abc")
    }

    @Test
    fun `empty kords are equal and hash to zero`() {
        assertEqualKords(kordOf(), kordOf(""))
        kordOf().hashCode() shouldBe 0
        kordOf("a").remove(0..0) shouldBe kordOf()
        kordOf() shouldNotBe kordOf("a")
    }

    @Test
    fun `a kord never equals a String or other CharSequence`() {
        val kord = kordOf("abc")

        (kord as Any).equals("abc").shouldBeFalse()
        (kord as Any).equals(StringBuilder("abc")).shouldBeFalse()
        (kord as Any).equals(kord.charSequence()).shouldBeFalse()
    }

    @Test
    fun `hash uses the String hash formula`() = runTest {
        // Documented formula: 31 * hash + char, over every char, starting from 0.
        checkAll(chunkOf(crlfChars, 0..60), seed) { s, sd ->
            val rng = Random(sd)
            val expected = s.fold(0) { h, c -> 31 * h + c.code }

            kordIn(tiny, partsOf(s, rng), rng).hashCode() shouldBe expected
        }
    }

    @Test
    fun `hash is stable across calls`() {
        val kord = kordOf("the quick brown fox")
        val first = kord.hashCode()

        kord.hashCode() shouldBe first
        kord.insert("!").hashCode() shouldNotBe first
        kord.hashCode() shouldBe first
    }

    @Test
    fun `an edit and its undo give an equal kord`() {
        val base = kordIn(tiny, listOf("hello world"), Random(3))
        val edited = base.insert(5, ",").remove(5..5)

        assertEqualKords(base, edited)
        base shouldNotBe base.insert(5, ",")
    }

    /**
     * Versions of one kord share buffers. Equality skips chars that both read from the same buffer
     * slot, so check that any two versions are equal exactly when their texts are.
     */
    @Test
    fun `versions of a branching history are equal exactly when their text is`() = runTest {
        val steps = Arb.list(
            Arb.bind(Arb.int(0..Int.MAX_VALUE), Arb.int(0..Int.MAX_VALUE), Arb.int(0..Int.MAX_VALUE), chunkOf(Arb.element('a', 'b'), 0..3)) { pick, pos, kind, text ->
                listOf(pick, pos, kind) to text
            },
            1..25,
        )

        checkAll(steps, seed) { ops, sd ->
            val rng = Random(sd)
            val history = mutableListOf(kordIn(tiny, listOf("abab"), rng) to "abab")

            for ((nums, text) in ops) {
                val (pick, pos, kind) = nums
                val (kord, s) = history[pick % history.size]

                history += if (kind % 2 == 0 || s.isEmpty()) {
                    val at = pos % (s.length + 1)
                    kord.insert(at, text) to s.substring(0, at) + text + s.substring(at)
                } else {
                    val at = pos % s.length
                    kord.remove(at..at) to s.removeRange(at, at + 1)
                }
            }

            for ((i, x) in history.withIndex()) for ((j, y) in history.withIndex()) {
                withClue("versions $i ${x.second.debug()} and $j ${y.second.debug()}") {
                    (x.first == y.first) shouldBe (x.second == y.second)
                    if (x.second == y.second) x.first.hashCode() shouldBe y.first.hashCode()
                }
            }
        }
    }

    @Test
    fun `kords work as set and map keys`() {
        val set = hashSetOf(kordOf("abc"), kordOf("xyz"))

        (kordIn(tiny, listOf("a", "bc"), Random(4)) in set).shouldBeTrue()
        (kordOf("abd") in set).shouldBeFalse()

        val map = hashMapOf(kordOf("key") to 1)
        map[kordOf("k", "e", "y")] shouldBe 1
    }
}

package com.oldwomanjosiah.kord

import kotlin.random.Random

/**
 * Create a new `Kord` with the concatenated values provided.
 *
 * This overload uses a standard [Random] instance, seeded with the global instance of itself. This is not
 * secure against DOS attacks, so it should not be used with untrusted input.
 */
fun kordOf(vararg of: String): Kord {
    val seededRandom = Random(Random.nextLong())

    return kordOf(
        of = of,
        randomProvider = seededRandom::nextInt,
    )
}

/**
 * Create a new `Kord` with the concatenated values provided.
 *
 * This overload is provided so that the randomness provider can be configured. When processing untrusted input,
 * the chosen source should be seeded using a cryptographic RNG implementation.
 *
 * @param randomProvider new random integer per-call
 */
fun kordOf(
    vararg of: String,
    randomProvider: () -> Int,
): Kord = of.fold(KordImpl(randomProvider = randomProvider)) { impl, item -> impl.insert(item) }
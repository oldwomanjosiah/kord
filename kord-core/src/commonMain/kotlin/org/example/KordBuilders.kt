package org.example

import kotlin.random.Random

fun kordOf(vararg of: String): Kord {
    val seededRandom = Random(Random.nextLong())

    return kordOf(
        of = of,
        randomProvider = seededRandom::nextInt,
    )
}

fun kordOf(
    vararg of: String,
    randomProvider: () -> Int,
): Kord = of.fold(KordImpl(randomProvider = randomProvider)) { impl, item -> impl.insert(item) }
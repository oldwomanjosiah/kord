package org.example

import kotlin.random.Random

fun kordOf(vararg of: String): Kord {
    val seededRandom = Random(Random.nextLong())

    return of.fold(KordImpl { seededRandom.nextInt() }) { impl, item -> impl.insert(impl.length, item) }
}
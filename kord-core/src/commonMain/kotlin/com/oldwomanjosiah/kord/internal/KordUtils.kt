package com.oldwomanjosiah.kord.internal

import com.oldwomanjosiah.kord.Kord

/** Whole chunk contents in order, without the per-chunk sub-views that [chunks] makes. */
internal fun Kord.chunkContents(): Iterator<CharSequence> = when (this) {
    is KordImpl -> root?.iterator()?.let { chunks -> chunks.asSequence().map { it.node.contents }.iterator() }
        ?: emptyList<CharSequence>().iterator()

    else -> chunks().asSequence().map { it.contents }.iterator()
}

/** Walks both chunk sequences in step, comparing char runs however the chunk boundaries fall. */
internal fun chunksEqual(a: Iterator<CharSequence>, b: Iterator<CharSequence>): Boolean {
    var ca: CharSequence = ""
    var ia = 0
    var cb: CharSequence = ""
    var ib = 0

    while (true) {
        while (ia == ca.length && a.hasNext()) { ca = a.next(); ia = 0 }
        while (ib == cb.length && b.hasNext()) { cb = b.next(); ib = 0 }

        val aDone = ia == ca.length
        val bDone = ib == cb.length
        if (aDone || bDone) return aDone && bDone

        val run = minOf(ca.length - ia, cb.length - ib)

        val matches = when {
            ca is NodeBuffer.View && cb is NodeBuffer.View ->
                ca.sharesStorageAt(ia, cb, ib) || ca.regionMatches(ia, cb, ib, run)

            else -> (0 until run).all { k -> ca[ia + k] == cb[ib + k] }
        }

        if (!matches) return false

        ia += run
        ib += run
    }
}
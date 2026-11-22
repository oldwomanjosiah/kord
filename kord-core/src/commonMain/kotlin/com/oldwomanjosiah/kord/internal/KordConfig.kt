package com.oldwomanjosiah.kord.internal

data class KordConfig(
    val maxChunkSize: Int,
) {
    companion object {
        val Default = KordConfig(
            maxChunkSize = 1024,
        )
    }
}

package com.oldwomanjosiah.kord.internal

data class KordConfig(
    val maxChunkSize: Int,
    val initialChunkSize: Int,
) {
    init {
        require(initialChunkSize <= maxChunkSize) { "initialChunkSize must be less than or equal to maxChunkSize" }

        require(maxChunkSize.countOneBits() == 1) { "maxChunkSize must be a power of 2" }
        require(initialChunkSize.countOneBits() == 1) { "initialChunkSize must be a power of 2" }
    }

    companion object {
        val Default = KordConfig(
            maxChunkSize = 1024,
            initialChunkSize = 32,
        )
    }
}

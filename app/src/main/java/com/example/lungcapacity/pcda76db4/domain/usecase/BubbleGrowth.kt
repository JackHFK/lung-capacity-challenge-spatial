package com.example.lungcapacity.pcda76db4.domain.usecase

internal object BubbleGrowth {
    const val CHARGE_SECONDS = 0.6f
    const val CHILD_ENERGY = 0.175f
    const val MICRO_ENERGY = 0.1f
    const val WAVE_ENERGY = 0.06f

    fun chargeMultiplier(seconds: Float, rank: Int): Float =
        if (seconds >= CHARGE_SECONDS) 1f + rank * 0.03f else 1f

    fun offspringCount(generation: Int, firstSplit: Boolean, secondBloom: Boolean): Int =
        when {
            generation == 0 && firstSplit -> 2
            generation == 1 && secondBloom -> 3
            else -> 0
        }

    fun offspringEnergy(energy: Float, generation: Int): Float =
        energy * if (generation == 0) CHILD_ENERGY else MICRO_ENERGY
}

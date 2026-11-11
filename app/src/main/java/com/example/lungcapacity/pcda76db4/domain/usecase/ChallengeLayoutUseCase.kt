package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.CandlePlacement
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.domain.model.cakeTiers
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class ChallengeLayoutUseCase {
    operator fun invoke(difficulty: Difficulty): List<CandlePlacement> {
        return difficulty.cakeTiers.flatMap { tier ->
            val radius = when (difficulty) {
                Difficulty.EASY -> 0.51f
                else -> if (tier.nextRadius > 0f) {
                    (tier.radius + tier.nextRadius) / 2f
                } else {
                    tier.radius * 0.70f
                }
            }
            List(CANDLES_PER_TIER) { slot ->
                val angle = (slot + 0.5f + tier.index * 0.25f) *
                    2f * PI.toFloat() / CANDLES_PER_TIER
                CandlePlacement(
                    index = tier.index * CANDLES_PER_TIER + slot,
                    tierIndex = tier.index,
                    x = sin(angle) * radius,
                    y = tier.surfaceY - CANDLE_INSERTION_DEPTH,
                    z = cos(angle) * radius,
                    angleDegrees = angle * 180f / PI.toFloat(),
                )
            }
        }
    }

    private companion object {
        const val CANDLES_PER_TIER = 8
        const val CANDLE_INSERTION_DEPTH = 0.045f
    }
}

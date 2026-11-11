package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import kotlin.math.pow
import kotlin.math.roundToInt

class CalculateRewardUseCase {
    operator fun invoke(
        difficulty: Difficulty,
        elapsedSeconds: Float,
    ): Int {
        val rules = when (difficulty) {
            Difficulty.EASY -> RewardRules(20f, 2, 12, 24)
            Difficulty.NORMAL -> RewardRules(40f, 3, 24, 48)
            Difficulty.HARD -> RewardRules(60f, 5, 40, 80)
        }
        val timeRatio = (rules.referenceTime / elapsedSeconds.coerceAtLeast(0.1f))
            .coerceIn(0.25f, 1.75f)
        val timeReward = (rules.referenceReward * timeRatio.pow(1.6f))
            .roundToInt()
            .coerceAtMost(rules.rewardCap)
        return rules.guaranteedCoins + timeReward
    }

    private data class RewardRules(
        val referenceTime: Float,
        val guaranteedCoins: Int,
        val referenceReward: Int,
        val rewardCap: Int,
    )
}

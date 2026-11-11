package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Test

class CalculateRewardUseCaseTest {
    private val calculate = CalculateRewardUseCase()

    @Test
    fun normalReferenceTimeMatchesWebReward() {
        assertEquals(
            27,
            calculate(Difficulty.NORMAL, 40f),
        )
    }

    @Test
    fun hardReferenceTimeUsesDifficultyReward() {
        assertEquals(
            45,
            calculate(Difficulty.HARD, 60f),
        )
    }

    @Test
    fun rewardIsCappedForNearZeroCompletionTime() {
        assertEquals(
            85,
            calculate(Difficulty.HARD, 0.1f),
        )
    }
}

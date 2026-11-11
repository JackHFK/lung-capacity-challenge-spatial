package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class ChallengeLayoutUseCaseTest {
    private val useCase = ChallengeLayoutUseCase()

    @Test
    fun everyDifficultyPlacesEightCandlesPerTier() {
        Difficulty.entries.forEach { difficulty ->
            val placements = useCase(difficulty)
            assertEquals(difficulty.candleCount, placements.size)
            placements.groupBy { it.tierIndex }.values.forEach { tier ->
                assertEquals(8, tier.size)
            }
        }
    }

    @Test
    fun hardLayoutUsesThreeDistinctTerraces() {
        val byTier = useCase(Difficulty.HARD).groupBy { it.tierIndex }

        assertEquals(setOf(0, 1, 2), byTier.keys)
        assertEquals(0.85f, byTier.getValue(0).first().radius(), 0.0001f)
        assertEquals(0.575f, byTier.getValue(1).first().radius(), 0.0001f)
        assertEquals(0.315f, byTier.getValue(2).first().radius(), 0.0001f)
        assertTrue(byTier.getValue(0).first().y < byTier.getValue(2).first().y)
    }

    private fun com.example.lungcapacity.pcda76db4.domain.model.CandlePlacement.radius(): Float =
        sqrt(x * x + z * z)
}

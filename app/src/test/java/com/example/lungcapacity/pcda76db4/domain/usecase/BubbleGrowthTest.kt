package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.GrowthCatalog
import com.example.lungcapacity.pcda76db4.domain.model.GrowthNodeId
import org.junit.Assert.*
import org.junit.Test

class BubbleGrowthTest {
    @Test
    fun chargeRequiresContinuousStableInterval() {
        assertEquals(1f, BubbleGrowth.chargeMultiplier(0.59f, 4), 0f)
        assertEquals(1.12f, BubbleGrowth.chargeMultiplier(0.6f, 4), 0.00001f)
        assertEquals(1f, BubbleGrowth.chargeMultiplier(0f, 4), 0f)
        assertEquals(1f, BubbleGrowth.chargeMultiplier(2f, 0), 0f)
    }

    @Test
    fun purchasedAbilitiesLimitDescendantsToThreeGenerations() {
        assertEquals(0, BubbleGrowth.offspringCount(0, false, false))
        assertEquals(2, BubbleGrowth.offspringCount(0, true, false))
        assertEquals(0, BubbleGrowth.offspringCount(1, true, false))
        assertEquals(3, BubbleGrowth.offspringCount(1, true, true))
        assertEquals(0, BubbleGrowth.offspringCount(2, true, true))
    }

    @Test
    fun fullGrowthEnergyIncludingNonRecursiveWavesRemainsBelowBudget() {
        val parentEnergy = 1f
        val childEnergy = BubbleGrowth.offspringEnergy(parentEnergy, 0)
        val microEnergy = BubbleGrowth.offspringEnergy(childEnergy, 1)
        val familyEnergy = parentEnergy + 2 * childEnergy + 6 * microEnergy
        val density = 1f + GrowthCatalog.spec(GrowthNodeId.DENSITY).maxRank * 0.04f
        val charge = BubbleGrowth.chargeMultiplier(1f, GrowthCatalog.spec(GrowthNodeId.CHARGE).maxRank)
        val fullGrowth = density * charge * familyEnergy * (1f + BubbleGrowth.WAVE_ENERGY)
        assertEquals(1.455f, familyEnergy, 0.00001f)
        assertEquals(2.072851f, fullGrowth, 0.00001f)
        assertTrue(fullGrowth <= 2.2f)
    }
}

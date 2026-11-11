package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import com.example.lungcapacity.pcda76db4.domain.model.GrowthCatalog
import com.example.lungcapacity.pcda76db4.domain.model.GrowthNodeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpgradeGrowthUseCaseTest {
    private val useCase = UpgradeGrowthUseCase()

    @Test
    fun densityCanUpgradeWithoutPrerequisite() {
        val snapshot = GameSnapshot(coins = 15, selectedGrowthNodeId = GrowthNodeId.DENSITY)

        val result = useCase(snapshot)

        assertTrue(result.upgraded)
        assertEquals(1, result.snapshot.growthRanks[GrowthNodeId.DENSITY])
        assertEquals(0, result.snapshot.coins)
    }

    @Test
    fun spreadRequiresDensityRankOne() {
        val snapshot = GameSnapshot(selectedGrowthNodeId = GrowthNodeId.SPREAD)

        val result = useCase(snapshot)

        assertFalse(result.upgraded)
        assertEquals(0, result.snapshot.coins)
        assertEquals(0, result.snapshot.growthRanks[GrowthNodeId.SPREAD])
        assertTrue(result.message.contains("密度 1 级"))
    }

    @Test
    fun canonicalGraphHasNoSelfDependency() {
        GrowthCatalog.specs.forEach { spec ->
            assertFalse(spec.prerequisites.any { it.nodeId == spec.id })
        }
        assertTrue(GrowthCatalog.spec(GrowthNodeId.DENSITY).prerequisites.isEmpty())
        assertEquals(
            listOf(GrowthNodeId.DENSITY),
            GrowthCatalog.spec(GrowthNodeId.SPREAD).prerequisites.map { it.nodeId },
        )
    }
}

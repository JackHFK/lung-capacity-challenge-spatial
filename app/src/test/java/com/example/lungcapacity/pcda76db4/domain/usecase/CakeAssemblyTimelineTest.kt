package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CakeAssemblyTimelineTest {
    @Test
    fun exportedDifficultyConfigurationsDefineTheAssemblyBoundaries() {
        assertEquals(2.064f, CakeAssemblyTimeline.assemblySeconds(Difficulty.EASY), 0.0001f)
        assertEquals(2.738f, CakeAssemblyTimeline.assemblySeconds(Difficulty.NORMAL), 0.0001f)
        assertEquals(3.25f, CakeAssemblyTimeline.assemblySeconds(Difficulty.HARD), 0.0001f)
    }

    @Test
    fun hardTierBodiesUseTheirConfiguredStartsAndDurationScale() {
        val before = CakeAssemblyTimeline.part(
            elapsedSeconds = 0.499f,
            difficulty = Difficulty.HARD,
            tierIndex = 1,
            kind = CakeAssemblyPartKind.LARGE_BODY,
            sequence = 0,
            travel = 1f,
        )
        val halfway = CakeAssemblyTimeline.part(
            elapsedSeconds = 0.75f,
            difficulty = Difficulty.HARD,
            tierIndex = 1,
            kind = CakeAssemblyPartKind.LARGE_BODY,
            sequence = 0,
            travel = 1f,
        )
        val settled = CakeAssemblyTimeline.part(
            elapsedSeconds = 1f,
            difficulty = Difficulty.HARD,
            tierIndex = 1,
            kind = CakeAssemblyPartKind.LARGE_BODY,
            sequence = 0,
            travel = 1f,
        )

        assertFalse(before.visible)
        assertTrue(halfway.visible)
        assertEquals(0.875f, halfway.progress, 0.0001f)
        assertEquals(0.125f, halfway.verticalOffset, 0.0001f)
        assertEquals(1f, settled.progress, 0f)
    }

    @Test
    fun normalSmallPartsUsePerTierDurationScales() {
        val firstTierHalfway = CakeAssemblyTimeline.part(
            elapsedSeconds = 1.115f,
            difficulty = Difficulty.NORMAL,
            tierIndex = 0,
            kind = CakeAssemblyPartKind.SMALL,
            sequence = 0,
            travel = 1f,
        )
        val secondTierHalfway = CakeAssemblyTimeline.part(
            elapsedSeconds = 1.52f,
            difficulty = Difficulty.NORMAL,
            tierIndex = 1,
            kind = CakeAssemblyPartKind.SMALL,
            sequence = 0,
            travel = 1f,
        )

        assertEquals(0.875f, firstTierHalfway.progress, 0.0001f)
        assertEquals(0.875f, secondTierHalfway.progress, 0.0001f)
    }

    @Test
    fun smallPartsUseTheConfiguredWithinTierStagger() {
        val beforeSecondPart = CakeAssemblyTimeline.part(
            elapsedSeconds = 1.229f,
            difficulty = Difficulty.HARD,
            tierIndex = 1,
            kind = CakeAssemblyPartKind.SMALL,
            sequence = 1,
            travel = 1f,
        )
        val secondPartStart = CakeAssemblyTimeline.part(
            elapsedSeconds = 1.2301f,
            difficulty = Difficulty.HARD,
            tierIndex = 1,
            kind = CakeAssemblyPartKind.SMALL,
            sequence = 1,
            travel = 1f,
        )

        assertFalse(beforeSecondPart.visible)
        assertTrue(secondPartStart.visible)
        assertTrue(secondPartStart.progress < 0.001f)
    }

    @Test
    fun candleTiersUseConfiguredStartsDurationAndSlotStagger() {
        val firstTierStart = CakeAssemblyTimeline.candle(
            elapsedSeconds = 1.2001f,
            difficulty = Difficulty.HARD,
            tierIndex = 0,
            slotIndex = 0,
        )
        val secondTierStart = CakeAssemblyTimeline.candle(
            elapsedSeconds = 1.7001f,
            difficulty = Difficulty.HARD,
            tierIndex = 1,
            slotIndex = 0,
        )
        val thirdTierStart = CakeAssemblyTimeline.candle(
            elapsedSeconds = 2.2001f,
            difficulty = Difficulty.HARD,
            tierIndex = 2,
            slotIndex = 0,
        )
        val secondSlotStart = CakeAssemblyTimeline.candle(
            elapsedSeconds = 1.2501f,
            difficulty = Difficulty.HARD,
            tierIndex = 0,
            slotIndex = 1,
        )
        val firstCandleSettled = CakeAssemblyTimeline.candle(
            elapsedSeconds = 1.9001f,
            difficulty = Difficulty.HARD,
            tierIndex = 0,
            slotIndex = 0,
        )

        assertTrue(firstTierStart.visible)
        assertTrue(secondTierStart.visible)
        assertTrue(thirdTierStart.visible)
        assertTrue(secondSlotStart.visible)
        assertTrue(secondSlotStart.progress < 0.001f)
        assertTrue(firstCandleSettled.inserted)
    }

    @Test
    fun finalCandleCompletesAtTheAssemblyBoundary() {
        val total = CakeAssemblyTimeline.assemblySeconds(Difficulty.HARD)
        val before = CakeAssemblyTimeline.candle(
            elapsedSeconds = total - 0.001f,
            difficulty = Difficulty.HARD,
            tierIndex = 2,
            slotIndex = 7,
        )
        val complete = CakeAssemblyTimeline.candle(
            elapsedSeconds = total,
            difficulty = Difficulty.HARD,
            tierIndex = 2,
            slotIndex = 7,
        )

        assertFalse(before.inserted)
        assertTrue(complete.inserted)
    }

    @Test
    fun zeroTravelSupportStillUsesTheHalfUnitFallback() {
        val initial = CakeAssemblyTimeline.part(
            elapsedSeconds = 0f,
            difficulty = Difficulty.EASY,
            tierIndex = 0,
            kind = CakeAssemblyPartKind.SUPPORT,
            sequence = 0,
            travel = 0f,
        )
        val settled = CakeAssemblyTimeline.part(
            elapsedSeconds = 0.5101f,
            difficulty = Difficulty.EASY,
            tierIndex = 0,
            kind = CakeAssemblyPartKind.SUPPORT,
            sequence = 0,
            travel = 0f,
        )

        assertTrue(initial.visible)
        assertEquals(0.5f, initial.verticalOffset, 0f)
        assertEquals(0f, settled.verticalOffset, 0f)
    }
}

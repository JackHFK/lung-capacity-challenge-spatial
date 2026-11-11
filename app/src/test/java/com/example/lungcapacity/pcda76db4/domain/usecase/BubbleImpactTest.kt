package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.BubbleResponse
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import org.junit.Assert.*
import org.junit.Test

class BubbleImpactTest {
    private val center = Point3(0f, 1.25f, -1.15f)
    private val flame = Point3(0.0225f, 0.0475f, 0.019f)
    private val radius = Point3(0.035f, 0.035f, 0.035f)

    @Test
    fun boardSweepBlocksTunnelingAndAllowsSideAndUpperOpenings() {
        val board = Point3(0.17f, 0.16f, 0.015f)
        val center = Point3(0f, 1.12f, -0.66f)
        assertNotNull(BubbleImpact.boxFraction(
            Point3(0f, 1.12f, -0.4f), Point3(0f, 1.12f, -0.9f), center, board, radius,
        ))
        assertNull(BubbleImpact.boxFraction(
            Point3(0.3f, 1.12f, -0.4f), Point3(0.3f, 1.12f, -0.9f), center, board, radius,
        ))
        assertNull(BubbleImpact.boxFraction(
            Point3(0f, 1.4f, -0.4f), Point3(0f, 1.4f, -0.9f), center, board, radius,
        ))
        assertNull(BubbleImpact.boxFraction(
            Point3(0f, 1.12f, -0.9f), Point3(0f, 1.12f, -1f), center, board, radius,
        ))
    }

    @Test
    fun stationaryOverlappingBoardAndFastRingCrossingAreDetected() {
        assertEquals(0f, BubbleImpact.boxFraction(center, center, center, flame, radius)!!, 0f)
        assertEquals(0.5f, BubbleImpact.diskFraction(
            Point3(0f, 1.25f, -1f), Point3(0f, 1.25f, -1.3f), center, 0.18f,
        )!!, 0.0001f)
        assertNull(BubbleImpact.diskFraction(
            Point3(0.3f, 1.25f, -1f), Point3(0.3f, 1.25f, -1.3f), center, 0.18f,
        ))
        assertNull(BubbleImpact.diskFraction(
            Point3(0f, 1.25f, -1.3f), Point3(0f, 1.25f, -1f), center, 0.18f,
        ))
    }

    @Test
    fun sweptHitDetectsFlameBetweenFrameEndpoints() {
        val t = BubbleImpact.fraction(
            Point3(0f, 1.25f, -1f), Point3(0f, 1.25f, -1.3f), center, flame, radius,
        )
        assertNotNull(t)
        assertTrue(t!! in 0.2f..0.5f)
    }

    @Test
    fun nearbyAndBehindFlamesAreNotHits() {
        assertNull(BubbleImpact.fraction(
            Point3(0.13f, 1.25f, -1f), Point3(0.13f, 1.25f, -1.3f), center, flame, radius,
        ))
        assertNull(BubbleImpact.fraction(
            Point3(0f, 1.25f, -1.3f), Point3(0f, 1.25f, -1.5f), center, flame, radius,
        ))
    }

    @Test
    fun actualBubbleSizeControlsEdgeContact() {
        val start = Point3(0.06f, 1.25f, -1f)
        val end = Point3(0.06f, 1.25f, -1.3f)
        assertNull(BubbleImpact.fraction(start, end, center, flame, Point3(0.01f, 0.01f, 0.01f)))
        assertNotNull(BubbleImpact.fraction(start, end, center, flame, Point3(0.065f, 0.065f, 0.065f)))
    }

    @Test
    fun initiallyOverlappingAndStationaryBubbleIsAHit() {
        assertEquals(0f, BubbleImpact.fraction(center, center, center, flame, radius)!!, 0f)
    }

    @Test
    fun sustainedGentleHitsExtinguishAtBothFrameRates() {
        for (fps in listOf(30, 90)) {
            val response = FlameResponse(Difficulty.HARD.flameThreshold)
            val intensity = 0.15f
            val emission = BubbleResponse.fromIntensity(intensity).particlesPerSecond
            val energy = 0.25f * 7.5f * intensity / emission
            var emitted = 0f
            repeat(fps * 4) {
                response.update(1f / fps)
                emitted += emission / fps
                while (emitted >= 1f) {
                    response.hit(energy)
                    emitted -= 1f
                }
            }
            assertTrue("gentle stream did not extinguish at $fps fps", response.extinguished)
        }
    }

    @Test
    fun hitIsVisibleImmediatelyAndExtinctionPublishesOnlyOnce() {
        val response = FlameResponse(Difficulty.HARD.flameThreshold)
        assertFalse(response.hit(0.1f))
        assertEquals(1f, response.pulse, 0f)
        assertEquals(0.1f, response.exposure, 0f)
        response.update(0.3f)
        assertEquals(0.1f, response.exposure, 0.0001f)
        assertTrue(response.hit(0.8f))
        assertFalse(response.hit(0.8f))
        response.update(5f)
        assertTrue(response.extinguished)
        response.reset()
        assertFalse(response.extinguished)
        assertEquals(0f, response.exposure, 0f)
    }

    @Test
    fun candleEnduranceRequiresFourSixAndEightEqualHits() {
        for ((difficulty, hits) in listOf(Difficulty.EASY to 4, Difficulty.NORMAL to 6, Difficulty.HARD to 8)) {
            val response = FlameResponse(difficulty.flameThreshold)
            repeat(hits - 1) {
                assertFalse("$difficulty extinguished before hit $hits", response.hit(0.1f))
                response.update(1f / 36f)
            }
            assertTrue("$difficulty still lit after hit $hits", response.hit(0.1f))
            assertFalse(response.hit(0.1f))
            response.reset()
            assertFalse(response.extinguished)
            assertEquals(0f, response.exposure, 0f)
        }
    }
}

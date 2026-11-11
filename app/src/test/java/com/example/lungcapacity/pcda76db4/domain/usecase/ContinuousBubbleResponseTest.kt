package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.BubbleResponse
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousBubbleResponseTest {
    private fun processor() = BreathSignalProcessor()

    @Test
    fun gentleBreathProducesTheFirstBubbleWithinOneAudioSample() {
        val intensity = processor().process(0.005f)
        val emitted = BreathEmissionGate().update(
            1f / 90f, ChallengePhase.RUNNING, intensity, 0, false,
        )
        assertTrue(intensity > BubbleResponse.MIN_ACTIVE_INTENSITY)
        assertEquals(1, emitted)
    }

    @Test
    fun steadyRoomNoiseNeverEmitsAcrossManyFrames() {
        val processor = processor()
        val gate = BreathEmissionGate()
        val emitted = (0 until 500).sumOf {
            val noise = if (it % 2 == 0) 0.0038f else 0.004f
            gate.update(0.02f, ChallengePhase.RUNNING, processor.process(noise), 0, false)
        }
        assertEquals(0, emitted)
    }

    @Test
    fun softMediumAndLoudBreathsGrowInSizeSpeedAndDensity() {
        val levels = listOf(0.0045f, 0.0055f, 0.007f).map { rms ->
            val intensity = processor().process(rms)
            BubbleResponse.fromIntensity(intensity).also {
                println("rms=$rms intensity=$intensity response=$it")
            }
        }
        assertTrue(levels.first().particlesPerSecond >= 16f)
        levels.zipWithNext().forEach { (soft, loud) ->
            assertTrue(loud.particlesPerSecond > soft.particlesPerSecond)
            assertTrue(loud.diameterMeters > soft.diameterMeters)
            assertTrue(loud.speedMetersPerSecond > soft.speedMetersPerSecond)
        }
    }

    @Test
    fun emissionIsIndependentOfFrameRate() {
        fun count(frameRate: Int): Int {
            val gate = BreathEmissionGate()
            return (0 until frameRate * 4).sumOf {
                gate.update(1f / frameRate, ChallengePhase.RUNNING, 0.4f, 0, false)
            }
        }
        assertTrue(kotlin.math.abs(count(60) - count(90)) <= 1)
    }

    @Test
    fun silenceImmediatelyStopsEmissionAfterARealBreath() {
        val processor = processor()
        val gate = BreathEmissionGate()
        repeat(30) { gate.update(0.02f, ChallengePhase.RUNNING, processor.process(0.007f), 0, false) }
        val emitted = (0 until 100).sumOf {
            gate.update(0.02f, ChallengePhase.RUNNING, processor.process(0f), 0, false)
        }
        assertEquals(0, emitted)
    }

    @Test
    fun maximumDensityLifetimeFitsTheBoundedParticlePool() {
        val full = BubbleResponse.fromIntensity(1f, 5)
        val maxLifetime = 1f + 4 * 0.12f
        assertTrue(full.particlesPerSecond * maxLifetime < BubbleResponse.MAX_PARTICLES)
        assertEquals(0f, BubbleResponse.fromIntensity(Float.NaN).particlesPerSecond, 0f)
        assertEquals(0f, processor().process(Float.NaN), 0f)
    }
}

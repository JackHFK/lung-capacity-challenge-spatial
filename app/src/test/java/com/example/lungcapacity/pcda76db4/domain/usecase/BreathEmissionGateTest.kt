package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BreathEmissionGateTest {
    @Test
    fun runningWithoutMicrophoneIntensityNeverEmits() {
        val gate = BreathEmissionGate()

        val emitted = (0 until 240).sumOf {
            gate.update(1f / 60f, ChallengePhase.RUNNING, 0f, 0, preview = false)
        }

        assertEquals(0, emitted)
    }

    @Test
    fun countdownNeverEmitsEvenWithStrongInput() {
        val gate = BreathEmissionGate()

        val emitted = (0 until 240).sumOf {
            gate.update(1f / 60f, ChallengePhase.COUNTDOWN, 1f, 5, preview = false)
        }

        assertEquals(0, emitted)
    }

    @Test
    fun runningStrongInputEmitsAndDensityIncreasesTheCount() {
        val baseline = BreathEmissionGate()
        val upgraded = BreathEmissionGate()

        val baselineCount = (0 until 120).sumOf {
            baseline.update(1f / 60f, ChallengePhase.RUNNING, 1f, 0, preview = false)
        }
        val upgradedCount = (0 until 120).sumOf {
            upgraded.update(1f / 60f, ChallengePhase.RUNNING, 1f, 5, preview = false)
        }

        assertTrue(baselineCount > 0)
        assertTrue(upgradedCount > baselineCount)
    }
}

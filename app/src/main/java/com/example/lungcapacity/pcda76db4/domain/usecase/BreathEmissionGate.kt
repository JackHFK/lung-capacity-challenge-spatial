package com.example.lungcapacity.pcda76db4.domain.usecase

import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.domain.model.BubbleResponse

class BreathEmissionGate {
    private var accumulator = 0f
    private var wasActive = false

    fun update(
        deltaSeconds: Float,
        phase: ChallengePhase,
        intensity: Float,
        densityRank: Int,
        preview: Boolean,
    ): Int {
        val effectiveIntensity = intensity.coerceIn(0f, 1f)
        val active = (preview || phase == ChallengePhase.RUNNING) &&
            effectiveIntensity >= BubbleResponse.MIN_ACTIVE_INTENSITY
        if (!active) {
            accumulator = 0f
            wasActive = false
            return 0
        }

        if (!wasActive) accumulator = 1f // First bubble appears on the onset frame.
        wasActive = true
        accumulator += deltaSeconds.coerceIn(0f, 0.05f) *
            BubbleResponse.fromIntensity(effectiveIntensity, densityRank).particlesPerSecond
        var count = 0
        while (accumulator >= 1f) {
            accumulator -= 1f
            count += 1
        }
        return count
    }

    fun reset() {
        accumulator = 0f
        wasActive = false
    }
}

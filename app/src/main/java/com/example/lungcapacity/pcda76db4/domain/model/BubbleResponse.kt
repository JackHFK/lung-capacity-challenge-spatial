package com.example.lungcapacity.pcda76db4.domain.model

import kotlin.math.pow

/** One continuous response contract shared by emission, visuals and collision energy. */
data class BubbleResponse(
    val particlesPerSecond: Float,
    val diameterMeters: Float,
    val speedMetersPerSecond: Float,
) {
    companion object {
        const val MIN_ACTIVE_INTENSITY = 0.025f
        const val MAX_PARTICLES = 96

        fun fromIntensity(intensity: Float, densityRank: Int = 0): BubbleResponse {
            val level = if (intensity.isFinite()) intensity.coerceIn(0f, 1f) else 0f
            val density = 1f + densityRank.coerceIn(0, 5) * 0.07f
            return BubbleResponse(
                particlesPerSecond = if (level < MIN_ACTIVE_INTENSITY) 0f
                else (12f + 24f * level.pow(0.65f)) * density,
                diameterMeters = 0.035f + 0.095f * level.pow(0.65f),
                speedMetersPerSecond = 0.9f + 1.1f * level,
            )
        }
    }
}

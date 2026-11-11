package com.example.lungcapacity.pcda76db4.domain.usecase

class BreathSignalProcessor {
    fun process(rms: Float): Float {
        val sample = if (rms.isFinite()) rms else 0f
        return ((sample - BASELINE_RMS) / (FULL_STRENGTH_RMS - BASELINE_RMS))
            .coerceIn(0f, 1f)
    }

    fun reset() = Unit

    private companion object {
        const val BASELINE_RMS = 0.004f
        const val FULL_STRENGTH_RMS = 0.007f
    }
}

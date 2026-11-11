package com.example.lungcapacity.pcda76db4.ui.game.scene

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.sin

enum class FlamePhase {
    BURNING,
    EXTINGUISHING,
    OUT,
}

data class FlameSample(
    val phase: FlamePhase,
    val time: Float,
    val energy: Float,
    val bendX: Float,
    val bendZ: Float,
    val hitPulse: Float,
    val smoke: Float,
    val smokeAge: Float,
) {
    val visible: Boolean get() = energy > 0f
}

class FlameState(
    private val extinguishDuration: Float = 0.55f,
    private val smokeDuration: Float = 1.3f,
) {
    var phase: FlamePhase = FlamePhase.BURNING
        private set

    private var time = 0f
    private var fadeAge = 0f
    private var fadeFrom = 1f
    private var kickX = 0f
    private var kickZ = 0f
    private var pulse = 0f
    private var smokeAge = Float.POSITIVE_INFINITY

    init {
        require(extinguishDuration > 0f)
        require(smokeDuration > 0f)
    }

    fun ignite() {
        phase = FlamePhase.BURNING
        fadeAge = 0f
        fadeFrom = 1f
        kickX = 0f
        kickZ = 0f
        pulse = 0f
        smokeAge = Float.POSITIVE_INFINITY
    }

    fun hit(strength: Float = 1f, directionX: Float = 1f, directionZ: Float = 0f): Boolean {
        require(strength >= 0f)
        if (phase != FlamePhase.BURNING || strength == 0f) return false
        val length = hypot(directionX, directionZ)
        val normalizedX = if (length > 0.00000001f) directionX / length else 0f
        val normalizedZ = if (length > 0.00000001f) directionZ / length else 0f
        val clamped = strength.coerceIn(0f, 1f)
        kickX = (kickX + normalizedX * clamped).coerceIn(-1.1f, 1.1f)
        kickZ = (kickZ + normalizedZ * clamped).coerceIn(-1.1f, 1.1f)
        pulse = (pulse + clamped).coerceAtMost(1.2f)
        return true
    }

    fun extinguish(): Boolean {
        if (phase != FlamePhase.BURNING) return false
        fadeFrom = sample().energy
        fadeAge = 0f
        phase = FlamePhase.EXTINGUISHING
        smokeAge = 0f
        return true
    }

    fun update(deltaSeconds: Float) {
        require(deltaSeconds >= 0f)
        time += deltaSeconds
        val damping = exp(-deltaSeconds * 8f)
        kickX *= damping
        kickZ *= damping
        pulse *= exp(-deltaSeconds * 7f)
        if (phase == FlamePhase.EXTINGUISHING) {
            fadeAge = (fadeAge + deltaSeconds).coerceAtMost(extinguishDuration)
            if (fadeAge >= extinguishDuration) phase = FlamePhase.OUT
        }
        if (smokeAge.isFinite()) smokeAge += deltaSeconds
    }

    fun sample(): FlameSample {
        val fade = (fadeAge / extinguishDuration).coerceIn(0f, 1f)
        val smoothFade = fade * fade * (3f - 2f * fade)
        val energy = when (phase) {
            FlamePhase.BURNING -> 1f
            FlamePhase.EXTINGUISHING -> fadeFrom * (1f - smoothFade)
            FlamePhase.OUT -> 0f
        }
        val smoke = if (smokeAge.isFinite()) {
            sin(PI.toFloat() * (smokeAge / smokeDuration).coerceIn(0f, 1f)) * 0.65f
        } else {
            0f
        }
        return FlameSample(
            phase = phase,
            time = time,
            energy = energy,
            bendX = kickX,
            bendZ = kickZ,
            hitPulse = pulse,
            smoke = smoke.coerceAtLeast(0f),
            smokeAge = if (smokeAge.isFinite()) smokeAge else 0f,
        )
    }
}


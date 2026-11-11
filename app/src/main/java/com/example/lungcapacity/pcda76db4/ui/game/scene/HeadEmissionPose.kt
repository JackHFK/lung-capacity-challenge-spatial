package com.example.lungcapacity.pcda76db4.ui.game.scene

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3

/** Immutable world-space snapshot. The same pose drives mouth, aim and viewer. */
internal data class HeadEmissionPose(
    val position: Vector3,
    val rotation: Quat,
    val intensity: Float,
) {
    fun mouthWorld(): Vector3 = position + rotation.rotateVector(MOUTH_OFFSET)

    fun trajectory(owner: Entity, headDirection: Vector3, speed: Float): LocalTrajectory {
        val originWorld = mouthWorld()
        val velocityWorld = rotation.rotateVector(headDirection) * speed
        val localOrigin = owner.convertPositionFrom(originWorld, null)
        // A vector is the difference of two converted points. Do not normalize away owner scale.
        val localVelocity = owner.convertPositionFrom(originWorld + velocityWorld, null) - localOrigin
        return LocalTrajectory(localOrigin, localVelocity)
    }

    companion object {
        val MOUTH_OFFSET = Vector3(0f, -0.10f, -0.08f)
    }
}

internal data class LocalTrajectory(val origin: Vector3, val velocity: Vector3)

package com.example.lungcapacity.pcda76db4.ui.game.scene

import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System as SpatialSystem
import java.util.concurrent.CopyOnWriteArraySet

internal interface FrameParticipant {
    fun update(context: SceneUpdateContext)
}

internal class BreathFrameSystem : SpatialSystem() {
    override fun update(context: SceneUpdateContext) {
        participants.forEach { it.update(context) }
    }

    companion object {
        private val participants = CopyOnWriteArraySet<FrameParticipant>()

        @Synchronized
        fun add(participant: FrameParticipant) {
            if (participants.isEmpty()) {
                SpatialSystem.register(BreathFrameSystem::class.java)
            }
            participants.add(participant)
        }

        @Synchronized
        fun remove(participant: FrameParticipant) {
            participants.remove(participant)
            if (participants.isEmpty()) {
                SpatialSystem.unregister(BreathFrameSystem::class.java)
            }
        }
    }
}

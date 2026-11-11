package com.example.lungcapacity.pcda76db4.ui.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FlameStateTest {
    @Test
    fun hitBendsAndDecaysWhileBurning() {
        val state = FlameState()
        assertTrue(state.hit(0.95f, directionX = 1f))
        val hit = state.sample()
        assertEquals(0.95f, hit.bendX, 0.0001f)
        assertEquals(0.95f, hit.hitPulse, 0.0001f)

        state.update(0.5f)
        assertTrue(state.sample().bendX < hit.bendX)
        assertEquals(FlamePhase.BURNING, state.phase)
    }

    @Test
    fun extinguishUsesSourceFadeAndSmokeDurations() {
        val state = FlameState()
        assertTrue(state.extinguish())
        state.update(0.275f)
        val midpoint = state.sample()
        assertEquals(0.5f, midpoint.energy, 0.0001f)
        assertTrue(midpoint.smoke > 0f)

        state.update(0.275f)
        assertEquals(FlamePhase.OUT, state.phase)
        assertFalse(state.sample().visible)
        state.ignite()
        assertEquals(FlamePhase.BURNING, state.phase)
    }
}

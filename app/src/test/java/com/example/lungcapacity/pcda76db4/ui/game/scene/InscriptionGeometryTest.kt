package com.example.lungcapacity.pcda76db4.ui.game.scene

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InscriptionGeometryTest {
    @Test
    fun exactSourceMeshesDecodeWithExpectedTopology() {
        val prepared = PreparedInscriptionGeometry.prepare()

        assertEquals(31_576, prepared.happy.vertexCount)
        assertEquals(38_568, prepared.happy.indices.size)
        assertEquals(40_727, prepared.birthday.vertexCount)
        assertEquals(49_188, prepared.birthday.indices.size)
        assertEquals(0, prepared.happy.indices.size % 3)
        assertEquals(0, prepared.birthday.indices.size % 3)
        assertTrue(prepared.happy.vertices.all(Float::isFinite))
        assertTrue(prepared.birthday.vertices.all(Float::isFinite))
    }
}

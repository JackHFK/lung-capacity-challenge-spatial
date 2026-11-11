package com.example.lungcapacity.pcda76db4.ui.stage

import org.junit.Assert.assertEquals
import org.junit.Test

class StagePanelLayoutTest {
    @Test
    fun everyStagePanelMovesDownByExactlyFifteenCentimeters() {
        assertEquals(-0.15f, StagePanelLayout.VerticalShiftMeters, 0f)
        assertEquals(0.13f, StagePanelLayout.StatusY, 0.0001f)
        assertEquals(-0.13f, StagePanelLayout.CountdownY, 0.0001f)
        assertEquals(-0.15f, StagePanelLayout.ResultY, 0.0001f)
        assertEquals(-0.9f, StagePanelLayout.ForwardZ, 0f)
        assertEquals(-0.65f, StagePanelLayout.ResultForwardZ, 0f)
    }
}

package com.example.lungcapacity.pcda76db4.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Test

class BreathSignalProcessorTest {
    private val processor = BreathSignalProcessor()

    @Test
    fun valuesAtOrBelowBaselineProduceZeroStrength() {
        assertEquals(0f, processor.process(0f), 0f)
        assertEquals(0f, processor.process(0.0039f), 0f)
        assertEquals(0f, processor.process(0.004f), 0f)
    }

    @Test
    fun midpointProducesHalfStrength() {
        assertEquals(0.5f, processor.process(0.0055f), 0.0001f)
    }

    @Test
    fun valuesAtOrAboveFullStrengthThresholdProduceFullStrength() {
        assertEquals(1f, processor.process(0.007f), 0f)
        assertEquals(1f, processor.process(0.1f), 0f)
    }

    @Test
    fun invalidValuesProduceZeroStrength() {
        assertEquals(0f, processor.process(Float.NaN), 0f)
        assertEquals(0f, processor.process(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun resetDoesNotChangeTheFixedMapping() {
        assertEquals(0.5f, processor.process(0.0055f), 0.0001f)
        processor.reset()
        assertEquals(0.5f, processor.process(0.0055f), 0.0001f)
    }
}

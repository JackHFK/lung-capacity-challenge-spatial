package com.example.lungcapacity.pcda76db4.ui.game.scene

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SoapBubbleSurfaceInstrumentedTest {
    @Test
    fun filmHasClearCenterColoredRimAndTransparentOutside() {
        val bitmap = SoapBubbleTexture.createBitmap()
        try {
            assertTrue(Color.alpha(bitmap.getPixel(128, 128)) <= 4)
            assertEquals(0, Color.alpha(bitmap.getPixel(0, 0)))
            val rim = bitmap.getPixel(247, 128)
            assertTrue(Color.alpha(rim) > 130)
            val channels = listOf(Color.red(rim), Color.green(rim), Color.blue(rim))
            assertTrue(channels.max() - channels.min() > 50)
        } finally {
            bitmap.recycle()
        }
    }
}

package com.example.lungcapacity.pcda76db4.ui.game.scene

import android.graphics.Bitmap
import android.graphics.Color
import com.pico.spatial.core.ecs.resource.TextureResource
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

internal data class TextureSet(
    val baseColor: TextureResource,
    val roughness: TextureResource,
    val normal: TextureResource,
) {
    fun close() {
        baseColor.close()
        roughness.close()
        normal.close()
    }
}

internal data class SurfaceBitmaps(
    val baseColor: Bitmap,
    val roughness: Bitmap,
    val normal: Bitmap,
) {
    fun close() {
        baseColor.recycle()
        roughness.recycle()
        normal.recycle()
    }
}

internal data class PreparedCakeTextures(
    val cream: SurfaceBitmaps,
    val fruit: SurfaceBitmaps,
    val wood: SurfaceBitmaps,
    val candle: Bitmap,
) {
    fun close() {
        cream.close()
        fruit.close()
        wood.close()
        candle.recycle()
    }

    companion object {
        fun prepare(): PreparedCakeTextures =
            PreparedCakeTextures(
                cream = ProceduralTextures.surfaceBitmaps(SurfaceKind.CREAM),
                fruit = ProceduralTextures.surfaceBitmaps(SurfaceKind.FRUIT),
                wood = ProceduralTextures.surfaceBitmaps(SurfaceKind.WOOD),
                candle = ProceduralTextures.candleBitmap(),
            )
    }
}

internal object ProceduralTextures {
    fun surface(kind: SurfaceKind, size: Int = 1024): TextureSet {
        val bitmaps = surfaceBitmaps(kind, size)
        return try {
            TextureSet(
                baseColor = texture(bitmaps.baseColor),
                roughness = texture(bitmaps.roughness),
                normal = texture(bitmaps.normal),
            )
        } finally {
            bitmaps.close()
        }
    }

    fun surfaceBitmaps(
        kind: SurfaceKind,
        size: Int = 1024,
        onProgress: (Float) -> Unit = {},
    ): SurfaceBitmaps {
        onProgress(0f)
        val colorPixels = IntArray(size * size)
        val roughnessPixels = IntArray(size * size)
        val heights = FloatArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val index = y * size + x
                val u = x / size.toFloat()
                val v = y / size.toFloat()
                val random = seededNoise(x + y * size)
                val fold =
                    0.5f +
                        0.25f * sin(u * 25f + sin(v * 34f) * 0.7f) +
                        0.15f * sin(u * 56f - v * 17f)
                val sample = when (kind) {
                    SurfaceKind.CREAM -> {
                        val value = 244f + (fold - 0.5f) * 5f
                        PixelSample(
                            red = value,
                            green = 233f + (fold - 0.5f) * 5f,
                            blue = 213f + (fold - 0.5f) * 5f,
                            roughness = 155f + (fold - 0.5f) * 12f + (random - 0.5f) * 9f,
                            height = (random - 0.5f) * 0.6f + (fold - 0.5f),
                        )
                    }
                    SurfaceKind.FRUIT -> {
                        val variation = sin(u * 15f + v * 3f) * sin(v * 23f)
                        PixelSample(
                            red = 192f + variation * 17f + random * 4f,
                            green = 19f + variation * 5f + random * 3f,
                            blue = 29f + variation * 5f + random * 4f,
                            roughness = 65f + fold * 20f + random * 5f,
                            height = (random - 0.5f) * 0.55f + variation * 0.35f,
                        )
                    }
                    SurfaceKind.WOOD -> {
                        val fine = sin(v * TWO_PI * 75f + 2f * sin(u * 12f) + sin(u * 27f) * 0.8f)
                        val broad = sin(v * TWO_PI * 7f + sin(u * 14f))
                        PixelSample(
                            red = 126f + fine * 10f + broad * 12f,
                            green = 83f + fine * 7f + broad * 9f,
                            blue = 39f + fine * 4f + broad * 5f,
                            roughness = 160f + fine * 9f + random * 8f,
                            height = fine * 0.75f + (random - 0.5f) * 0.3f,
                        )
                    }
                }
                colorPixels[index] = Color.rgb(
                    sample.red.byteValue(),
                    sample.green.byteValue(),
                    sample.blue.byteValue(),
                )
                val roughness = sample.roughness.byteValue()
                roughnessPixels[index] = Color.rgb(roughness, roughness, roughness)
                heights[index] = sample.height
            }
            if ((y + 1) % PROGRESS_ROW_INTERVAL == 0 || y == size - 1) {
                onProgress((y + 1).toFloat() / size * SURFACE_COLOR_PROGRESS)
            }
        }
        val normalPixels = normalMap(
            heights = heights,
            size = size,
            strength = kind.normalStrength,
            onProgress = { progress ->
                onProgress(
                    SURFACE_COLOR_PROGRESS +
                        progress * (1f - SURFACE_COLOR_PROGRESS),
                )
            },
        )
        onProgress(1f)
        return SurfaceBitmaps(
            baseColor = bitmap(colorPixels, size),
            roughness = bitmap(roughnessPixels, size),
            normal = bitmap(normalPixels, size),
        )
    }

    fun candle(size: Int = 512): TextureResource {
        val bitmap = candleBitmap(size)
        return try {
            texture(bitmap)
        } finally {
            bitmap.recycle()
        }
    }

    fun candleBitmap(
        size: Int = 512,
        onProgress: (Float) -> Unit = {},
    ): Bitmap {
        onProgress(0f)
        val pixels = IntArray(size * size)
        for (y in 0 until size) {
            for (x in 0 until size) {
                val stripe = (x / size.toFloat() * 2f + y / size.toFloat() * 5f) % 1f < 0.34f
                pixels[y * size + x] = if (stripe) {
                    Color.rgb(237, 105, 147)
                } else {
                    Color.rgb(252, 245, 230)
                }
            }
            if ((y + 1) % PROGRESS_ROW_INTERVAL == 0 || y == size - 1) {
                onProgress((y + 1).toFloat() / size)
            }
        }
        onProgress(1f)
        return bitmap(pixels, size)
    }

    private fun normalMap(
        heights: FloatArray,
        size: Int,
        strength: Float,
        onProgress: (Float) -> Unit,
    ): IntArray {
        val output = IntArray(heights.size)
        fun height(x: Int, y: Int): Float {
            val wrappedX = (x + size) % size
            val wrappedY = (y + size) % size
            return heights[wrappedY * size + wrappedX]
        }
        for (y in 0 until size) {
            for (x in 0 until size) {
                val dx = (height(x + 1, y) - height(x - 1, y)) * strength
                val dy = (height(x, y + 1) - height(x, y - 1)) * strength
                val length = sqrt(dx * dx + dy * dy + 1f)
                val nx = -dx / length
                val ny = -dy / length
                val nz = 1f / length
                output[y * size + x] = Color.rgb(
                    ((nx * 0.5f + 0.5f) * 255f).byteValue(),
                    ((ny * 0.5f + 0.5f) * 255f).byteValue(),
                    ((nz * 0.5f + 0.5f) * 255f).byteValue(),
                )
            }
            if ((y + 1) % PROGRESS_ROW_INTERVAL == 0 || y == size - 1) {
                onProgress((y + 1).toFloat() / size)
            }
        }
        return output
    }

    private fun bitmap(
        pixels: IntArray,
        size: Int,
    ): Bitmap = Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)

    internal fun texture(bitmap: Bitmap): TextureResource =
        TextureResource.create(bitmap).apply { toGlobal() }

    private fun seededNoise(seed: Int): Float {
        val value = sin(seed * 127.1f + 311.7f) * 43758.5453f
        return value - kotlin.math.floor(value)
    }

    private fun Float.byteValue(): Int = min(255, max(0, toInt()))

    private data class PixelSample(
        val red: Float,
        val green: Float,
        val blue: Float,
        val roughness: Float,
        val height: Float,
    )

    private const val TWO_PI = (PI * 2.0).toFloat()
    private const val PROGRESS_ROW_INTERVAL = 64
    private const val SURFACE_COLOR_PROGRESS = 0.65f
}

internal enum class SurfaceKind(val normalStrength: Float) {
    CREAM(0.35f),
    FRUIT(0.28f),
    WOOD(0.55f),
}

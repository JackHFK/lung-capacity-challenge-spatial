package com.example.lungcapacity.pcda76db4.ui.game.scene

import android.graphics.Bitmap
import android.graphics.Color
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector2
import com.pico.spatial.core.math.Vector3
import kotlin.math.atan2
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Scene-owned camera-facing particle surface; transparent center and a thin-film rim. */
internal class SoapBubbleResources {
    val mesh = MeshResource.createWithMeshModel(
        model = MeshModel(
            positions = listOf(
                Vector3(-0.5f, -0.5f, 0f), Vector3(0.5f, -0.5f, 0f),
                Vector3(0.5f, 0.5f, 0f), Vector3(-0.5f, 0.5f, 0f),
            ),
            triangleIndices = listOf(0, 1, 2, 0, 2, 3),
            normals = List(4) { Vector3(0f, 0f, 1f) },
            uv0 = listOf(Vector2(0f, 0f), Vector2(1f, 0f), Vector2(1f, 1f), Vector2(0f, 1f)),
        ),
        bounds = BoundingBox(Vector3.ZERO, Vector3(0.5f, 0.5f, 0.001f)),
        name = "soap-bubble-film",
    ).apply { toGlobal() }
    private val texture = createTexture()
    private val materials = mutableListOf<UnlitMaterial>()

    fun material(): UnlitMaterial = UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
        toGlobal()
        setBaseColor(Color4.WHITE)
        setBaseColorTexture(texture)
        setApplyToneMapping(false)
        setOpacity(1f)
        setDepthWrite(false)
        setDepthTest(true)
        setCullingMode(MaterialCullingMode.NONE)
    }.also(materials::add)

    fun close() {
        materials.forEach { it.close() }
        materials.clear()
        texture.close()
        mesh.close()
    }

    private fun createTexture(): TextureResource {
        val bitmap = SoapBubbleTexture.createBitmap()
        return try {
            TextureResource.create(bitmap).apply { toGlobal() }
        } finally {
            bitmap.recycle()
        }
    }
}

/** CPU texture creation stays separate from SDK uploads and can run off the frame loop. */
internal object SoapBubbleTexture {
    fun createBitmap(): Bitmap {
        val size = 256
        val pixels = IntArray(size * size)
        for (y in 0 until size) for (x in 0 until size) {
            val u = (x + 0.5f) / size * 2f - 1f
            val v = (y + 0.5f) / size * 2f - 1f
            val radius = sqrt(u * u + v * v)
            if (radius >= 0.96f) continue
            val normalZ = sqrt((1f - (radius / 0.96f).pow(2)).coerceAtLeast(0f))
            val fresnel = (1f - normalZ).pow(2.1f)
            val rim = exp(-((radius - 0.928f) / 0.017f).pow(2))
            val innerArc = exp(-((radius - 0.84f) / 0.04f).pow(2))
            val angle = atan2(v, u)
            val hue = ((angle * 180f / Math.PI.toFloat() + normalZ * 190f + 210f) % 360f + 360f) % 360f
            val filmColor = Color.HSVToColor(floatArrayOf(hue, 0.56f, 1f))
            val sheen = exp(-((u + 0.33f) / 0.13f).pow(2) - ((v + 0.49f) / 0.10f).pow(2))
            val arcHighlight = exp(-((radius - 0.79f) / 0.042f).pow(2)) *
                exp(-((angle + 2.15f) / 0.45f).pow(2))
            val gleam = (sheen + arcHighlight * 0.85f).coerceIn(0f, 1f)
            val alpha = (0.008f + fresnel * 0.23f + rim * 0.72f +
                innerArc * 0.10f + gleam * 0.98f).coerceIn(0f, 0.97f)
            fun channel(component: Int): Int {
                val film = component / 255f
                return ((film * (1f - gleam) + gleam) * 255f).roundToInt().coerceIn(0, 255)
            }
            pixels[y * size + x] = ((alpha * 255f).roundToInt() shl 24) or
                (channel(Color.red(filmColor)) shl 16) or
                (channel(Color.green(filmColor)) shl 8) or channel(Color.blue(filmColor))
        }
        return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
    }
}

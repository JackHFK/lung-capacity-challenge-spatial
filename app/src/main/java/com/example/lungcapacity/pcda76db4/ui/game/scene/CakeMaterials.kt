package com.example.lungcapacity.pcda76db4.ui.game.scene

import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MaterialTextureParameter
import com.pico.spatial.core.ecs.resource.MaterialTextureSampler
import com.pico.spatial.core.ecs.resource.PhysicallyBasedMaterial
import com.pico.spatial.core.ecs.resource.SamplerWrapMode
import com.pico.spatial.core.ecs.resource.TextureResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4

internal class CakeMaterials(
    private val textures: CakeTextureResources,
) {
    private val materials = mutableListOf<Material>()

    val easyCream = pbr("0xFAF1DFFF", roughness = 0.63f).apply {
        applySurfaceTextures(textures.cream, normalScale = 0.48f)
    }
    val tierCream = pbr("0xFFFFFFFF", roughness = 0.62f).apply {
        applySurfaceTextures(textures.cream, normalScale = 0.32f)
    }
    val glossyCream = pbr("0xFFFFFFFF", roughness = 0.30f).apply {
        applySurfaceTextures(textures.cream, normalScale = 0.26f)
    }
    val wood = pbr("0xFFFFFFFF", roughness = 0.65f).apply {
        applySurfaceTextures(textures.wood, normalScale = 0.58f)
    }
    val gold = pbr("0xCDA251FF", roughness = 0.44f, metallic = 0.80f)
    val silver = pbr("0xC6CAD1FF", roughness = 0.38f, metallic = 1f)
    val easyStrawberry = pbr("0xBF152BFF", roughness = 0.28f).apply {
        applySurfaceTextures(textures.fruit, normalScale = 0.30f)
    }
    val tierStrawberry = pbr("0xFFFFFFFF", roughness = 0.30f).apply {
        applySurfaceTextures(textures.fruit, normalScale = 0.32f)
    }
    val raspberry = pbr("0xB2203CFF", roughness = 0.37f)
    val blackberry = pbr("0x20172BFF", roughness = 0.30f)
    val cherry = pbr("0x631D32FF", roughness = 0.23f)
    val leaf = pbr("0x537B26FF", roughness = 0.80f).apply {
        setCullingMode(MaterialCullingMode.NONE)
    }
    val stem = pbr("0x5C652FFF", roughness = 0.80f)
    val seed = pbr("0xC89951FF", roughness = 0.55f)
    val redIcing = pbr("0xBE2737FF", roughness = 0.50f)
    val wick = pbr("0x29211CFF", roughness = 1f)
    val ground = pbr("0xF1F3F6FF", roughness = 1f)
    val candleWax = pbr("0xFFFFFFFF", roughness = 0.43f).apply {
        setBaseColorTextureParameter(repeating(textures.candle))
    }
    val flameVertexColor = opaqueUnlit("0xFFFFFFFF")
    val flameCore = transparentUnlit("0xFF7A12FF", 0.88f)

    fun smokeMaterial(): UnlitMaterial = transparentUnlit("0x8A9098FF", 0f)

    fun emberMaterial(): UnlitMaterial = transparentUnlit("0xF48223FF", 0f)

    fun close() {
        materials.asReversed().forEach { runCatching { it.close() } }
        materials.clear()
    }

    private fun pbr(
        color: String,
        roughness: Float,
        metallic: Float = 0f,
    ): PhysicallyBasedMaterial =
        PhysicallyBasedMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(Color4.fromSRGBHex(color))
            setRoughness(roughness)
            setMetallic(metallic)
            toGlobal()
        }.tracked()

    private fun transparentUnlit(
        color: String,
        opacity: Float,
    ): UnlitMaterial =
        UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(Color4.fromSRGBHex(color))
            setOpacity(opacity)
            setCullingMode(MaterialCullingMode.NONE)
            setDepthWrite(false)
            setApplyToneMapping(false)
            toGlobal()
        }.tracked()

    private fun opaqueUnlit(color: String): UnlitMaterial =
        UnlitMaterial.create(BlendingMode.OPAQUE).apply {
            setBaseColor(Color4.fromSRGBHex(color))
            setCullingMode(MaterialCullingMode.NONE)
            setDepthWrite(false)
            setApplyToneMapping(false)
            toGlobal()
        }.tracked()

    private fun PhysicallyBasedMaterial.applySurfaceTextures(
        set: TextureSet,
        normalScale: Float,
    ) {
        setBaseColorTextureParameter(repeating(set.baseColor))
        setRoughnessTextureParameter(repeating(set.roughness))
        setNormalTextureParameter(repeating(set.normal))
        setNormalScale(normalScale)
    }

    private fun repeating(texture: TextureResource): MaterialTextureParameter {
        val sampler = MaterialTextureSampler().apply {
            sWrapMode = SamplerWrapMode.REPEAT
            tWrapMode = SamplerWrapMode.REPEAT
        }
        return MaterialTextureParameter(texture, sampler, 0)
    }

    private fun <T : Material> T.tracked(): T = apply { materials += this }

}

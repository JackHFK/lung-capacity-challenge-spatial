package com.example.lungcapacity.pcda76db4.ui.game.scene

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.DrawOrderGroup
import com.pico.spatial.core.ecs.DrawOrderGroupComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshIndexSizeType
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.MeshVertexAttribute
import com.pico.spatial.core.ecs.resource.MeshVertexAttributeType
import com.pico.spatial.core.ecs.resource.PrimitiveType
import com.pico.spatial.core.ecs.resource.RawMesh
import com.pico.spatial.core.ecs.resource.RawMeshDescriptorBuilder
import com.pico.spatial.core.ecs.resource.RawMeshStreamAttribute
import com.pico.spatial.core.ecs.resource.RawMeshStreamLayout
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin

internal class FlameVisual private constructor(
    val root: Entity,
    private val state: FlameState,
    private val phaseOffset: Float,
    private val rootTransform: TransformComponent,
    private val flameOuter: Entity,
    private val flameOuterTransform: TransformComponent,
    private val flameCore: Entity,
    private val flameCoreTransform: TransformComponent,
    private val smoke: Entity,
    private val smokeTransform: TransformComponent,
    private val smokeMaterial: UnlitMaterial,
    private val ember: Entity,
    private val emberMaterial: UnlitMaterial,
) {
    private var enabled = true

    fun update(deltaSeconds: Float) {
        state.update(deltaSeconds)
        apply()
    }

    fun ignite() {
        state.ignite()
        apply()
    }

    fun hit(
        strength: Float,
        directionX: Float,
        directionZ: Float,
    ): Boolean = state.hit(strength, directionX, directionZ)

    fun extinguish(): Boolean {
        val changed = state.extinguish()
        apply()
        return changed
    }

    fun setBillboardYaw(degrees: Float) {
        rootTransform.setEulerAngles(EulerAngles(0f, degrees, 0f))
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        root.enabled = value
    }

    private fun apply() {
        val sample = state.sample()
        if (sample.visible) {
            val phase = sample.time + phaseOffset
            val flicker =
                0.96f +
                    0.025f * sin(phase * 11.1f) +
                    0.02f * sin(phase * 23.4f)
            flameOuterTransform.setPosition(
                Vector3(
                    sample.bendX * 0.006f,
                    0f,
                    sample.bendZ * 0.006f,
                ),
            )
            flameOuterTransform.setScaleVector(
                Vector3(
                    1f + sample.hitPulse * 0.32f,
                    max(0.03f, sample.energy) *
                        flicker *
                        (1f - 0.30f * sample.hitPulse),
                    1f,
                ),
            )
            flameOuterTransform.setEulerAngles(
                EulerAngles(
                    sample.bendZ * 18f,
                    0f,
                    -sample.bendX * 18f + sin(phase * 5.4f) * 1.2f,
                ),
            )
        }
        flameOuter.enabled = enabled && sample.visible
        flameCore.enabled = enabled && sample.visible
        if (sample.visible) {
            val coreEnergy = sample.energy * (1f - 0.20f * sample.hitPulse)
            flameCoreTransform.setPosition(
                Vector3(
                    sample.bendX * 0.012f,
                    0.045f * coreEnergy,
                    sample.bendZ * 0.012f,
                ),
            )
            flameCoreTransform.setScaleVector(
                Vector3(
                    0.016f * (1f + sample.hitPulse * 0.25f),
                    0.050f * coreEnergy,
                    0.016f * (1f + sample.hitPulse * 0.25f),
                ),
            )
        }

        val smokeVisible = enabled && sample.smoke > 0.00001f
        smoke.enabled = smokeVisible
        if (smokeVisible) {
            smokeMaterial.setOpacity(sample.smoke * 0.35f)
            smokeTransform.setPosition(
                Vector3(
                    sin(sample.smokeAge * 3.6f) * 0.004f,
                    sample.smokeAge * 0.025f,
                    -0.001f,
                ),
            )
            smokeTransform.setScaleVector(
                Vector3(
                    1f + sample.smokeAge * 0.08f,
                    1f + sample.smokeAge * 0.12f,
                    1f,
                ),
            )
            smokeTransform.setEulerAngles(
                EulerAngles(0f, 0f, sin(sample.smokeAge * 4.2f) * 4f),
            )
        }

        val emberOpacity = if (sample.phase.name == "BURNING") 0.45f else sample.smoke * 0.7f
        emberMaterial.setOpacity(emberOpacity)
        ember.enabled = enabled && emberOpacity > 0f
    }

    companion object {
        private const val FLAME_HEIGHT = 0.135f
        private const val FLAME_WIDTH = 0.072f
        private const val SMOKE_HEIGHT = FLAME_HEIGHT * 2f

        fun create(
            parent: Entity,
            index: Int,
            meshes: ProceduralMeshFactory,
            ribbons: FlameMeshResources,
            materials: CakeMaterials,
        ): FlameVisual {
            val root = Entity().apply {
                setName("candle-$index-flame-root")
                components.get(TransformComponent::class.java)?.setPosition(
                    Vector3(0f, 0.345f, 0f),
                )
                parent.addChild(this)
            }
            val rootTransform = requireNotNull(root.components.get(TransformComponent::class.java))
            val drawOrder = DrawOrderGroup.create()
            val outer = model(
                root,
                "flame-$index-outer",
                ribbons.flame,
                materials.flameVertexColor,
                drawOrder = drawOrder,
                order = 10,
            )
            val outerTransform = requireNotNull(
                outer.components.get(TransformComponent::class.java),
            )
            val core = model(
                root,
                "flame-$index-core",
                meshes.sphere,
                materials.flameCore,
                position = Vector3(0f, 0.045f, 0f),
                scale = Vector3(0.016f, 0.050f, 0.016f),
                drawOrder = drawOrder,
                order = 11,
            )
            val coreTransform = requireNotNull(
                core.components.get(TransformComponent::class.java),
            )

            val smokeMaterial = materials.smokeMaterial()
            val smoke = model(
                root,
                "flame-$index-smoke",
                ribbons.smoke,
                smokeMaterial,
                position = Vector3(0f, 0f, -0.001f),
                drawOrder = drawOrder,
                order = 12,
            ).apply { enabled = false }
            val smokeTransform = requireNotNull(
                smoke.components.get(TransformComponent::class.java),
            )

            val emberMaterial = materials.emberMaterial()
            val ember = model(
                root,
                "flame-$index-ember",
                meshes.sphere,
                emberMaterial,
                scale = Vector3(0.006f),
                drawOrder = drawOrder,
                order = 13,
            )

            return FlameVisual(
                root = root,
                state = FlameState(),
                phaseOffset = index * 1.73f,
                rootTransform = rootTransform,
                flameOuter = outer,
                flameOuterTransform = outerTransform,
                flameCore = core,
                flameCoreTransform = coreTransform,
                smoke = smoke,
                smokeTransform = smokeTransform,
                smokeMaterial = smokeMaterial,
                ember = ember,
                emberMaterial = emberMaterial,
            ).also { it.apply() }
        }

        private fun model(
            parent: Entity,
            name: String,
            mesh: MeshResource,
            material: UnlitMaterial,
            position: Vector3 = Vector3.ZERO,
            scale: Vector3 = Vector3(1f),
            drawOrder: DrawOrderGroup? = null,
            order: Int = 0,
        ): Entity =
            Entity().apply {
                setName(name)
                components.set(ModelComponent(mesh, material))
                if (drawOrder != null) {
                    components.set(DrawOrderGroupComponent(drawOrder, order))
                }
                components.get(TransformComponent::class.java)?.apply {
                    setPosition(position)
                    setScaleVector(scale)
                }
                parent.addChild(this)
            }
    }
}

internal class FlameMeshResources private constructor(
    val flame: MeshResource,
    val smoke: MeshResource,
) {
    fun close() {
        flame.close()
        smoke.close()
    }

    companion object {
        fun create(): FlameMeshResources {
            var flame: MeshResource? = null
            return try {
                flame = createRibbonMesh(
                    name = "shared-flame-ribbon",
                    width = 0.072f,
                    height = 0.135f,
                    kind = RibbonKind.FLAME,
                )
                val smoke = createRibbonMesh(
                    name = "shared-smoke-ribbon",
                    width = 0.072f * 1.4f,
                    height = 0.135f * 2f,
                    kind = RibbonKind.SMOKE,
                )
                FlameMeshResources(flame, smoke)
            } catch (error: Throwable) {
                runCatching { flame?.close() }
                throw error
            }
        }
    }
}

private enum class RibbonKind {
    FLAME,
    SMOKE,
}

private fun createRibbonMesh(
    name: String,
    width: Float,
    height: Float,
    kind: RibbonKind,
): MeshResource {
    val vertexData = FloatArray(RIBBON_VERTEX_COUNT * RIBBON_FLOATS_PER_VERTEX)
    val indexData = ShortArray(RIBBON_INDEX_COUNT)
    run {
        var write = 0
        repeat(RIBBON_ROWS - 1) { row ->
            repeat(RIBBON_COLUMNS - 1) { column ->
                val start = row * RIBBON_COLUMNS + column
                indexData[write++] = start.toShort()
                indexData[write++] = (start + RIBBON_COLUMNS).toShort()
                indexData[write++] = (start + 1).toShort()
                indexData[write++] = (start + 1).toShort()
                indexData[write++] = (start + RIBBON_COLUMNS).toShort()
                indexData[write++] = (start + RIBBON_COLUMNS + 1).toShort()
            }
        }
    }

    repeat(RIBBON_VERTEX_COUNT) { vertex ->
        val offset = vertex * RIBBON_FLOATS_PER_VERTEX
        vertexData[offset + 5] = 1f
    }
    repeat(RIBBON_ROWS) { row ->
        val t = row.toFloat() / (RIBBON_ROWS - 1)
        val silhouette =
            when (kind) {
                RibbonKind.FLAME ->
                    0.35f *
                        sin(PI.toFloat() * t).coerceAtLeast(0f).pow(0.64f) *
                        (1f - 0.48f * t)
                RibbonKind.SMOKE ->
                    0.12f *
                        sin(PI.toFloat() * t).coerceAtLeast(0f).pow(0.62f) *
                        (1f - 0.28f * t)
            }
        val halfWidth = width * silhouette
        RIBBON_COLUMN_FACTORS.forEachIndexed { column, factor ->
            val vertex = row * RIBBON_COLUMNS + column
            val offset = vertex * RIBBON_FLOATS_PER_VERTEX
            vertexData[offset] = halfWidth * factor
            vertexData[offset + 1] = t * height
            vertexData[offset + 2] = 0f
            if (kind == RibbonKind.FLAME) {
                val edgeMix = if (column == 0 || column == RIBBON_COLUMNS - 1) 0f else 1f
                val blueMix = ((0.22f - t) / 0.22f).coerceIn(0f, 1f)
                vertexData[offset + 6] = lerp(1f, 0.05f, blueMix)
                vertexData[offset + 7] = lerp(
                    lerp(0.08f, 0.92f, edgeMix),
                    0.25f,
                    blueMix,
                )
                vertexData[offset + 8] = lerp(
                    lerp(0.005f, 0.48f, edgeMix),
                    1f,
                    blueMix,
                )
            } else {
                vertexData[offset + 6] = 0.40f
                vertexData[offset + 7] = 0.43f
                vertexData[offset + 8] = 0.46f
            }
            vertexData[offset + 9] = 1f
        }
    }

    val descriptor = RawMeshDescriptorBuilder().use { builder ->
        builder
            .streamLayout(
                RawMeshStreamLayout(
                    bufferSlot = 0,
                    stride = RIBBON_VERTEX_STRIDE_BYTES,
                ),
            )
            .streamAttribute(
                RawMeshStreamAttribute(
                    MeshVertexAttribute.POSITION,
                    MeshVertexAttributeType.FLOAT3,
                ),
            )
            .streamAttribute(
                RawMeshStreamAttribute(
                    MeshVertexAttribute.NORMAL,
                    MeshVertexAttributeType.FLOAT3,
                    recordByteOffset = 12,
                ),
            )
            .streamAttribute(
                RawMeshStreamAttribute(
                    MeshVertexAttribute.COLOR,
                    MeshVertexAttributeType.FLOAT4,
                    recordByteOffset = 24,
                ),
            )
            .vertexCount(RIBBON_VERTEX_COUNT)
            .indexCount(RIBBON_INDEX_COUNT)
            .indexType(MeshIndexSizeType.UNSIGNED_SHORT)
            .boundingBox(
                BoundingBox(
                    Vector3(0f, height * 0.5f, 0f),
                    Vector3(width * 0.5f, height * 0.5f, 0.12f),
                ),
            )
            .addSubMesh(0, RIBBON_INDEX_COUNT, 0, name, PrimitiveType.TRIANGLES)
            .build()
    }
    return descriptor.use { value ->
        RawMesh.create(value).use { raw ->
            check(
                raw.populateVertexBuffer(0) { buffer ->
                    buffer.asFloatBuffer().put(vertexData)
                    true
                },
            )
            check(
                raw.populateIndicesBuffer { buffer ->
                    buffer.asShortBuffer().put(indexData)
                    true
                },
            )
            MeshResource.createWithRawMesh(raw, name).apply { toGlobal() }
        }
    }
}

private const val RIBBON_ROWS = 15
private const val RIBBON_COLUMNS = 4
private const val RIBBON_VERTEX_COUNT = RIBBON_ROWS * RIBBON_COLUMNS
private const val RIBBON_INDEX_COUNT = (RIBBON_ROWS - 1) * (RIBBON_COLUMNS - 1) * 6
private const val RIBBON_FLOATS_PER_VERTEX = 10
private const val RIBBON_VERTEX_STRIDE_BYTES = RIBBON_FLOATS_PER_VERTEX * 4
private val RIBBON_COLUMN_FACTORS = floatArrayOf(-1f, -0.34f, 0.34f, 1f)

private fun lerp(start: Float, end: Float, amount: Float): Float =
    start + (end - start) * amount

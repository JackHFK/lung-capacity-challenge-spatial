package com.example.lungcapacity.pcda76db4.ui.game.scene

import com.example.lungcapacity.pcda76db4.domain.model.CakeTierSpec
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.domain.model.cakeTiers
import com.example.lungcapacity.pcda76db4.domain.usecase.CakeAssemblyTimeline
import com.example.lungcapacity.pcda76db4.domain.usecase.CakeAssemblyPartKind
import com.example.lungcapacity.pcda76db4.domain.usecase.ChallengeLayoutUseCase
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DynamicShadowComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshInstancesResource
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Transform
import com.pico.spatial.core.math.Vector3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

internal class CakeSceneBuilder(
    private val difficulty: Difficulty,
    private val resources: CakeSceneResources,
) {
    private val assemblyNodes = mutableListOf<CakeAssemblyNode>()
    private val candles = mutableListOf<CakeCandleVisual>()

    private val easySeedInstances by lazy {
        resources.instances("easy-strawberry-seeds", easySeedTransforms())
    }
    private val tierSeedInstances by lazy {
        resources.instances("tier-strawberry-seeds", tierSeedTransforms())
    }
    private val hardTierSeedInstances by lazy {
        resources.instances(
            "hard-tier-strawberry-seeds",
            tierSeedTransforms().filterIndexed { index, _ -> index % 4 == 0 },
        )
    }
    private val raspberryInstances by lazy {
        resources.instances(
            "raspberry-drupelets",
            drupeletTransforms(blackberry = false, count = HARD_DRUPELET_COUNT),
        )
    }
    private val blackberryInstances by lazy {
        resources.instances(
            "blackberry-drupelets",
            drupeletTransforms(blackberry = true, count = HARD_DRUPELET_COUNT),
        )
    }
    private val hardStrawberryLeafInstances by lazy {
        resources.instances(
            "hard-strawberry-leaves",
            List(HARD_STRAWBERRY_LEAF_COUNT) { index ->
                val angle = index * TWO_PI / HARD_STRAWBERRY_LEAF_COUNT
                MeshInstancesResource.Instance(
                    "hard-strawberry-leaf-$index",
                    Transform(
                        Vector3.ZERO,
                        EulerAngles(
                            79f,
                            Math.toDegrees(angle.toDouble()).toFloat(),
                            0f,
                        ),
                        Vector3(1f),
                    ),
                )
            },
        )
    }

    suspend fun build(
        parent: Entity,
        preview: Boolean,
        awaitLooperTurn: suspend () -> Unit,
        onProgress: (Float) -> Unit = {},
    ): CakeSceneInstance {
        onProgress(0f)
        val pivotY = difficulty.viewPivotY()
        val scale = if (preview) difficulty.previewScale() else STAGE_SCALE
        val modelBaseY = if (preview) PREVIEW_TRANSLATION_Y else STAGE_BASE_Y
        val cakeRoot = Entity().apply {
            setName("cake-rotation-root")
            components.get(TransformComponent::class.java)?.apply {
                setPosition(
                    Vector3(
                        if (preview) PREVIEW_TRANSLATION_X else 0f,
                        modelBaseY + scale * pivotY,
                        if (preview) 0f else STAGE_DEPTH_Z,
                    ),
                )
                setScaleVector(Vector3(scale))
            }
            parent.addChild(this)
        }
        val modelRoot = Entity().apply {
            setName("cake-model-root")
            components.get(TransformComponent::class.java)?.setPosition(
                Vector3(0f, -pivotY, 0f),
            )
            cakeRoot.addChild(this)
        }

        when (difficulty) {
            Difficulty.EASY -> buildEasy(modelRoot)
            Difficulty.NORMAL -> buildTiered(modelRoot, vintage = true)
            Difficulty.HARD -> buildTiered(modelRoot, vintage = false)
        }
        onProgress(0.55f)
        awaitLooperTurn()
        buildCandles(modelRoot, awaitLooperTurn, onProgress)

        val hitTarget = Entity().apply {
            setName("cake-rotation-target")
            components.set(InteractableComponent())
            components.set(
                CollisionComponent(
                    listOf(resources.sphereShape(1.45f)),
                    resources.physicsMaterial,
                ),
            )
            components.get(TransformComponent::class.java)?.setPosition(
                Vector3(0f, pivotY, 0f),
            )
            modelRoot.addChild(this)
        }
        return CakeSceneInstance(
            difficulty = difficulty,
            root = cakeRoot,
            hitTarget = hitTarget,
            assemblyNodes = assemblyNodes,
            candles = candles,
        ).also {
            it.resetAssembly()
            onProgress(1f)
        }
    }

    private fun buildEasy(parent: Entity) {
        val stand = part(
            parent = parent,
            id = "wooden-stand",
            position = Vector3.ZERO,
            tierIndex = 0,
            kind = CakeAssemblyPartKind.SUPPORT,
            sequence = 0,
            travel = 0f,
        )
        model(stand, resources.meshes.woodenStand(), resources.materials.wood)
        listOf(1.2f, 1.27f).forEach { radius ->
            model(
                stand,
                resources.meshes.torus(radius, 0.005f),
                resources.materials.wood,
                position = Vector3(0f, 0.318f, 0f),
                rotation = EulerAngles(90f, 0f, 0f),
            )
        }
        modelPart(
            parent,
            "scalloped-board",
            Vector3(0f, 0.346f, 0f),
            0,
            CakeAssemblyPartKind.SUPPORT,
            1,
            0.12f * 0.85f,
            resources.meshes.scallopedBoard(),
            resources.materials.gold,
        )
        modelPart(
            parent,
            "cream-body",
            Vector3(0f, 0.36f, 0f),
            0,
            CakeAssemblyPartKind.LARGE_BODY,
            2,
            0.28f * 0.85f,
            resources.meshes.easyCreamBody(),
            resources.materials.easyCream,
        )
        modelPart(
            parent,
            "top-icing",
            Vector3(0f, 0.944f, 0f),
            0,
            CakeAssemblyPartKind.LARGE_BODY,
            3,
            0.42f * 0.85f,
            resources.meshes.easyTopIcing(),
            resources.materials.easyCream,
        )
        repeat(6) { index ->
            val angle = index * TWO_PI / 6f
            val rosetteAngle = angle + PI.toFloat() / 6f
            val berry = part(
                parent,
                "berry-$index",
                Vector3(sin(angle) * 0.77f, 0.957f, cos(angle) * 0.77f),
                0,
                CakeAssemblyPartKind.SMALL,
                index * 2 + 1,
                (0.70f + index * 0.012f) * 0.85f,
            )
            berry.components.get(TransformComponent::class.java)?.setEulerAngles(
                EulerAngles(
                    Math.toDegrees((0.12f * cos(angle)).toDouble()).toFloat(),
                    Math.toDegrees(angle.toDouble()).toFloat(),
                    Math.toDegrees((0.14f * sin(angle)).toDouble()).toFloat(),
                ),
            )
            model(
                berry,
                resources.meshes.strawberry(),
                resources.materials.easyStrawberry,
                dynamicShadow = false,
            )
            instanceModel(
                berry,
                resources.meshes.seed,
                resources.materials.seed,
                easySeedInstances,
                dynamicShadow = false,
            )
            val leaves = Entity().apply {
                setName("berry-$index-leaves")
                components.get(TransformComponent::class.java)?.setPosition(
                    Vector3(0f, 0.286f, 0f),
                )
                berry.addChild(this)
            }
            repeat(7) { leafIndex ->
                val leafAngle = leafIndex * TWO_PI / 7f
                val length = 0.14f + seededNoise(index * 10 + leafIndex) * 0.065f
                val width = 0.027f + 0.009f * seededNoise(leafIndex)
                model(
                    leaves,
                    resources.meshes.leaf(length, width, 0.035f),
                    resources.materials.leaf,
                    rotation = EulerAngles(
                        76f - seededNoise(leafIndex) * 12f,
                        Math.toDegrees(leafAngle.toDouble()).toFloat(),
                        0f,
                    ),
                    dynamicShadow = false,
                )
            }
            modelPart(
                parent,
                "rosette-$index",
                Vector3(
                    sin(rosetteAngle) * 0.77f,
                    0.96f,
                    cos(rosetteAngle) * 0.77f,
                ),
                0,
                CakeAssemblyPartKind.SMALL,
                index * 2,
                (0.53f + index * 0.012f) * 0.85f,
                resources.meshes.rosette(),
                resources.materials.easyCream,
                dynamicShadow = false,
            )
        }
        val inscription = part(
            parent,
            "inscription",
            Vector3(0f, 0.975f, 0f),
            0,
            CakeAssemblyPartKind.SMALL,
            12,
            1.1f,
        )
        model(
            inscription,
            resources.inscription.happy,
            resources.materials.redIcing,
            position = Vector3(0f, 0f, -0.13f),
            rotation = EulerAngles(-90f, 0f, 0f),
        )
        model(
            inscription,
            resources.inscription.birthday,
            resources.materials.redIcing,
            position = Vector3(0f, 0f, 0.1f),
            rotation = EulerAngles(-90f, 0f, 0f),
        )
    }

    private fun buildTiered(parent: Entity, vintage: Boolean) {
        modelPart(
            parent,
            "silver-board",
            Vector3.ZERO,
            0,
            CakeAssemblyPartKind.SUPPORT,
            0,
            0.5f,
            resources.meshes.tierBody(1.145f, 0.03f),
            resources.materials.silver,
        )
        difficulty.cakeTiers.forEach { tier ->
            val tierTravel = 0.5f + (0.10f + tier.index * 0.15f) * 0.5f
            modelPart(
                parent,
                "tier-${tier.index}",
                Vector3(0f, tier.baseY, 0f),
                tier.index,
                CakeAssemblyPartKind.LARGE_BODY,
                tier.index + 1,
                tierTravel,
                resources.meshes.tierBody(tier.radius, tier.height),
                resources.materials.tierCream,
            )
            listOf("top", "base").forEachIndexed { edgeIndex, edge ->
                val compact = vintage && edge == "top"
                val travel = 0.5f + (0.55f + tier.index * 0.045f) * 0.5f
                modelPart(
                    parent,
                    "border-${tier.index}-$edge",
                    Vector3(
                        0f,
                        if (edge == "top") tier.surfaceY - 0.012f else tier.baseY + 0.021f,
                        0f,
                    ),
                    tier.index,
                    CakeAssemblyPartKind.SMALL,
                    edgeIndex,
                    travel,
                    resources.meshes.shellRing(tier.radius - 0.012f, compact),
                    resources.materials.tierCream,
                )
            }
            if (vintage) {
                buildVintageSwag(parent, tier)
            } else {
                buildCreamDots(parent, tier)
            }
            buildTierFruit(parent, tier, vintage)
        }
    }

    private fun buildVintageSwag(parent: Entity, tier: CakeTierSpec) {
        val travel = 0.5f + (0.61f + tier.index * 0.035f) * 0.5f
        modelPart(
            parent,
            "swag-${tier.index}",
            Vector3(0f, tier.surfaceY, 0f),
            tier.index,
            CakeAssemblyPartKind.SMALL,
            2,
            travel,
            resources.meshes.vintageSwag(tier.radius),
            resources.materials.tierCream,
        )
    }

    private fun buildCreamDots(parent: Entity, tier: CakeTierSpec) {
        val travel = 0.5f + (0.59f + tier.index * 0.04f) * 0.5f
        val root = part(
            parent,
            "dots-${tier.index}",
            Vector3(0f, tier.baseY, 0f),
            tier.index,
            CakeAssemblyPartKind.SMALL,
            2,
            travel,
        )
        val values = List(26) { index ->
            val angle = index * 2.39996f
            val y = 0.08f + seededNoise(index + tier.index * 71) * 0.8f * tier.height
            MeshInstancesResource.Instance(
                "dot-${tier.index}-$index",
                Transform(
                    Vector3(
                        sin(angle) * (tier.radius + 0.003f),
                        y,
                        cos(angle) * (tier.radius + 0.003f),
                    ),
                    EulerAngles(0f, 0f, 0f),
                    Vector3(0.014f, 0.016f, 0.014f),
                ),
            )
        }
        instanceModel(
            root,
            resources.meshes.sphere,
            resources.materials.tierCream,
            resources.instances("cream-dots-${tier.index}", values),
            dynamicShadow = false,
        )
    }

    private fun buildTierFruit(
        parent: Entity,
        tier: CakeTierSpec,
        vintage: Boolean,
    ) {
        val count = if (vintage) {
            if (tier.index == 1) 8 else 0
        } else {
            listOf(28, 22, 12)[tier.index]
        }
        repeat(count) { index ->
            val angle = (index + 0.25f) / count * TWO_PI
            val type = if (vintage) FruitType.STRAWBERRY else FruitType.entries[index % 4]
            val top = tier.index == difficulty.cakeTiers.lastIndex
            var radius = if (vintage) {
                0.22f + 0.07f * (index % 2)
            } else {
                tier.radius - 0.075f
            }
            var scale = if (vintage) {
                1.05f
            } else {
                0.78f + seededNoise(index + tier.index * 103) * 0.18f
            }
            if (top) {
                radius = if (vintage) {
                    0.20f + 0.04f * (index % 2)
                } else {
                    0.12f + 0.035f * (index % 2)
                }
            } else {
                radius = tier.radius - 0.055f
            }
            if (!vintage) scale = if (top) 0.65f else 0.68f
            val travel = 0.5f + (0.65f + tier.index * 0.055f) * 0.5f
            val fruit = part(
                parent,
                "fruit-${tier.index}-$index",
                Vector3(
                    sin(angle) * radius,
                    tier.surfaceY - 0.012f + if (top && index % 3 == 0) 0.015f else 0f,
                    cos(angle) * radius,
                ),
                tier.index,
                CakeAssemblyPartKind.SMALL,
                index + 3,
                travel,
            )
            fruit.components.get(TransformComponent::class.java)?.apply {
                setScaleVector(Vector3(scale))
                setEulerAngles(
                    EulerAngles(0f, Math.toDegrees((index * 0.63).toDouble()).toFloat(), 0f),
                )
            }
            when (type) {
                FruitType.STRAWBERRY -> buildTierStrawberry(fruit)
                FruitType.RASPBERRY -> instanceModel(
                    fruit,
                    resources.meshes.sphere,
                    resources.materials.raspberry,
                    raspberryInstances,
                    dynamicShadow = false,
                )
                FruitType.BLACKBERRY -> instanceModel(
                    fruit,
                    resources.meshes.sphere,
                    resources.materials.blackberry,
                    blackberryInstances,
                    dynamicShadow = false,
                )
                FruitType.CHERRY -> {
                    model(
                        fruit,
                        resources.meshes.sphere,
                        resources.materials.cherry,
                        position = Vector3(0f, 0.06f, 0f),
                        scale = Vector3(0.13f),
                        dynamicShadow = false,
                    )
                    model(
                        fruit,
                        resources.meshes.cherryStem(),
                        resources.materials.stem,
                        dynamicShadow = false,
                    )
                }
            }
        }
    }

    private fun buildTierStrawberry(parent: Entity) {
        model(
            parent,
            resources.meshes.strawberry(detailed = true),
            resources.materials.tierStrawberry,
            scale = Vector3(0.58f, 0.67f, 0.58f),
            dynamicShadow = false,
        )
        instanceModel(
            parent,
            resources.meshes.seed,
            resources.materials.seed,
            if (difficulty == Difficulty.HARD) hardTierSeedInstances else tierSeedInstances,
            dynamicShadow = false,
        )
        if (difficulty == Difficulty.HARD) {
            val leaves = Entity().apply {
                setName("radial-strawberry-leaves")
                components.get(TransformComponent::class.java)?.setPosition(
                    Vector3(0f, 0.19f, 0f),
                )
                parent.addChild(this)
            }
            instanceModel(
                parent = leaves,
                mesh = resources.meshes.leaf(0.09f, 0.022f, 0.024f),
                material = resources.materials.leaf,
                instances = hardStrawberryLeafInstances,
                dynamicShadow = false,
            )
        }
    }

    private suspend fun buildCandles(
        parent: Entity,
        awaitLooperTurn: suspend () -> Unit,
        onProgress: (Float) -> Unit,
    ) {
        val placements = ChallengeLayoutUseCase()(difficulty)
        placements.forEachIndexed { index, placement ->
            val root = Entity().apply {
                setName("candle-${placement.index}")
                enabled = false
                components.get(TransformComponent::class.java)?.setPosition(
                    Vector3(placement.x, placement.y, placement.z),
                )
                parent.addChild(this)
            }
            val anchor = Entity().apply {
                setName("candle-${placement.index}-insertion-pivot")
                enabled = false
                components.get(TransformComponent::class.java)?.setScaleVector(
                    Vector3(if (difficulty == Difficulty.EASY) EASY_CANDLE_SCALE else 1f),
                )
                root.addChild(this)
            }
            model(
                anchor,
                resources.meshes.candleBody(),
                resources.materials.candleWax,
            )
            model(
                anchor,
                resources.meshes.unitCylinder,
                resources.materials.wick,
                position = Vector3(0f, 0.335f, 0f),
                scale = Vector3(0.004f, 0.021f, 0.004f),
            )
            val flame = FlameVisual.create(
                parent = anchor,
                index = placement.index,
                meshes = resources.meshes,
                ribbons = resources.flameMeshes,
                materials = resources.materials,
            ).also { it.setEnabled(false) }
            candles += CakeCandleVisual(
                root = root,
                anchor = anchor,
                anchorTransform = requireNotNull(
                    anchor.components.get(TransformComponent::class.java),
                ),
                placementIndex = placement.index,
                tierIndex = placement.tierIndex,
                slotIndex = placement.index % CANDLES_PER_TIER,
                flame = flame,
            )
            onProgress(0.58f + 0.37f * (index + 1).toFloat() / placements.size)
            if ((index + 1) % 4 == 0) {
                awaitLooperTurn()
            }
        }
    }

    private fun modelPart(
        parent: Entity,
        id: String,
        position: Vector3,
        tierIndex: Int,
        kind: CakeAssemblyPartKind,
        sequence: Int,
        travel: Float,
        mesh: MeshResource,
        material: Material,
        dynamicShadow: Boolean = true,
    ): Entity {
        val root = part(parent, id, position, tierIndex, kind, sequence, travel)
        model(root, mesh, material, dynamicShadow = dynamicShadow)
        return root
    }

    private fun part(
        parent: Entity,
        id: String,
        position: Vector3,
        tierIndex: Int,
        kind: CakeAssemblyPartKind,
        sequence: Int,
        travel: Float,
    ): Entity {
        val entity = Entity().apply {
            setName(id)
            enabled = false
            parent.addChild(this)
        }
        val transform = requireNotNull(entity.components.get(TransformComponent::class.java))
        transform.setPosition(position)
        assemblyNodes += CakeAssemblyNode(
            id = id,
            entity = entity,
            transform = transform,
            finalPosition = position,
            tierIndex = tierIndex,
            kind = kind,
            sequence = sequence,
            travel = travel,
        )
        return entity
    }

    private fun model(
        parent: Entity,
        mesh: MeshResource,
        material: Material,
        position: Vector3 = Vector3.ZERO,
        scale: Vector3 = Vector3(1f),
        rotation: EulerAngles? = null,
        dynamicShadow: Boolean = true,
    ): Entity =
        Entity().apply {
            components.set(ModelComponent(mesh, material))
            if (dynamicShadow) {
                components.set(DynamicShadowComponent(true, true, false))
            }
            components.get(TransformComponent::class.java)?.apply {
                setPosition(position)
                setScaleVector(scale)
                rotation?.let(::setEulerAngles)
            }
            parent.addChild(this)
        }

    private fun instanceModel(
        parent: Entity,
        mesh: MeshResource,
        material: Material,
        instances: MeshInstancesResource,
        dynamicShadow: Boolean = true,
    ): Entity =
        Entity().apply {
            components.set(
                ModelComponent(mesh, material).apply {
                    meshInstances = instances
                },
            )
            if (dynamicShadow) {
                components.set(DynamicShadowComponent(true, true, false))
            }
            parent.addChild(this)
        }

    private fun easySeedTransforms(): List<MeshInstancesResource.Instance> {
        val values = mutableListOf<MeshInstancesResource.Instance>()
        for (row in 1 until 12) {
            val t = row / 12f
            val radius = strawberryRadius(t)
            val count = max(6, (radius / 0.012f).roundToInt())
            repeat(count) { index ->
                val angle = (index + if (row % 2 == 0) 0f else 0.5f) / count * TWO_PI
                values += MeshInstancesResource.Instance(
                    "easy-seed-${values.size}",
                    Transform(
                        Vector3(
                            sin(angle) * radius * 0.985f,
                            t * 0.32f,
                            cos(angle) * radius * 0.985f,
                        ),
                        EulerAngles(0f, Math.toDegrees(angle.toDouble()).toFloat(), 0f),
                        Vector3(0.0058f, 0.0096f, 0.0028f),
                    ),
                )
            }
        }
        return values
    }

    private fun tierSeedTransforms(): List<MeshInstancesResource.Instance> {
        val values = mutableListOf<MeshInstancesResource.Instance>()
        for (row in 1..9) {
            val t = row / 11f
            val radius = strawberryRadius(t) * 0.58f
            repeat(11) { index ->
                val angle = (index + if (row % 2 == 0) 0f else 0.5f) / 11f * TWO_PI
                values += MeshInstancesResource.Instance(
                    "tier-seed-${values.size}",
                    Transform(
                        Vector3(
                            sin(angle) * radius * 0.982f,
                            t * 0.32f * 0.67f,
                            cos(angle) * radius * 0.982f,
                        ),
                        EulerAngles(0f, Math.toDegrees(angle.toDouble()).toFloat(), 0f),
                        Vector3(0.0036f, 0.0056f, 0.002f),
                    ),
                )
            }
        }
        return values
    }

    private fun drupeletTransforms(
        blackberry: Boolean,
        count: Int,
    ): List<MeshInstancesResource.Instance> =
        List(count) { index ->
            val y = 1f - 2f * (index + 0.5f) / count
            val angle = index * 2.39996f
            val horizontal = sqrt(1f - y * y)
            MeshInstancesResource.Instance(
                "${if (blackberry) "blackberry" else "raspberry"}-$index",
                Transform(
                    Vector3(
                        cos(angle) * horizontal * 0.043f,
                        (y + 1f) * 0.055f,
                        sin(angle) * horizontal * 0.043f,
                    ),
                    EulerAngles(0f, 0f, 0f),
                    Vector3(0.028f, if (blackberry) 0.0368f else 0.032f, 0.028f),
                ),
            )
        }

    private fun seededNoise(seed: Int): Float {
        val value = sin(seed * 127.1f + 311.7f) * 43758.5453f
        return value - floor(value)
    }

    private fun strawberryRadius(value: Float): Float =
        0.17f *
            sin(PI.toFloat() * value).coerceAtLeast(0f).pow(0.57f) *
            (0.6f + 0.55f * value)

    private fun Difficulty.viewPivotY(): Float =
        when (this) {
            Difficulty.EASY -> 0.73f
            Difficulty.NORMAL -> 0.915f
            Difficulty.HARD -> 1.04f
        }

    private fun Difficulty.previewScale(): Float =
        if (this == Difficulty.EASY) EASY_PREVIEW_SCALE else PREVIEW_SCALE

    private enum class FruitType {
        STRAWBERRY,
        RASPBERRY,
        BLACKBERRY,
        CHERRY,
    }

    private companion object {
        const val PREVIEW_SCALE = 0.11811571f
        const val EASY_PREVIEW_SCALE = 0.106f
        const val PREVIEW_TRANSLATION_X = 0.10f
        const val PREVIEW_TRANSLATION_Y = -0.17107073f
        const val STAGE_SCALE = 0.44f
        const val STAGE_BASE_Y = 0.50f
        const val STAGE_DEPTH_Z = -1.55f
        const val EASY_CANDLE_SCALE = 1.55f
        const val CANDLES_PER_TIER = 8
        const val HARD_DRUPELET_COUNT = 16
        const val HARD_STRAWBERRY_LEAF_COUNT = 3
        const val TWO_PI = (PI * 2.0).toFloat()
    }
}

internal class CakeSceneInstance(
    private val difficulty: Difficulty,
    val root: Entity,
    val hitTarget: Entity,
    private val assemblyNodes: List<CakeAssemblyNode>,
    val candles: List<CakeCandleVisual>,
) {
    var assemblyComplete: Boolean = false
        private set

    private var yawDegrees = 0f

    fun resetAssembly() {
        assemblyComplete = false
        updateAssembly(0f)
    }

    fun updateAssembly(elapsedSeconds: Float) {
        assemblyNodes.forEach { node ->
            val sample = CakeAssemblyTimeline.part(
                elapsedSeconds = elapsedSeconds,
                difficulty = difficulty,
                tierIndex = node.tierIndex,
                kind = node.kind,
                sequence = node.sequence,
                travel = node.travel,
            )
            node.entity.enabled = sample.visible
            node.transform.setPosition(
                Vector3(
                    node.finalPosition.x,
                    node.finalPosition.y + sample.verticalOffset,
                    node.finalPosition.z,
                ),
            )
        }
        candles.forEach { candle ->
            val sample = CakeAssemblyTimeline.candle(
                elapsedSeconds = elapsedSeconds,
                difficulty = difficulty,
                tierIndex = candle.tierIndex,
                slotIndex = candle.slotIndex,
            )
            candle.root.enabled = sample.visible
            candle.anchor.enabled = sample.visible
            candle.flame.setEnabled(sample.visible)
            candle.anchorTransform.setPosition(Vector3(0f, sample.verticalOffset, 0f))
            candle.anchorTransform.setEulerAngles(
                EulerAngles(
                    0f,
                    0f,
                    Math.toDegrees(sample.tiltRadians.toDouble()).toFloat(),
                ),
            )
        }
        assemblyComplete = elapsedSeconds >= CakeAssemblyTimeline.assemblySeconds(difficulty)
    }

    fun rotateBy(deltaDegrees: Float) {
        yawDegrees = (yawDegrees + deltaDegrees) % 360f
        root.components.get(TransformComponent::class.java)?.setEulerAngles(
            EulerAngles(0f, yawDegrees, 0f),
        )
        candles.forEach { it.flame.setBillboardYaw(-yawDegrees) }
    }

    fun getVisualBounds(relativeTo: Entity): BoundingBox =
        root.getVisualBounds(relativeTo, true, true)

}

internal data class CakeCandleVisual(
    val root: Entity,
    val anchor: Entity,
    val anchorTransform: TransformComponent,
    val placementIndex: Int,
    val tierIndex: Int,
    val slotIndex: Int,
    val flame: FlameVisual,
)

internal data class CakeAssemblyNode(
    val id: String,
    val entity: Entity,
    val transform: TransformComponent,
    val finalPosition: Vector3,
    val tierIndex: Int,
    val kind: CakeAssemblyPartKind,
    val sequence: Int,
    val travel: Float,
)

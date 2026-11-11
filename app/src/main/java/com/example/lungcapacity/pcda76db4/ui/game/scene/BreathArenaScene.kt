package com.example.lungcapacity.pcda76db4.ui.game.scene

import android.util.Log
import com.example.lungcapacity.pcda76db4.domain.model.ChallengeConfig
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.domain.model.GrowthNodeId
import com.example.lungcapacity.pcda76db4.domain.model.BubbleResponse
import com.example.lungcapacity.pcda76db4.domain.usecase.BreathEmissionGate
import com.example.lungcapacity.pcda76db4.domain.usecase.BubbleImpact
import com.example.lungcapacity.pcda76db4.domain.usecase.BubbleGrowth
import com.example.lungcapacity.pcda76db4.domain.usecase.FlameResponse
import com.example.lungcapacity.pcda76db4.domain.usecase.Point3
import com.pico.spatial.core.container.SpatialViewContent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Vector3
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject

private const val POP_SECONDS = 0.14f

internal data class CakeLoadingProgress(
    val sharedResources: Float = 0f,
    val sceneBuild: Float = 0f,
)

internal class BreathArenaScene(
    private val config: ChallengeConfig,
    private val preview: Boolean,
    private val onCandleExtinguished: () -> Unit = {},
    private val onLoadingProgress: (CakeLoadingProgress) -> Unit = {},
) : FrameParticipant {
    private val turnAwaiter = MainLooperTurnAwaiter()
    private val ready = CompletableDeferred<Unit>()
    private var resources: CakeSceneResources? = null
    private var initializationJob: Job? = null
    private var root: Entity? = null
    private var cake: CakeSceneInstance? = null
    private val candleTargets = mutableListOf<CandleTarget>()
    private val bubbles = mutableListOf<BubbleSlot>()
    private val emissionGate = BreathEmissionGate()
    private var bubbleResources: SoapBubbleResources? = null
    private var elapsed = 0f
    private var assemblyElapsed = 0f
    private var assemblyBoundsLogged = false
    private var nextBubble = 0
    private var chargeTime = 0f
    private var sharedResourceProgress = 0f
    private var sceneBuildProgress = 0f

    @Volatile
    private var isVisible = true

    @Volatile
    private var phase: ChallengePhase = if (preview) ChallengePhase.RUNNING else ChallengePhase.COUNTDOWN

    @Volatile
    private var headPose: HeadEmissionPose? = null

    fun attach(content: SpatialViewContent, scope: CoroutineScope) {
        if (root != null) return
        reportSharedResourceProgress(0.01f)
        val sceneRoot = Entity().apply {
            enabled = isVisible
            addCakeLights(this)
        }
        content.addEntity(sceneRoot)
        root = sceneRoot
        initializationJob = scope.launch {
            try {
                val createdResources = CakeSceneResources.create(
                    awaitLooperTurn = turnAwaiter::await,
                    onProgress = { progress ->
                        reportSharedResourceProgress(progress)
                    },
                )
                if (root !== sceneRoot) {
                    createdResources.close()
                    return@launch
                }
                resources = createdResources
                turnAwaiter.await()
                val instance = CakeSceneBuilder(
                    config.difficulty,
                    createdResources,
                ).build(
                    parent = sceneRoot,
                    preview = preview,
                    awaitLooperTurn = turnAwaiter::await,
                    onProgress = { progress ->
                        reportSceneBuildProgress(progress)
                    },
                )
                cake = instance
                instance.hitTarget.enabled = phase != ChallengePhase.RESULT
                instance.updateAssembly(assemblyElapsed)
                instance.candles.forEach { visual ->
                    candleTargets += CandleTarget(
                        candleRoot = visual.root,
                        candleAnchor = visual.anchor,
                        flame = visual.flame,
                        response = FlameResponse(config.difficulty.flameThreshold),
                    )
                }
                if (!preview) {
                    turnAwaiter.await()
                    createBubbles(sceneRoot)
                }
                ready.complete(Unit)
                reportSceneBuildProgress(1f)
            } catch (cancelled: CancellationException) {
                ready.cancel(cancelled)
                throw cancelled
            } catch (error: Throwable) {
                ready.completeExceptionally(error)
                throw error
            }
        }
    }

    private fun reportSharedResourceProgress(progress: Float) {
        val next = progress.coerceIn(sharedResourceProgress, 1f)
        if (next == sharedResourceProgress) return
        sharedResourceProgress = next
        reportLoadingProgress()
    }

    private fun reportSceneBuildProgress(progress: Float) {
        val next = progress.coerceIn(sceneBuildProgress, 1f)
        if (next == sceneBuildProgress) return
        sceneBuildProgress = next
        reportLoadingProgress()
    }

    private fun reportLoadingProgress() {
        Log.i(
            "CakeLoadProgress",
            "difficulty=${config.difficulty.name.lowercase()} " +
                "resources=$sharedResourceProgress scene=$sceneBuildProgress",
        )
        onLoadingProgress(
            CakeLoadingProgress(
                sharedResources = sharedResourceProgress,
                sceneBuild = sceneBuildProgress,
            ),
        )
    }

    suspend fun awaitReady() {
        ready.await()
    }

    fun submit(newPhase: ChallengePhase) {
        if (newPhase == ChallengePhase.COUNTDOWN && phase != ChallengePhase.COUNTDOWN) {
            resetRun()
        }
        phase = newPhase
        cake?.hitTarget?.enabled = newPhase != ChallengePhase.RESULT
    }

    fun submitBreathInput(pose: HeadEmissionPose) {
        headPose = pose
    }

    fun isCakeRotationTarget(entity: Entity?): Boolean = entity == cake?.hitTarget

    fun rotateCake(deltaPixels: Float) {
        if (!preview) {
            cake?.rotateBy(deltaPixels * 0.16f)
        }
    }

    fun setVisible(visible: Boolean) {
        isVisible = visible
        root?.enabled = visible
    }

    fun restartAssembly() {
        resetRun()
        phase = ChallengePhase.COUNTDOWN
    }

    override fun update(context: SceneUpdateContext) {
        val owner = root ?: return
        if (owner.scene != context.scene) return
        if (!isVisible) return
        val assemblyDt = context.deltaTime.coerceIn(0f, 0.25f)
        val dt = context.deltaTime.coerceIn(0f, 0.05f)
        elapsed += dt
        val pose = headPose
        val currentCake = cake
        if (phase != ChallengePhase.RESULT && currentCake != null && !currentCake.assemblyComplete) {
            assemblyElapsed += assemblyDt
            currentCake.updateAssembly(assemblyElapsed)
        }
        if (preview) {
            currentCake?.rotateBy(HOME_ROTATION_DEGREES_PER_SECOND * dt)
        }
        if (currentCake?.assemblyComplete == true && !assemblyBoundsLogged) {
            val bounds = currentCake.getVisualBounds(owner)
            Log.i(
                "CakeBoundsProbe",
                "CAKE_GAME_BOUNDS " + JSONObject()
                    .put("difficulty", config.difficulty.name.lowercase())
                    .put("preview", preview)
                    .put(
                        "aabb_m",
                        JSONObject()
                            .put("min", bounds.min.toJson())
                            .put("max", bounds.max.toJson())
                            .put("size", bounds.size.toJson()),
                    ),
            )
            assemblyBoundsLogged = true
        }
        candleTargets.forEach { target ->
            target.response.update(dt)
            target.flame.update(dt)
            target.visible = target.candleRoot.enabled && target.candleAnchor.enabled
            if (!preview && target.visible && !target.response.extinguished) {
                val bounds = target.flame.root.getVisualBounds(owner, true, true)
                target.position = bounds.center
                target.halfExtent = bounds.halfExtent
            }
        }
        if (preview) return
        val effectiveIntensity = pose?.intensity ?: 0f
        chargeTime = if (phase == ChallengePhase.RUNNING &&
            effectiveIntensity >= BubbleResponse.MIN_ACTIVE_INTENSITY
        ) chargeTime + dt else 0f
        repeat(
            emissionGate.update(
                deltaSeconds = dt,
                phase = phase,
                intensity = effectiveIntensity,
                densityRank = rank(GrowthNodeId.DENSITY),
                preview = false,
            ),
        ) {
            launchBubble(effectiveIntensity, pose)
        }
        updateBubbles(dt, pose)
        if (phase == ChallengePhase.RESULT) {
            candleTargets.forEach { it.flame.setEnabled(false) }
        }
    }

    fun dispose() {
        initializationJob?.cancel()
        initializationJob = null
        root?.destroy(true)
        root = null
        cake = null
        candleTargets.clear()
        bubbles.clear()
        bubbleResources?.close()
        bubbleResources = null
        resources?.close()
        resources = null
        emissionGate.reset()
        if (!ready.isCompleted) {
            ready.cancel(CancellationException("Scene disposed before initialization completed"))
        }
    }

    private fun firstImpact(start: Vector3, end: Vector3, radius: Vector3): BubbleContact? {
        var result: BubbleContact? = null
        candleTargets.forEachIndexed { index, target ->
            if (!target.visible || target.response.extinguished) return@forEachIndexed
            val fraction = BubbleImpact.fraction(
                start.point(), end.point(), target.position.point(),
                target.halfExtent.point(), radius.point(),
            )
            if (fraction != null && fraction < (result?.fraction ?: Float.POSITIVE_INFINITY)) {
                result = BubbleContact(fraction, index)
            }
        }
        return result
    }

    private fun createBubbles(parent: Entity) {
        val resources = SoapBubbleResources().also { bubbleResources = it }
        repeat(BubbleResponse.MAX_PARTICLES) {
            val material = resources.material()
            val entity = Entity().apply {
                enabled = false
                components.set(ModelComponent(resources.mesh, material))
            }
            parent.addChild(entity)
            bubbles += BubbleSlot(entity, material)
        }
    }

    private fun launchBubble(intensity: Float, pose: HeadEmissionPose?) {
        if (pose == null) return
        // Never overwrite a flying bubble when the pool is full.
        val slot = bubbles.firstOrNull { !it.entity.enabled } ?: return
        nextBubble += 1
        val response = BubbleResponse.fromIntensity(intensity, rank(GrowthNodeId.DENSITY))
        val spreadDegrees = 3f + rank(GrowthNodeId.SPREAD) * 2f
        val spreadRadians = sin(elapsed * 11f + nextBubble * 1.7f) *
            spreadDegrees *
            PI.toFloat() /
            180f
        // Spread in head-local axes before mapping the whole ray to world and scene.
        val direction = Vector3(
            sin(spreadRadians),
            sin(nextBubble * 1.9f) * 0.045f,
            -cos(spreadRadians),
        )
        val forceMultiplier = 1f + rank(GrowthNodeId.FORCE) * 0.04f
        val lifeMultiplier = 1f + rank(GrowthNodeId.LIFETIME) * 0.12f
        val densityEnergy = 1f + rank(GrowthNodeId.DENSITY) * 0.04f
        slot.age = 0f
        slot.life = 1f * lifeMultiplier
        slot.popRemaining = 0f
        slot.generation = 0
        slot.split = false
        slot.initialLife = slot.life
        // Higher visual density must not multiply the total damage budget.
        slot.energy = (0.25f * 7.5f * densityEnergy * intensity) /
            response.particlesPerSecond *
            BubbleGrowth.chargeMultiplier(chargeTime, rank(GrowthNodeId.CHARGE))
        val diameter = response.diameterMeters * (0.80f + sin(nextBubble * 2.3f) * 0.20f)
        val unitDirection = normalized(direction)
        val speed = response.speedMetersPerSecond * forceMultiplier
        val owner = root ?: return
        val trajectory = pose.trajectory(owner, unitDirection, speed)
        slot.velocity = trajectory.velocity
        slot.diameter = owner.convertScaleFrom(Vector3(diameter, diameter, diameter), null)
        slot.entity.enabled = true
        slot.material.setOpacity(0.25f)
        slot.entity.components.get(TransformComponent::class.java)?.apply {
            setPosition(trajectory.origin)
            setScaleVector(slot.diameter * 0.30f)
        }
    }

    private fun updateBubbles(dt: Float, pose: HeadEmissionPose?) {
        if (phase != ChallengePhase.RUNNING) {
            bubbles.forEach { it.entity.enabled = false }
            return
        }
        val view = pose?.let { root?.convertPositionFrom(it.position, null) }
        // Descendants begin moving on the next frame, regardless of their pool index.
        bubbles.filter { it.entity.enabled }.forEachIndexed { index, slot ->
            val transform = slot.entity.components.get(TransformComponent::class.java) ?: return@forEachIndexed
            if (slot.popRemaining > 0f) {
                slot.popRemaining -= dt
                val progress = (1f - slot.popRemaining / POP_SECONDS).coerceIn(0f, 1f)
                transform.setScaleVector(slot.diameter * (1f + progress * 0.65f))
                slot.material.setOpacity(1f - progress)
                if (slot.popRemaining <= 0f) slot.entity.enabled = false
                return@forEachIndexed
            }
            val step = minOf(dt, slot.life)
            slot.age += step
            slot.life -= step
            var current = transform.position
            var next = current + slot.velocity * step
            val reveal = (slot.age / 0.12f).coerceIn(0f, 1f)
            val fade = (slot.life / 0.22f).coerceIn(0f, 1f)
            val diameter = slot.diameter * (0.3f + reveal * 0.7f)
            var contact = firstImpact(current, next, diameter * 0.5f)
            transform.setPosition(next)
            transform.setScaleVector(diameter)
            slot.material.setOpacity((0.25f + reveal * 0.75f) * fade)
            if (view != null) {
                val deltaX = view.x - transform.position.x
                val deltaY = view.y - transform.position.y
                val deltaZ = view.z - transform.position.z
                val horizontal = sqrt(deltaX * deltaX + deltaZ * deltaZ)
                val degrees = 180f / PI.toFloat()
                transform.setEulerAngles(
                    EulerAngles(
                        -atan2(deltaY, horizontal) * degrees,
                        atan2(deltaX, deltaZ) * degrees,
                        sin(slot.age * 0.8f + index) * 7f,
                    ),
                )
            }
            contact?.let { hit ->
                transform.setPosition(current + (next - current) * hit.fraction)
                slot.popRemaining = POP_SECONDS
                hit.candleIndex?.let { targetIndex ->
                    hitCandle(
                        index = targetIndex,
                        energy = slot.energy,
                        wave = true,
                        directionX = slot.velocity.x,
                        directionZ = slot.velocity.z,
                    )
                    splitBubble(slot, transform.position)
                }
            }
            if (contact == null && slot.life <= slot.initialLife * 0.4f) {
                splitBubble(slot, transform.position)
            }
            if (slot.popRemaining <= 0f && slot.life <= 0f) {
                slot.entity.enabled = false
            }
        }
    }

    private fun resetRun() {
        emissionGate.reset()
        chargeTime = 0f
        assemblyElapsed = 0f
        assemblyBoundsLogged = false
        cake?.hitTarget?.enabled = true
        cake?.resetAssembly()
        bubbles.forEach { it.entity.enabled = false }
        candleTargets.forEach { target ->
            val wasExtinguished = target.response.extinguished
            target.response.reset()
            if (wasExtinguished) {
                target.flame.ignite()
            }
            target.flame.setEnabled(false)
            target.visible = false
        }
    }

    private fun rank(id: GrowthNodeId): Int = config.growthRanks[id] ?: 0

    private fun splitBubble(parent: BubbleSlot, position: Vector3) {
        if (parent.split) return
        val count = BubbleGrowth.offspringCount(
            parent.generation, rank(GrowthNodeId.FIRST_SPLIT) > 0,
            rank(GrowthNodeId.SECOND_BLOOM) > 0,
        )
        if (count == 0) return
        parent.split = true
        repeat(count) { index ->
            val child = bubbles.firstOrNull { !it.entity.enabled } ?: return@repeat
            val sign = if (count == 2) index * 2f - 1f else index - 1f
            child.generation = parent.generation + 1
            child.age = 0f
            child.life = if (child.generation == 1) 0.65f + rank(GrowthNodeId.LIFETIME) * 0.08f else 0.35f
            child.initialLife = child.life
            child.popRemaining = 0f
            child.split = false
            child.energy = BubbleGrowth.offspringEnergy(parent.energy, parent.generation)
            child.diameter = parent.diameter * 0.70f
            child.velocity = yaw(parent.velocity, -sign * if (count == 2) 5f else 7f)
            if (child.generation == 1 && rank(GrowthNodeId.CHILD_DRIFT) > 0) {
                child.velocity += Vector3(sign * 0.08f, 0f, 0f)
            }
            child.entity.components.get(TransformComponent::class.java)?.apply {
                setPosition(position)
                setScaleVector(child.diameter * 0.30f)
            }
            child.material.setOpacity(0.25f)
            child.entity.enabled = true
        }
    }

    private fun hitCandle(
        index: Int,
        energy: Float,
        wave: Boolean,
        directionX: Float,
        directionZ: Float,
    ) {
        val target = candleTargets[index]
        if (!target.visible || target.response.extinguished) return
        target.flame.hit(
            strength = if (wave) 0.95f else 0.45f,
            directionX = directionX,
            directionZ = directionZ,
        )
        if (!target.response.hit(energy)) return
        target.flame.extinguish()
        onCandleExtinguished()
        if (!wave || rank(GrowthNodeId.EXTINGUISH_WAVE) == 0) return
        val neighbors = listOf(index - 1, index + 1).filter {
            it in candleTargets.indices && !candleTargets[it].response.extinguished
        }
        val sharedEnergy = target.response.threshold * BubbleGrowth.WAVE_ENERGY / maxOf(1, neighbors.size)
        neighbors.forEach { neighbor ->
            hitCandle(
                index = neighbor,
                energy = sharedEnergy,
                wave = false,
                directionX = if (neighbor > index) 1f else -1f,
                directionZ = 0f,
            )
        }
    }

}

private const val HOME_ROTATION_DEGREES_PER_SECOND = 10f

private fun addCakeLights(parent: Entity) {
    fun light(
        color: String,
        intensity: Float,
        rotation: EulerAngles,
        castsShadow: Boolean,
    ) {
        parent.addChild(
            Entity().apply {
                components.set(
                    DirectionalLightComponent(
                        Color4.fromSRGBHex(color),
                        intensity,
                        castsShadow,
                    ),
                )
                components.get(TransformComponent::class.java)?.setEulerAngles(rotation)
            },
        )
    }
    light("0xFFFFFFFF", 560f, EulerAngles(-38f, -36f, 0f), castsShadow = true)
    light("0xEAF0FFFF", 185f, EulerAngles(-25f, 128f, 0f), castsShadow = false)
    light("0xFFF7EAFF", 78f, EulerAngles(75f, 8f, 0f), castsShadow = false)
}

private data class BubbleSlot(
    val entity: Entity,
    val material: UnlitMaterial,
    var age: Float = 0f,
    var life: Float = 0f,
    var popRemaining: Float = 0f,
    var energy: Float = 0f,
    var diameter: Vector3 = Vector3(0.055f, 0.055f, 0.055f),
    var velocity: Vector3 = Vector3(0f, 0f, -1f),
    var generation: Int = 0,
    var initialLife: Float = 1f,
    var split: Boolean = false,
)

private data class BubbleContact(val fraction: Float, val candleIndex: Int? = null)

private data class CandleTarget(
    val candleRoot: Entity,
    val candleAnchor: Entity,
    val flame: FlameVisual,
    val response: FlameResponse,
    var position: Vector3 = Vector3.ZERO,
    var halfExtent: Vector3 = Vector3.ZERO,
    var visible: Boolean = false,
)

private fun Vector3.point(): Point3 = Point3(x, y, z)

private fun Vector3.toJson(): JSONArray =
    JSONArray()
        .put(x.toDouble())
        .put(y.toDouble())
        .put(z.toDouble())

private fun yaw(vector: Vector3, degrees: Float): Vector3 {
    val angle = degrees * PI.toFloat() / 180f
    return Vector3(
        vector.x * cos(angle) + vector.z * sin(angle),
        vector.y,
        -vector.x * sin(angle) + vector.z * cos(angle),
    )
}

private fun normalized(vector: Vector3): Vector3 {
    val length = vectorLength(vector)
    return Vector3(vector.x / length, vector.y / length, vector.z / length)
}

private fun vectorLength(vector: Vector3): Float =
    sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z)

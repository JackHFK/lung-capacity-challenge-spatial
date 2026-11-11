package com.example.lungcapacity.pcda76db4.ui.game.scene

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lungcapacity.pcda76db4.BREATH_STAGE_ID
import com.example.lungcapacity.pcda76db4.data.repository.FakeGameRepository
import com.example.lungcapacity.pcda76db4.domain.model.ChallengeConfig
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import com.example.lungcapacity.pcda76db4.domain.model.GrowthCatalog
import com.example.lungcapacity.pcda76db4.domain.model.GrowthNodeId
import com.example.lungcapacity.pcda76db4.domain.usecase.FlameResponse
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.closeStage
import com.pico.spatial.ui.platform.containers.minimizeWindowContainer
import com.pico.spatial.ui.platform.containers.openStage
import com.pico.spatial.ui.platform.containers.restoreWindowContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/** Controlled input and private-field inspection are confined to the separate test APK. */
@RunWith(AndroidJUnit4::class)
class GrowthInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun purchasedGrowthChangesRealParticleGenerationsChargeAndNeighborFlames() = inArena { scene, root ->
        val pose = HeadEmissionPose(Vector3(0f, 2.8f, 0f), Quat.identity(), 0.8f)
        onFrame {
            scene.restartAssembly()
            scene.submit(ChallengePhase.RUNNING)
        }
        drive(scene, pose, 0.12f)
        var baseEnergy = 0f
        onFrame { baseEnergy = slots(scene).filter { entity(it).enabled }.maxOf { get(it, "energy") as Float } }
        onFrame {
            scene.restartAssembly()
            scene.submit(ChallengePhase.RUNNING)
        }
        drive(scene, pose, 0.70f)
        var chargedEnergy = 0f
        var chargedTime = 0f
        onFrame {
            chargedEnergy = slots(scene)
                .filter { entity(it).enabled }
                .maxOf { get(it, "energy") as Float }
            chargedTime = get(scene, "chargeTime") as Float
        }
        val config = get(scene, "config") as ChallengeConfig
        val chargeRank = config.growthRanks[GrowthNodeId.CHARGE] ?: 0
        assertTrue(
            "charge did not accumulate: time=$chargedTime rank=$chargeRank phase=${get(scene, "phase")}",
            chargedTime >= 0.6f && chargeRank == GrowthCatalog.spec(GrowthNodeId.CHARGE).maxRank,
        )
        assertEquals(
            "charged bubble missing: time=$chargedTime rank=$chargeRank",
            1.12f,
            chargedEnergy / baseEnergy,
            0.0001f,
        )
        drive(scene, pose.copy(intensity = 0f), 0.03f)
        assertEquals(0f, get(scene, "chargeTime") as Float, 0f)

        var familyEnergy = 0f
        onFrame {
            clear(scene)
            set(scene, "elapsed", 1f)
            val parent = particle(scene, Vector3(-0.4f, 1.55f, -0.4f), Vector3(0f, 0f, -1f))
            set(parent, "energy", 0.1f)
            set(parent, "life", 0.39f)
            invoke(scene, "updateBubbles", 0.01f, pose)
            val children = slots(scene).filter { entity(it).enabled && get(it, "generation") == 1 }
            assertEquals(2, children.size)
            assertEquals(0.0175f, get(children[0], "energy") as Float, 0.00001f)
            val lateral = children.map { (get(it, "velocity") as Vector3).x }.sorted()
            assertEquals(-0.1671557f, lateral[0], 0.0001f)
            assertEquals(0.1671557f, lateral[1], 0.0001f)
            children.forEach { set(it, "life", 0.1f) }
            invoke(scene, "updateBubbles", 0.01f, pose)
            assertEquals(6, slots(scene).count { entity(it).enabled && get(it, "generation") == 2 })
            familyEnergy = slots(scene).filter { entity(it).enabled }.sumOf { (get(it, "energy") as Float).toDouble() }.toFloat()
            assertEquals(0.1455f, familyEnergy, 0.00001f)
            invoke(scene, "updateBubbles", 0.01f, pose)
            assertEquals("split must happen once per particle", 9, slots(scene).count { entity(it).enabled })
        }
        capture("p1-three-generations.png")

        onFrame {
            clear(scene)
            val targets = targets(scene)
            targets.forEach {
                (get(it, "response") as FlameResponse).reset()
                (get(it, "flame") as FlameVisual).apply {
                    setEnabled(true)
                    ignite()
                }
                set(it, "visible", true)
            }
            val middle = targets.size / 2
            val left = get(targets[middle - 1], "response") as FlameResponse
            val right = get(targets[middle + 1], "response") as FlameResponse
            val far = get(targets[middle - 2], "response") as FlameResponse
            left.hit(left.threshold - 0.001f)
            invoke(scene, "hitCandle", middle, left.threshold, true, 1f, 0f)
            assertTrue(left.extinguished)
            assertEquals(right.threshold * 0.03f, right.exposure, 0.00001f)
            assertEquals("a wave extinction must not recursively spread", 0f, far.exposure, 0f)
            assertEquals(1f, right.pulse, 0f)
            invoke(scene, "hitCandle", middle, left.threshold, true, 1f, 0f)
            assertEquals("repeat hit must not repeat the wave", right.threshold * 0.03f, right.exposure, 0.00001f)

            val particle = particle(scene, Vector3(-0.4f, 1.55f, -0.4f), Vector3(0f, 0f, -100f))
            set(particle, "life", 0.001f)
            invoke(scene, "updateBubbles", 0.05f, pose)
            assertEquals("motion must end at expiration", -0.5f, transform(particle).position.z, 0.0001f)
        }
        File(context.getExternalFilesDir(null), "p1-growth-evidence.json").writeText(
            """{"charge_ratio":${chargedEnergy / baseEnergy},"generation_counts":[1,2,6],"family_energy_ratio":${familyEnergy / 0.1f},"child_lateral_speed":0.1671557,"wave_neighbor_fraction":0.03,"recursive_wave":false,"expired_particle_motion_m":0.1,"input_source":"androidTest only"}""",
        )
        onFrame { assertNotNull(root.scene) }
    }

    private fun inArena(
        requireVisibleCandle: Boolean = false,
        action: (BreathArenaScene, Entity) -> Unit,
    ) {
        val repository = FakeGameRepository.shared
        val original = repository.state.value
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.startActivitySync(
            Intent().setClassName(context.packageName, "${context.packageName}.platform.LaunchActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        runBlocking(Dispatchers.Main) {
            repository.update {
                GameSnapshot(
                    phase = ChallengePhase.COUNTDOWN,
                    growthRanks = GrowthCatalog.specs.associate { it.id to it.maxRank },
                )
            }
            context.openStage(BREATH_STAGE_ID, StageStyle.Mixed)
            context.minimizeWindowContainer("Home")
        }
        var scene: BreathArenaScene? = null
        await {
            scene = participants().filterIsInstance<BreathArenaScene>().firstOrNull {
                val config = get(it, "config") as ChallengeConfig
                val preview = get(it, "preview") as Boolean
                return@firstOrNull !preview &&
                    get(it, "root") != null &&
                    get(it, "headPose") != null &&
                    config.growthRanks[GrowthNodeId.CHARGE] ==
                    GrowthCatalog.spec(GrowthNodeId.CHARGE).maxRank
            }
            val firstCandleVisible = !requireVisibleCandle || scene?.let { arena ->
                (get(arena, "candleTargets") as List<*>)
                    .filterNotNull()
                    .any { get(it, "visible") as Boolean }
            } == true
            FakeGameRepository.shared.state.value.phase == ChallengePhase.RUNNING &&
                scene != null && get(scene!!, "phase") == ChallengePhase.RUNNING &&
                firstCandleVisible
        }
        val arena = scene!!
        val loop = participants().first { it.javaClass.simpleName == "StageAnchorLoop" }
        BreathFrameSystem.remove(arena)
        BreathFrameSystem.remove(loop)
        try {
            action(arena, get(arena, "root") as Entity)
        } finally {
            BreathFrameSystem.add(loop)
            BreathFrameSystem.add(arena)
            runBlocking(Dispatchers.Main) {
                repository.update { original }
                context.restoreWindowContainer("Home")
                closeStage()
            }
            await {
                arena !in participants() &&
                    participants().none { it.javaClass.simpleName == "StageAnchorLoop" }
            }
        }
    }

    private fun drive(scene: BreathArenaScene, pose: HeadEmissionPose, seconds: Float) {
        val latch = CountDownLatch(1)
        var clock = 0f
        val root = get(scene, "root") as Entity
        val participant = object : FrameParticipant {
            override fun update(context: SceneUpdateContext) {
                if (root.scene != context.scene) return
                scene.submitBreathInput(pose)
                scene.update(context)
                clock += context.deltaTime.coerceIn(0f, 0.05f)
                if (clock >= seconds) latch.countDown()
            }
        }
        BreathFrameSystem.add(participant)
        try { assertTrue(latch.await(12, TimeUnit.SECONDS)) }
        finally { BreathFrameSystem.remove(participant) }
    }

    private fun particle(scene: BreathArenaScene, position: Vector3, velocity: Vector3): Any {
        val slot = slots(scene).first { !entity(it).enabled }
        set(slot, "age", 0.7f); set(slot, "life", 1f); set(slot, "initialLife", 1f)
        set(slot, "generation", 0); set(slot, "split", false)
        set(slot, "popRemaining", 0f); set(slot, "velocity", velocity)
        set(slot, "diameter", Vector3(0.04f, 0.04f, 0.04f))
        transform(slot).setPosition(position)
        entity(slot).enabled = true
        return slot
    }

    private fun clear(scene: BreathArenaScene) = slots(scene).forEach { entity(it).enabled = false }
    private fun slots(scene: BreathArenaScene): List<Any> = (get(scene, "bubbles") as List<*>).filterNotNull()
    private fun targets(scene: BreathArenaScene): List<Any> = (get(scene, "candleTargets") as List<*>).filterNotNull()
    private fun entity(value: Any): Entity = get(value, "entity") as Entity
    private fun transform(value: Any): TransformComponent = entity(value).components.get(TransformComponent::class.java)!!
    private fun get(value: Any, name: String): Any? =
        value.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(value) }
    private fun set(value: Any, name: String, content: Any?) =
        value.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.set(value, content) }
    private fun invoke(value: Any, name: String, vararg args: Any?): Any? =
        value.javaClass.declaredMethods.first { it.name == name && it.parameterCount == args.size }
            .let { it.isAccessible = true; it.invoke(value, *args) }
    private fun participants(): Set<FrameParticipant> {
        @Suppress("UNCHECKED_CAST")
        return BreathFrameSystem::class.java.getDeclaredField("participants")
            .let { it.isAccessible = true; it.get(null) as Set<FrameParticipant> }
    }

    private fun onFrame(action: () -> Unit) {
        val latch = CountDownLatch(1)
        val once = AtomicBoolean(false)
        var error: Throwable? = null
        val participant = object : FrameParticipant {
            override fun update(context: SceneUpdateContext) {
                if (!once.compareAndSet(false, true)) return
                try { action() } catch (failure: Throwable) { error = failure }
                finally { latch.countDown() }
            }
        }
        BreathFrameSystem.add(participant)
        try {
            assertTrue("SDK frame timed out", latch.await(5, TimeUnit.SECONDS))
            error?.let { throw it }
        } finally { BreathFrameSystem.remove(participant) }
    }

    private fun capture(name: String) {
        val destination = File(context.getExternalFilesDir(null), name)
        ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(
            "screencap --spatial --size 1920x1080 -p ${destination.absolutePath}",
        )).use { it.readBytes() }
        val bitmap = BitmapFactory.decodeFile(destination.absolutePath)
        assertNotNull("spatial screenshot did not decode", bitmap)
        val decoded = requireNotNull(bitmap)
        try {
            assertEquals(1920, decoded.width)
            assertEquals(1080, decoded.height)
        } finally {
            decoded.recycle()
        }
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(18)
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue("native scene was not ready", condition())
    }
}

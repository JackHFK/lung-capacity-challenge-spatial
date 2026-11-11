package com.example.lungcapacity.pcda76db4.ui.game.scene

import android.Manifest
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lungcapacity.pcda76db4.BREATH_STAGE_ID
import com.example.lungcapacity.pcda76db4.data.repository.FakeGameRepository
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import com.example.lungcapacity.pcda76db4.domain.usecase.FlameResponse
import com.example.lungcapacity.pcda76db4.platform.audio.MicrophoneBreathInput
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.EulerAngles
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.closeStage
import com.pico.spatial.ui.platform.containers.openStage
import com.pico.spatial.ui.platform.containers.minimizeWindowContainer
import com.pico.spatial.ui.platform.containers.restoreWindowContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.*

/** All controlled sensor input and reflection live only in the separate androidTest APK. */
@RunWith(AndroidJUnit4::class)
class StageFeedbackInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private lateinit var originalSnapshot: GameSnapshot

    @Before
    fun resetGameState() {
        originalSnapshot = FakeGameRepository.shared.state.value
        runBlocking(Dispatchers.Main) {
            FakeGameRepository.shared.update {
                GameSnapshot(
                    difficulty = Difficulty.NORMAL,
                    phase = ChallengePhase.COUNTDOWN,
                )
            }
        }
    }

    @After
    fun restoreGameState() {
        runBlocking(Dispatchers.Main) {
            FakeGameRepository.shared.update { originalSnapshot }
            context.restoreWindowContainer("Home")
            closeStage()
        }
    }

    @Test
    fun realStageMapsPoseRendersStrengthAndRegistersVisibleHits() {
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        context.startActivity(Intent().setClassName(context.packageName, "${context.packageName}.platform.LaunchActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        Thread.sleep(1200)
        runBlocking(Dispatchers.Main) {
            context.openStage(BREATH_STAGE_ID, StageStyle.Mixed)
            context.minimizeWindowContainer("Home")
        }
        await("Stage is running") { FakeGameRepository.shared.state.value.phase == ChallengePhase.RUNNING }
        val scene = awaitScene()
        val loop = participants().first { it?.javaClass?.simpleName == "StageAnchorLoop" }!!
        val tickField = loop.javaClass.getDeclaredField("tick").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val originalTick = tickField.get(loop) as (Float) -> Unit
        val microphone = originalTick.javaClass.declaredFields.mapNotNull { field ->
            field.isAccessible = true
            field.get(originalTick) as? MicrophoneBreathInput
        }.first()
        val levelField = MicrophoneBreathInput::class.java.getDeclaredField("measuredIntensity").apply { isAccessible = true }
        @Suppress("UNCHECKED_CAST")
        val meter = levelField.get(microphone) as MutableStateFlow<Float>
        val root = field(scene, "root") as Entity
        val cakeRoot = field(field(scene, "cake")!!, "root") as Entity
        onFrame {
            val transform = cakeRoot.components.get(TransformComponent::class.java)!!
            val initialPosition = transform.position
            val initialScale = transform.scaleVector
            scene.rotateCake(125f)
            val yaw = transform.quaternion
            assertEquals(initialPosition.x, transform.position.x, 0f)
            assertEquals(initialPosition.y, transform.position.y, 0f)
            assertEquals(initialPosition.z, transform.position.z, 0f)
            assertEquals(initialScale.x, transform.scaleVector.x, 0f)
            assertEquals(initialScale.y, transform.scaleVector.y, 0f)
            assertEquals(initialScale.z, transform.scaleVector.z, 0f)
            assertEquals(0f, yaw.x, 0.0001f)
            assertTrue(kotlin.math.abs(yaw.y) > 0.01f)
            assertEquals(0f, yaw.z, 0.0001f)
            scene.rotateCake(-125f)
            assertEquals(0f, transform.quaternion.x, 0.0001f)
            assertEquals(0f, transform.quaternion.y, 0.0001f)
            assertEquals(0f, transform.quaternion.z, 0.0001f)
        }
        var maximumError = 0f
        onFrame {
            val owner = Entity()
            root.addChild(owner)
            owner.components.get(TransformComponent::class.java)!!.apply {
                setPosition(Vector3(0.4f, 0.2f, -0.3f))
                setEulerAngles(EulerAngles(12f, 27f, -8f))
                setScaleVector(Vector3(0.6f, 0.6f, 0.6f))
            }
            try {
                for (yaw in listOf(-60f, 0f, 60f)) for (pitch in listOf(-25f, 15f)) {
                    val pose = HeadEmissionPose(Vector3(0.3f, 1.62f, 0.2f), rotation(yaw, pitch), 0.4f)
                    val ray = pose.trajectory(owner, Vector3(0f, 0f, -1f), 2f)
                    val worldOrigin = owner.convertPositionTo(ray.origin, null)
                    val worldVelocity = owner.convertPositionTo(ray.origin + ray.velocity, null) - worldOrigin
                    val expectedVelocity = pose.rotation.rotateVector(Vector3(0f, 0f, -2f))
                    maximumError = maxOf(maximumError, length(worldOrigin - pose.mouthWorld()), length(worldVelocity - expectedVelocity))
                    assertTrue("mapped mouth drift", length(worldOrigin - pose.mouthWorld()) < 0.0001f)
                    assertTrue("scaled or rotated velocity drift", length(worldVelocity - expectedVelocity) < 0.0001f)
                    assertEquals(length(HeadEmissionPose.MOUTH_OFFSET), length(worldOrigin - pose.position), 0.0001f)
                }
            } finally {
                owner.destroy(true)
            }
        }
        val actualPose = field(scene, "headPose") as HeadEmissionPose
        val targets = field(scene, "candleTargets") as List<*>
        targets.forEach {
            assertEquals(Difficulty.NORMAL.flameThreshold, (field(it!!, "response") as FlameResponse).threshold, 0f)
        }
        val target = targets[targets.size / 2]!!
        await("target candle follows the tier-local insertion timing") {
            field(target, "visible") as Boolean
        }
        await("target candle finishes the bounded insertion motion") {
            var inserted = false
            onFrame {
                val anchor = field(target, "candleAnchor") as Entity
                val position = anchor.components.get(TransformComponent::class.java)!!.position
                inserted = kotlin.math.abs(position.y) < 0.0001f
            }
            inserted
        }
        val flameVisual = field(target, "flame") as FlameVisual
        val flame = flameVisual.root
        lateinit var targetWorld: Vector3
        var initialHeight = 0f
        onFrame {
            val bounds = flame.getVisualBounds(root, true, true)
            assertTrue("rendered flame has no bounds", bounds.halfExtent.y > 0.01f)
            initialHeight = bounds.halfExtent.y
            targetWorld = root.convertPositionTo(bounds.center, null)
        }
        val aimed = actualPose.copy(
            rotation = aimRotation(actualPose.position, targetWorld),
            intensity = 0f,
        )
        val before = FakeGameRepository.shared.state.value.remainingCandles
        val level = AtomicReference(0f)
        try {
            runBlocking { microphone.stop() }
            onFrame {
                tickField.set(loop, { dt: Float ->
                    originalTick(dt)
                    meter.value = level.get()
                    scene.submitBreathInput(aimed.copy(intensity = level.get()))
                })
            }
            await("silent input leaves no active particles") {
                var empty = false
                onFrame {
                    empty = (field(scene, "bubbles") as List<*>).none { (field(it!!, "entity") as Entity).enabled }
                }
                empty
            }
            assertEquals("silent input cannot extinguish candles", before, FakeGameRepository.shared.state.value.remainingCandles)
            capture("stage-strength-zero.png")
            level.set(0.18f)
            Thread.sleep(400)
            capture("stage-strength-low.png")
            level.set(0.78f)
            Thread.sleep(180)
            capture("stage-strength-high.png")
            val response = field(target, "response") as com.example.lungcapacity.pcda76db4.domain.usecase.FlameResponse
            await("actual flame receives sustained bubble hits") { response.exposure >= 0.25f }
            capture("stage-flame-hit.png")
            var hitPulse = 0f
            onFrame {
                hitPulse = (field(flameVisual, "state") as FlameState).sample().hitPulse
            }
            assertTrue("dynamic flame did not receive the bubble hit", hitPulse > 0f)
            await("actual candle extinguishes") { FakeGameRepository.shared.state.value.remainingCandles < before }
            level.set(0f)
            Thread.sleep(450)
            capture("stage-flame-extinguished.png")
            val remainingAfter = FakeGameRepository.shared.state.value.remainingCandles
            assertTrue(
                "aimed stream changed candles from $before to $remainingAfter",
                remainingAfter in (before - 3)..(before - 1),
            )
            File(context.getExternalFilesDir(null), "stage-feedback-evidence.json").writeText(
                """{"coordinate_cases":6,"max_world_error_m":$maximumError,"strength_levels":[0,0.18,0.78],"flame_threshold":${response.threshold},"initial_flame_half_height":$initialHeight,"dynamic_hit_pulse":$hitPulse,"remaining_before":$before,"remaining_after":$remainingAfter,"controlled_input_source":"androidTest only"}""",
            )
        } finally {
            level.set(0f)
            meter.value = 0f
            onFrame { tickField.set(loop, originalTick) }
            microphone.start()
        }
    }

    private fun capture(name: String) {
        val directory = "/sdcard/Download/lung-capacity-validation"
        shell("mkdir -p $directory")
        val destination = "$directory/$name"
        shell("screencap --spatial --size 1920x1080 -p $destination")
        val size = shell("stat -c %s $destination").trim().toLong()
        assertTrue("spatial capture missing", size > 100_000)
    }

    private fun awaitScene(): BreathArenaScene {
        var result: BreathArenaScene? = null
        await("native Stage scene exists") {
            result = participants().filterIsInstance<BreathArenaScene>().firstOrNull { !(field(it, "preview") as Boolean) }
            result != null && field(result!!, "headPose") != null
        }
        return result!!
    }

    private fun participants(): Set<*> =
        BreathFrameSystem::class.java.getDeclaredField("participants").let { it.isAccessible = true; it.get(null) as Set<*> }

    private fun field(instance: Any, name: String): Any? =
        instance.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(instance) }

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

    private fun await(reason: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(18)
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue(reason, predicate())
    }

    private fun rotation(yawDegrees: Float, pitchDegrees: Float): Quat {
        val y = yawDegrees * PI.toFloat() / 360f
        val p = pitchDegrees * PI.toFloat() / 360f
        return Quat(cos(y) * sin(p), sin(y) * cos(p), -sin(y) * sin(p), cos(y) * cos(p))
    }

    private fun aimRotation(headPosition: Vector3, targetWorld: Vector3): Quat {
        var result = Quat.identity()
        repeat(4) {
            val mouth = headPosition + result.rotateVector(HeadEmissionPose.MOUTH_OFFSET)
            val aim = targetWorld - mouth
            val yaw = atan2(-aim.x, -aim.z) * 180f / PI.toFloat()
            val pitch = asin((aim.y / length(aim)).coerceIn(-1f, 1f)) * 180f / PI.toFloat()
            result = rotation(yaw, pitch)
        }
        return result
    }

    private fun length(vector: Vector3): Float = sqrt(vector.x * vector.x + vector.y * vector.y + vector.z * vector.z)

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
}

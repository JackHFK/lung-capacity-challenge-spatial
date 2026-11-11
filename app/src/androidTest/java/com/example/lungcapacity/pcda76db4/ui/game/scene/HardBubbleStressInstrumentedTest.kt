package com.example.lungcapacity.pcda76db4.ui.game.scene

import android.Manifest
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lungcapacity.pcda76db4.BREATH_STAGE_ID
import com.example.lungcapacity.pcda76db4.data.repository.FakeGameRepository
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import com.example.lungcapacity.pcda76db4.domain.model.GrowthCatalog
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.closeStage
import com.pico.spatial.ui.platform.containers.minimizeWindowContainer
import com.pico.spatial.ui.platform.containers.openStage
import com.pico.spatial.ui.platform.containers.restoreWindowContainer
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class HardBubbleStressInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun hardCakeSustainsSaturatedBubblePool() {
        val repository = FakeGameRepository.shared
        val original = repository.state.value
        var arena: BreathArenaScene? = null
        var anchorLoop: FrameParticipant? = null
        var driver: FrameParticipant? = null

        try {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.RECORD_AUDIO,
            )
            instrumentation.startActivitySync(
                Intent().setClassName(
                    context.packageName,
                    "${context.packageName}.platform.LaunchActivity",
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            runBlocking(Dispatchers.Main) {
                repository.update {
                    GameSnapshot(
                        difficulty = Difficulty.HARD,
                        phase = ChallengePhase.COUNTDOWN,
                        growthRanks = GrowthCatalog.specs.associate { it.id to it.maxRank },
                    )
                }
                context.openStage(BREATH_STAGE_ID, StageStyle.Mixed)
                context.minimizeWindowContainer("Home")
            }
            await {
                arena = participants().filterIsInstance<BreathArenaScene>().firstOrNull {
                    !field<Boolean>(it, "preview") &&
                        field<Any?>(it, "root") != null &&
                        field<Any?>(it, "cake") != null
                }
                anchorLoop = participants().firstOrNull {
                    it.javaClass.simpleName == "StageAnchorLoop"
                }
                val cake = arena?.let { field<Any?>(it, "cake") }
                repository.state.value.phase == ChallengePhase.RUNNING &&
                    arena != null &&
                    anchorLoop != null &&
                    cake != null &&
                    field<Boolean>(cake, "assemblyComplete")
            }

            val scene = requireNotNull(arena)
            val loop = requireNotNull(anchorLoop)
            val sceneRoot = field<Entity>(scene, "root")
            BreathFrameSystem.remove(scene)
            BreathFrameSystem.remove(loop)
            scene.submit(ChallengePhase.RUNNING)

            val baseline = mutableListOf<Float>()
            val stress = mutableListOf<Float>()
            val completed = CountDownLatch(1)
            val failure = AtomicReference<Throwable?>(null)
            val stressPose = HeadEmissionPose(
                position = Vector3(0.85f, 1.6f, 0f),
                rotation = Quat.identity(),
                intensity = 1f,
            )
            var elapsed = 0f
            var occupancyClock = 0f
            var maxActiveBubbles = 0
            driver = object : FrameParticipant {
                override fun update(context: SceneUpdateContext) {
                    if (sceneRoot.scene != context.scene) return
                    try {
                        val rawDt = context.deltaTime.coerceAtLeast(0f)
                        val step = rawDt.coerceIn(0f, 0.05f)
                        elapsed += step
                        when {
                            elapsed < WARMUP_SECONDS -> {
                                scene.submitBreathInput(stressPose.copy(intensity = 0f))
                                scene.update(context)
                            }
                            elapsed < WARMUP_SECONDS + BASELINE_SECONDS -> {
                                scene.submitBreathInput(stressPose.copy(intensity = 0f))
                                scene.update(context)
                                baseline += rawDt
                            }
                            elapsed < TOTAL_SECONDS -> {
                                scene.submitBreathInput(stressPose)
                                scene.update(context)
                                stress += rawDt
                                occupancyClock += step
                                if (occupancyClock >= OCCUPANCY_SAMPLE_SECONDS) {
                                    occupancyClock = 0f
                                    maxActiveBubbles = maxOf(
                                        maxActiveBubbles,
                                        activeBubbleCount(scene),
                                    )
                                }
                            }
                            else -> completed.countDown()
                        }
                    } catch (error: Throwable) {
                        failure.compareAndSet(null, error)
                        completed.countDown()
                    }
                }
            }
            BreathFrameSystem.add(driver)
            assertTrue(
                "stress window timed out",
                completed.await(130, TimeUnit.SECONDS),
            )
            failure.get()?.let { throw it }
            BreathFrameSystem.remove(driver)
            driver = null

            val baselineP50 = percentileMillis(baseline, 0.50f)
            val baselineP95 = percentileMillis(baseline, 0.95f)
            val stressP50 = percentileMillis(stress, 0.50f)
            val stressP95 = percentileMillis(stress, 0.95f)
            val longFrameRatio = stress.count { it >= LONG_FRAME_SECONDS }.toFloat() /
                stress.size.coerceAtLeast(1)
            val p95Limit = maxOf(22.2f, baselineP95 * 1.5f)

            assertTrue("baseline frame samples missing", baseline.size >= 300)
            assertTrue("stress frame samples missing", stress.size >= 900)
            assertTrue(
                "bubble pool did not reach stress occupancy: $maxActiveBubbles",
                maxActiveBubbles >= MIN_ACTIVE_BUBBLES,
            )
            assertTrue(
                "stress P95 $stressP95 ms exceeds $p95Limit ms; baseline=$baselineP95 ms",
                stressP95 <= p95Limit,
            )
            assertTrue(
                "long frame ratio $longFrameRatio exceeds $MAX_LONG_FRAME_RATIO",
                longFrameRatio <= MAX_LONG_FRAME_RATIO,
            )

            val payload =
                """{"difficulty":"hard","pool_size":96,"max_active_bubbles":$maxActiveBubbles,"baseline":{"samples":${baseline.size},"p50_ms":$baselineP50,"p95_ms":$baselineP95},"stress":{"samples":${stress.size},"p50_ms":$stressP50,"p95_ms":$stressP95,"long_frame_ratio":$longFrameRatio},"acceptance":{"min_active_bubbles":$MIN_ACTIVE_BUBBLES,"p95_limit_ms":$p95Limit,"max_long_frame_ratio":$MAX_LONG_FRAME_RATIO}}"""
            Log.i("HardBubbleStress", payload)
            println("HARD_BUBBLE_STRESS $payload")
            val evidence = File(context.getExternalFilesDir(null), "hard-bubble-stress.json")
            evidence.writeText(payload)
            captureSpatial("hard-bubble-stress.png")
        } finally {
            driver?.let(BreathFrameSystem::remove)
            anchorLoop?.let(BreathFrameSystem::add)
            arena?.let(BreathFrameSystem::add)
            runBlocking(Dispatchers.Main) {
                repository.update { original }
                context.restoreWindowContainer("Home")
                closeStage()
            }
            await {
                val active = participants()
                active.none { it === arena } && active.none { it === anchorLoop }
            }
        }
    }

    private fun activeBubbleCount(scene: BreathArenaScene): Int =
        field<List<*>>(scene, "bubbles").count { slot ->
            slot != null && field<Entity>(slot, "entity").enabled
        }

    private fun percentileMillis(samples: List<Float>, percentile: Float): Float {
        assertTrue("frame sample list is empty", samples.isNotEmpty())
        val sorted = samples.sorted()
        val index = ((sorted.lastIndex) * percentile).roundToInt()
        return sorted[index] * 1_000f
    }

    private fun captureSpatial(name: String) {
        val destination = File(context.getExternalFilesDir(null), name)
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(
                "screencap --spatial --size 1920x1080 -p ${destination.absolutePath}",
            ),
        ).use { it.readBytes() }
        val bitmap = BitmapFactory.decodeFile(destination.absolutePath)
        assertNotNull("spatial screenshot did not decode", bitmap)
        val decoded = requireNotNull(bitmap)
        try {
            assertTrue("unexpected screenshot width ${decoded.width}", decoded.width == 1920)
            assertTrue("unexpected screenshot height ${decoded.height}", decoded.height == 1080)
        } finally {
            decoded.recycle()
        }
    }

    private fun participants(): Set<FrameParticipant> {
        @Suppress("UNCHECKED_CAST")
        return BreathFrameSystem::class.java.getDeclaredField("participants")
            .let {
                it.isAccessible = true
                it.get(null) as Set<FrameParticipant>
            }
    }

    private inline fun <reified T> field(target: Any, name: String): T =
        target.javaClass.getDeclaredField(name).let {
            it.isAccessible = true
            @Suppress("UNCHECKED_CAST")
            it.get(target) as T
        }

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(20)
        }
        assertTrue("native scene was not ready", condition())
    }

    private companion object {
        const val WARMUP_SECONDS = 5f
        const val BASELINE_SECONDS = 20f
        const val STRESS_SECONDS = 60f
        const val TOTAL_SECONDS = WARMUP_SECONDS + BASELINE_SECONDS + STRESS_SECONDS
        const val OCCUPANCY_SAMPLE_SECONDS = 0.25f
        const val LONG_FRAME_SECONDS = 0.10f
        const val MIN_ACTIVE_BUBBLES = 64
        const val MAX_LONG_FRAME_RATIO = 0.01f
    }
}

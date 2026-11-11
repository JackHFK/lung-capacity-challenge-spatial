package com.example.lungcapacity.pcda76db4.ui.game.scene

import android.Manifest
import android.content.Intent
import android.os.ParcelFileDescriptor
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lungcapacity.pcda76db4.data.repository.FakeGameRepository
import com.example.lungcapacity.pcda76db4.domain.model.ChallengeConfig
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import com.example.lungcapacity.pcda76db4.domain.usecase.CakeAssemblyTimeline
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.TransformComponent
import java.util.concurrent.TimeUnit
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CakeVisualInstrumentedTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @Test
    fun allHomeDifficultiesFinishTheirSourceAssemblyAndRender() {
        val repository = FakeGameRepository.shared
        val original = repository.state.value
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.RECORD_AUDIO,
        )
        runBlocking(Dispatchers.Main) {
            repository.update { GameSnapshot(difficulty = Difficulty.NORMAL) }
        }
        val activity = instrumentation.startActivitySync(
            Intent().setClassName(
                context.packageName,
                "${context.packageName}.platform.LaunchActivity",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        try {
            listOf(Difficulty.NORMAL, Difficulty.EASY, Difficulty.HARD).forEach { difficulty ->
                runBlocking(Dispatchers.Main) {
                    repository.update { GameSnapshot(difficulty = difficulty) }
                }
                val scene = awaitPreview(difficulty)
                await("assembly did not finish for ${difficulty.name}") {
                    val cake = field(scene, "cake") ?: return@await false
                    field(cake, "assemblyComplete") as Boolean
                }
                verifyCompletedCake(scene, difficulty)
                capture("home-cake-${difficulty.name.lowercase()}-final.png")
                if (difficulty == Difficulty.HARD) {
                    verifyHardAssemblyKeyframes(scene)
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                repository.update { original }
                activity.finish()
            }
            runBlocking {
                withTimeout(5_000) {
                    while (!activity.isDestroyed) delay(20)
                }
            }
        }
    }

    private fun verifyHardAssemblyKeyframes(scene: BreathArenaScene) {
        val cake = field(scene, "cake") as CakeSceneInstance
        val nodes = field(cake, "assemblyNodes") as List<*>

        freezeAssemblyAt(cake, 0.7001f)
        onFrame {
            assertNodeSettled(nodes, "tier-0")
            assertNodeFalling(nodes, "border-0-top")
            assertNodeFalling(nodes, "tier-1")
            assertNodeHidden(nodes, "tier-2")
        }
        capture("home-cake-hard-keyframe-tier-0-parts.png")

        freezeAssemblyAt(cake, 1.2001f)
        onFrame {
            assertNodeSettled(nodes, "tier-1")
            assertNodeFalling(nodes, "border-1-top")
            assertNodeFalling(nodes, "tier-2")
        }
        capture("home-cake-hard-keyframe-tier-1-parts.png")

        listOf(
            Triple(1.2001f, 0, "home-cake-hard-keyframe-candles-tier-0.png"),
            Triple(1.7001f, 1, "home-cake-hard-keyframe-candles-tier-1.png"),
            Triple(2.2001f, 2, "home-cake-hard-keyframe-candles-tier-2.png"),
        ).forEach { (seconds, visibleTier, screenshot) ->
            freezeAssemblyAt(cake, seconds)
            onFrame {
                cake.candles.forEach { candle ->
                    if (candle.slotIndex == 0 && candle.tierIndex <= visibleTier) {
                        assertTrue(
                            "tier ${candle.tierIndex} first candle is hidden",
                            candle.root.enabled,
                        )
                    }
                    if (candle.tierIndex > visibleTier) {
                        assertTrue(
                            "tier ${candle.tierIndex} started too early",
                            !candle.root.enabled,
                        )
                    }
                }
            }
            capture(screenshot)
        }

        freezeAssemblyAt(cake, CakeAssemblyTimeline.assemblySeconds(Difficulty.HARD))
    }

    private fun freezeAssemblyAt(cake: CakeSceneInstance, elapsedSeconds: Float) {
        onFrame {
            cake.updateAssembly(elapsedSeconds)
            cake.javaClass.getDeclaredField("assemblyComplete").let {
                it.isAccessible = true
                it.setBoolean(cake, true)
            }
        }
    }

    private fun assertNodeSettled(nodes: List<*>, id: String) {
        val node = node(nodes, id)
        assertTrue("$id is hidden", node.entity.enabled)
        assertEquals(node.finalPosition.y, node.transform.position.y, 0.0001f)
    }

    private fun assertNodeFalling(nodes: List<*>, id: String) {
        val node = node(nodes, id)
        assertTrue("$id is hidden", node.entity.enabled)
        assertTrue("$id is not falling", node.transform.position.y > node.finalPosition.y)
    }

    private fun assertNodeHidden(nodes: List<*>, id: String) {
        assertTrue("$id started too early", !node(nodes, id).entity.enabled)
    }

    private fun node(nodes: List<*>, id: String): CakeAssemblyNode =
        nodes.filterIsInstance<CakeAssemblyNode>().first { it.id == id }

    private fun verifyCompletedCake(
        scene: BreathArenaScene,
        difficulty: Difficulty,
    ) {
        val owner = field(scene, "root") as Entity
        val cake = field(scene, "cake")!!
        val cakeRoot = field(cake, "root") as Entity
        val targets = field(scene, "candleTargets") as List<*>
        assertEquals(difficulty.candleCount, targets.size)

        var initialYaw = 0f
        onFrame {
            val cakeTransform = cakeRoot.components.get(TransformComponent::class.java)!!
            assertTrue("Home cake was not shifted right", cakeTransform.position.x > 0.04f)
            initialYaw = cakeTransform.quaternion.y
            targets.forEachIndexed { index, value ->
                val target = value!!
                assertTrue("candle $index is not visible", field(target, "visible") as Boolean)
                val flame = field(target, "flame") as FlameVisual
                val flameSurface = field(flame, "flameOuter") as Entity
                val flameCore = field(flame, "flameCore") as Entity
                assertTrue("flame $index root is disabled", flame.root.enabled)
                assertTrue("flame $index surface is disabled", flameSurface.enabled)
                assertTrue("flame $index core is disabled", flameCore.enabled)
                assertTrue(
                    "flame $index has no rendered height",
                    flameSurface.getVisualBounds(owner, true, true).halfExtent.y > 0.001f,
                )
                val anchor = field(target, "candleAnchor") as Entity
                val scale = anchor.components.get(TransformComponent::class.java)!!.scaleVector.x
                val expected = if (difficulty == Difficulty.EASY) 1.55f else 1f
                assertEquals("candle scale mismatch", expected, scale, 0.0001f)
            }
        }
        Thread.sleep(500)
        onFrame {
            val currentYaw = cakeRoot.components
                .get(TransformComponent::class.java)!!
                .quaternion.y
            assertTrue("Home cake did not auto-rotate", kotlin.math.abs(currentYaw - initialYaw) > 0.001f)
        }
    }

    private fun awaitPreview(difficulty: Difficulty): BreathArenaScene {
        var result: BreathArenaScene? = null
        await("preview scene did not load for ${difficulty.name}") {
            result = participants()
                .filterIsInstance<BreathArenaScene>()
                .firstOrNull { scene ->
                    field(scene, "preview") as Boolean &&
                        (field(scene, "config") as ChallengeConfig).difficulty == difficulty &&
                        field(scene, "cake") != null
                }
            result != null
        }
        return result!!
    }

    private fun capture(name: String) {
        val directory = "/sdcard/Download/lung-capacity-validation"
        shell("mkdir -p $directory")
        val destination = "$directory/$name"
        shell("screencap --spatial --size 1920x1080 -p $destination")
        val size = shell("stat -c %s $destination").trim().toLong()
        assertTrue("spatial capture missing: $name", size > 100_000)
    }

    private fun participants(): Set<*> =
        BreathFrameSystem::class.java.getDeclaredField("participants")
            .let { it.isAccessible = true; it.get(null) as Set<*> }

    private fun field(instance: Any, name: String): Any? =
        instance.javaClass.getDeclaredField(name)
            .let { it.isAccessible = true; it.get(instance) }

    private fun onFrame(action: () -> Unit) {
        val latch = CountDownLatch(1)
        val once = AtomicBoolean(false)
        var error: Throwable? = null
        val participant = object : FrameParticipant {
            override fun update(context: SceneUpdateContext) {
                if (!once.compareAndSet(false, true)) return
                try {
                    action()
                } catch (failure: Throwable) {
                    error = failure
                } finally {
                    latch.countDown()
                }
            }
        }
        BreathFrameSystem.add(participant)
        try {
            assertTrue("SDK frame timed out", latch.await(5, TimeUnit.SECONDS))
            error?.let { throw it }
        } finally {
            BreathFrameSystem.remove(participant)
        }
    }

    private fun await(reason: String, predicate: () -> Boolean) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(45)
        while (!predicate() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue(reason, predicate())
    }

    private fun shell(command: String): String =
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command),
        ).bufferedReader().use { it.readText() }
}

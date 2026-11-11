package com.example.lungcapacity.pcda76db4.ui.stage

import android.Manifest
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lungcapacity.pcda76db4.BREATH_STAGE_ID
import com.example.lungcapacity.pcda76db4.data.repository.FakeGameRepository
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.domain.model.Difficulty
import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import com.example.lungcapacity.pcda76db4.platform.audio.MicrophoneBreathInput
import com.example.lungcapacity.pcda76db4.ui.game.scene.BreathArenaScene
import com.example.lungcapacity.pcda76db4.ui.game.scene.BreathFrameSystem
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.openStage
import com.pico.spatial.ui.platform.containers.minimizeWindowContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StageLifecycleInstrumentedTest {
    @Test
    fun replacingArenaKeepsHeadLoopAndMicrophoneSessionRunning() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.startActivitySync(
            Intent().setClassName(context.packageName, "${context.packageName}.platform.LaunchActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        runBlocking(Dispatchers.Main) {
            FakeGameRepository.shared.update { GameSnapshot(phase = ChallengePhase.COUNTDOWN) }
            context.openStage(BREATH_STAGE_ID, StageStyle.Mixed)
            context.minimizeWindowContainer("Home")
        }
        await { FakeGameRepository.shared.state.value.phase == ChallengePhase.RUNNING && stage()?.let { field(it, "headPose") } != null }
        val original = stage()!!
        val loop = participants().first { it?.javaClass?.simpleName == "StageAnchorLoop" }!!
        val tick = field(loop, "tick")!!
        val microphone = tick.javaClass.declaredFields.mapNotNull {
            it.isAccessible = true
            it.get(tick) as? MicrophoneBreathInput
        }.first()
        val job = field(microphone, "captureJob") as Job
        val elapsed = FakeGameRepository.shared.state.value.elapsedSeconds
        runBlocking(Dispatchers.Main) {
            FakeGameRepository.shared.update { it.copy(difficulty = Difficulty.EASY, remainingCandles = 8) }
        }
        await { stage()?.let { it !== original && field(it, "headPose") != null } == true }
        await { FakeGameRepository.shared.state.value.elapsedSeconds > elapsed + 0.3f }
        assertSame(loop, participants().first { it?.javaClass?.simpleName == "StageAnchorLoop" })
        assertSame(job, field(microphone, "captureJob"))
        assertTrue(job.isActive)
        assertNull(field(original, "root"))
        assertEquals(8, FakeGameRepository.shared.state.value.remainingCandles)
        runBlocking(Dispatchers.Main) {
            FakeGameRepository.shared.update { it.copy(difficulty = Difficulty.NORMAL, remainingCandles = 16) }
            loop.javaClass.getDeclaredField("elapsed").apply { isAccessible = true }.setFloat(loop, 0.05f)
            loop.javaClass.getDeclaredField("publishClock").apply { isAccessible = true }.setFloat(loop, 0.06f)
            FakeGameRepository.shared.update { it.copy(phase = ChallengePhase.RESULT) }
        }
        await { field(loop, "elapsed") == 0f && field(loop, "publishClock") == 0f }
        assertEquals(0f, field(loop, "elapsed") as Float, 0f)
        assertEquals(0f, field(loop, "publishClock") as Float, 0f)
    }

    @Test
    fun resultPhaseDisablesCakeRotationTargetUntilCountdown() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.startActivitySync(
            Intent().setClassName(context.packageName, "${context.packageName}.platform.LaunchActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        runBlocking(Dispatchers.Main) {
            FakeGameRepository.shared.update {
                GameSnapshot(
                    phase = ChallengePhase.COUNTDOWN,
                    difficulty = Difficulty.EASY,
                )
            }
            context.openStage(BREATH_STAGE_ID, StageStyle.Mixed)
            context.minimizeWindowContainer("Home")
        }
        await { stage()?.let { field(it, "cake") } != null }
        val scene = stage()!!

        runBlocking(Dispatchers.Main) {
            scene.submit(ChallengePhase.RESULT)
            assertFalse(cakeHitTarget(scene).enabled)

            scene.submit(ChallengePhase.COUNTDOWN)
            assertTrue(cakeHitTarget(scene).enabled)
        }
    }

    private fun field(instance: Any, name: String): Any? =
        instance.javaClass.getDeclaredField(name).let { it.isAccessible = true; it.get(instance) }

    private fun participants(): Set<*> =
        BreathFrameSystem::class.java.getDeclaredField("participants").let { it.isAccessible = true; it.get(null) as Set<*> }

    private fun stage(): BreathArenaScene? =
        participants().filterIsInstance<BreathArenaScene>().firstOrNull { !(field(it, "preview") as Boolean) }

    private fun cakeHitTarget(stage: BreathArenaScene): com.pico.spatial.core.ecs.Entity =
        field(field(stage, "cake")!!, "hitTarget") as com.pico.spatial.core.ecs.Entity

    private fun await(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 12_000_000_000
        while (!condition() && System.nanoTime() < deadline) Thread.sleep(20)
        assertTrue("native lifecycle condition was not reached", condition())
    }
}

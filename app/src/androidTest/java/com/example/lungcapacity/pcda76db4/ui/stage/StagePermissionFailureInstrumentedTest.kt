package com.example.lungcapacity.pcda76db4.ui.stage

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lungcapacity.pcda76db4.BREATH_STAGE_ID
import com.example.lungcapacity.pcda76db4.data.repository.FakeGameRepository
import com.example.lungcapacity.pcda76db4.domain.model.ChallengePhase
import com.example.lungcapacity.pcda76db4.domain.model.GameSnapshot
import com.example.lungcapacity.pcda76db4.ui.game.scene.BreathArenaScene
import com.example.lungcapacity.pcda76db4.ui.game.scene.BreathFrameSystem
import com.pico.spatial.ui.platform.containers.StageStyle
import com.pico.spatial.ui.platform.containers.openStage
import com.pico.spatial.ui.platform.containers.minimizeWindowContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Run with RECORD_AUDIO revoked and user-fixed on the dedicated emulator. */
@RunWith(AndroidJUnit4::class)
class StagePermissionFailureInstrumentedTest {
    @Test
    fun deniedMicrophoneReturnsToHomeAndDisposesStageResources() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        assumeTrue(
            "Run this test separately with RECORD_AUDIO revoked",
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_DENIED,
        )
        instrumentation.startActivitySync(
            Intent().setClassName(context.packageName, "${context.packageName}.platform.LaunchActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        runBlocking(Dispatchers.Main) {
            FakeGameRepository.shared.update { GameSnapshot(phase = ChallengePhase.COUNTDOWN) }
            context.openStage(BREATH_STAGE_ID, StageStyle.Mixed)
            context.minimizeWindowContainer("Home")
        }
        val deadline = System.nanoTime() + 12_000_000_000
        do {
            Thread.sleep(20)
            val state = FakeGameRepository.shared.state.value
            val participants = BreathFrameSystem::class.java.getDeclaredField("participants")
                .let { it.isAccessible = true; it.get(null) as Set<*> }
            val stageActive = participants.any {
                it?.javaClass?.simpleName == "StageAnchorLoop" ||
                    (it is BreathArenaScene && !it.javaClass.getDeclaredField("preview").let { f -> f.isAccessible = true; f.get(it) as Boolean })
            }
            if (state.phase == ChallengePhase.HOME && !stageActive) break
        } while (System.nanoTime() < deadline)
        val state = FakeGameRepository.shared.state.value
        assertEquals(ChallengePhase.HOME, state.phase)
        assertEquals(0, state.coins)
        assertEquals(0, state.reward)
        assertEquals("无法启用麦克风，请检查录音权限", state.feedback)
        Thread.sleep(3500)
        assertEquals(ChallengePhase.HOME, FakeGameRepository.shared.state.value.phase)
        val file = File(context.getExternalFilesDir(null), "stage-permission-failure.png")
        val descriptor = instrumentation.uiAutomation.executeShellCommand("screencap --spatial --size 1920x1080 -p ${file.absolutePath}")
        ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { it.readBytes() }
        assertTrue(file.length() > 100_000)
    }
}

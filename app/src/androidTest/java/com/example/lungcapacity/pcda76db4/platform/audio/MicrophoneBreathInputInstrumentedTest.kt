package com.example.lungcapacity.pcda76db4.platform.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.SystemClock
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.lungcapacity.pcda76db4.domain.usecase.BreathSignalProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sqrt

@RunWith(AndroidJUnit4::class)
class MicrophoneBreathInputInstrumentedTest {
    @SuppressLint("MissingPermission")
    @Test
    fun measureActualPcmStartupWithoutProcessorCalibrationDelay() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.grantRuntimePermission(
            instrumentation.targetContext.packageName,
            Manifest.permission.RECORD_AUDIO,
        )
        // Foreground microphone permission otherwise permits reads containing only zeros.
        instrumentation.startActivitySync(
            Intent().setClassName(
                instrumentation.targetContext.packageName,
                "${instrumentation.targetContext.packageName}.platform.LaunchActivity",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        instrumentation.waitForIdleSync()
        val minimum = AudioRecord.getMinBufferSize(
            16_000, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
        )
        assertTrue(minimum > 0)
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            16_000,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minimum, 320 * Short.SIZE_BYTES * 2),
        )
        val processor = BreathSignalProcessor()
        val samples = ShortArray(320)
        var firstReadMs: Long? = null
        var firstNonzeroPcmMs: Long? = null
        var firstStrengthMs: Long? = null
        var maximumRms = 0f
        var maximumStrength = 0f
        var totalSamples = 0
        try {
            assertEquals(AudioRecord.STATE_INITIALIZED, recorder.state)
            val started = SystemClock.elapsedRealtime()
            recorder.startRecording()
            repeat(100) {
                val count = recorder.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                val elapsed = SystemClock.elapsedRealtime() - started
                assertTrue("PCM read failed: $count", count > 0)
                if (firstReadMs == null) firstReadMs = elapsed
                totalSamples += count
                var sum = 0.0
                for (index in 0 until count) {
                    val normalized = samples[index] / Short.MAX_VALUE.toDouble()
                    sum += normalized * normalized
                }
                val rms = sqrt(sum / count).toFloat()
                if (rms > 0f && firstNonzeroPcmMs == null) firstNonzeroPcmMs = elapsed
                val strength = processor.process(rms)
                if (strength > 0f && firstStrengthMs == null) firstStrengthMs = elapsed
                maximumRms = maxOf(maximumRms, rms)
                maximumStrength = maxOf(maximumStrength, strength)
                Log.i("BreathPcmProbe", "block=$it elapsedMs=$elapsed rms=$rms strength=$strength")
            }
            Log.i(
                "BreathPcmProbe",
                "firstReadMs=$firstReadMs firstNonzeroPcmMs=$firstNonzeroPcmMs " +
                    "firstStrengthMs=$firstStrengthMs maximumRms=$maximumRms " +
                    "maximumStrength=$maximumStrength totalSamples=$totalSamples",
            )
            assertTrue(totalSamples > 0)
            assertEquals(1f, BreathSignalProcessor().process(0.10f), 0f)
        } finally {
            if (recorder.recordingState == AudioRecord.RECORDSTATE_RECORDING) recorder.stop()
            recorder.release()
        }
    }

    @Test
    fun grantedMicrophoneCanStartAndReleaseAudioRecord() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(
            context.packageName,
            Manifest.permission.RECORD_AUDIO,
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val input = MicrophoneBreathInput(context, scope)

        try {
            assertTrue(input.start())
            Thread.sleep(300)
        } finally {
            runBlocking { input.stop() }
            scope.cancel()
        }
    }

    @Test
    fun repeatedStopWaitsForPreviousCaptureAndReleasesItsRecorder() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        instrumentation.uiAutomation.grantRuntimePermission(context.packageName, Manifest.permission.RECORD_AUDIO)
        instrumentation.startActivitySync(
            Intent().setClassName(context.packageName, "${context.packageName}.platform.LaunchActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val input = MicrophoneBreathInput(context, scope)
        val jobField = MicrophoneBreathInput::class.java.getDeclaredField("captureJob").apply { isAccessible = true }
        val recorderField = MicrophoneBreathInput::class.java.getDeclaredField("recorder").apply { isAccessible = true }
        try {
            repeat(20) {
                assertTrue(input.start())
                val job = jobField.get(input) as Job
                val recorder = recorderField.get(input) as AudioRecord
                input.stop()
                assertTrue("old capture still running after stop", job.isCompleted)
                assertEquals(AudioRecord.STATE_UNINITIALIZED, recorder.state)
                assertEquals(0f, input.intensity, 0f)
            }
        } finally {
            input.stop()
            scope.cancel()
        }
    }
}

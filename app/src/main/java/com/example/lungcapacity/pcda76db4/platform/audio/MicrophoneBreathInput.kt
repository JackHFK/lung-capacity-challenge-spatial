package com.example.lungcapacity.pcda76db4.platform.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.example.lungcapacity.pcda76db4.domain.usecase.BreathSignalProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

class MicrophoneBreathInput(
    private val context: Context,
    private val scope: CoroutineScope,
) {
    private var recorder: AudioRecord? = null
    private var captureJob: Job? = null

    private val measuredIntensity = MutableStateFlow(0f)
    private val measuredRms = MutableStateFlow(0f)
    val intensityFlow: StateFlow<Float> = measuredIntensity.asStateFlow()
    val rawRmsFlow: StateFlow<Float> = measuredRms.asStateFlow()
    val intensity: Float get() = measuredIntensity.value

    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (captureJob?.isActive == true) return true
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            Log.w(TAG, "microphone permission is not granted")
            return false
        }

        val minimumBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minimumBuffer <= 0) {
            Log.e(TAG, "invalid AudioRecord minimum buffer: $minimumBuffer")
            return false
        }
        val audioRecord = runCatching {
            AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL,
                ENCODING,
                maxOf(minimumBuffer, SAMPLE_COUNT * Short.SIZE_BYTES * 2),
            )
        }.getOrElse {
            Log.e(TAG, "failed to create AudioRecord", it)
            return false
        }
        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            Log.e(TAG, "AudioRecord did not initialize")
            audioRecord.release()
            return false
        }

        val processor = BreathSignalProcessor()
        measuredIntensity.value = 0f
        measuredRms.value = 0f
        recorder = audioRecord
        runCatching { audioRecord.startRecording() }.getOrElse {
            Log.e(TAG, "failed to start AudioRecord", it)
            recorder = null
            audioRecord.release()
            return false
        }
        captureJob = scope.launch(Dispatchers.IO) {
            val samples = ShortArray(SAMPLE_COUNT)
            try {
                while (isActive) {
                    val count = audioRecord.read(
                        samples,
                        0,
                        samples.size,
                        AudioRecord.READ_BLOCKING,
                    )
                    if (count > 0 && isActive) {
                        val rawRms = rms(samples, count)
                        measuredRms.value = rawRms
                        measuredIntensity.value = processor.process(rawRms)
                    } else if (count < 0) {
                        Log.w(TAG, "AudioRecord read failed: $count")
                        break
                    }
                }
            } finally {
                measuredIntensity.value = 0f
                measuredRms.value = 0f
            }
        }
        Log.i(TAG, "microphone capture started")
        return true
    }

    suspend fun stop() {
        val current = recorder
        captureJob?.cancel()
        runCatching {
            if (current?.recordingState == AudioRecord.RECORDSTATE_RECORDING) current.stop()
        }
        captureJob?.join()
        runCatching { current?.release() }
        captureJob = null
        recorder = null
        measuredIntensity.value = 0f
        measuredRms.value = 0f
        Log.i(TAG, "microphone capture stopped")
    }

    private fun rms(samples: ShortArray, count: Int): Float {
        var sum = 0.0
        for (index in 0 until count) {
            val normalized = samples[index] / Short.MAX_VALUE.toDouble()
            sum += normalized * normalized
        }
        return sqrt(sum / count).toFloat()
    }

    private companion object {
        const val TAG = "MicrophoneBreathInput"
        const val SAMPLE_RATE = 16_000
        const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        const val ENCODING = AudioFormat.ENCODING_PCM_16BIT
        const val SAMPLE_COUNT = 320
    }
}

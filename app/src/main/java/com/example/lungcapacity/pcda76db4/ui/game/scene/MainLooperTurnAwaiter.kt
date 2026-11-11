package com.example.lungcapacity.pcda76db4.ui.game.scene

import android.os.Handler
import android.os.Looper
import android.view.Choreographer
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

internal class MainLooperTurnAwaiter {
    private val handler = Handler(Looper.getMainLooper())

    suspend fun await() {
        check(Looper.myLooper() == Looper.getMainLooper()) {
            "Spatial SDK resource creation must remain on the main Looper"
        }
        val choreographer = Choreographer.getInstance()
        suspendCancellableCoroutine { continuation ->
            lateinit var callback: Choreographer.FrameCallback
            val resume = Runnable {
                if (continuation.isActive) continuation.resume(Unit)
            }
            callback = Choreographer.FrameCallback {
                if (continuation.isActive) handler.post(resume)
            }
            continuation.invokeOnCancellation {
                choreographer.removeFrameCallback(callback)
                handler.removeCallbacks(resume)
            }
            choreographer.postFrameCallback(callback)
        }
    }
}

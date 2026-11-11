package com.example.lungcapacity.pcda76db4.platform

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.pico.spatial.ui.platform.stub.SpatialLaunchActivity

class LaunchActivity : SpatialLaunchActivity() {
    private val requestMicrophonePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            Log.i(TAG, "RECORD_AUDIO permission granted=$granted")
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            requestMicrophonePermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private companion object {
        const val TAG = "LungCapacityPermission"
    }
}

package com.example.lungcapacity.pcda76db4.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.example.lungcapacity.pcda76db4.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}

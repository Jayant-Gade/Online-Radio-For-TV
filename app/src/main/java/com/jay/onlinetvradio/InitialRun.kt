package com.jay.onlinetvradio

import android.app.Application
import com.facebook.drawee.backends.pipeline.Fresco

class InitialRun : Application() {
    override fun onCreate() {
        super.onCreate()
        Fresco.initialize(this)
    }
}
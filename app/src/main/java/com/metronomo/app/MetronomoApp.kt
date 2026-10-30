package com.metronomo.app

import android.app.Application
import com.metronomo.app.audio.ChronometerEngine
import com.metronomo.app.audio.MetronomeEngine

class MetronomoApp : Application() {

    lateinit var metronome: MetronomeEngine
        private set

    lateinit var chronometer: ChronometerEngine
        private set

    override fun onCreate() {
        super.onCreate()
        metronome = MetronomeEngine(this)
        chronometer = ChronometerEngine(this)
    }
}

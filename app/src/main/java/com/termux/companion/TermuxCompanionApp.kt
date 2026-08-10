package com.termux.companion

import android.app.Application
import com.termux.companion.data.termux.TermuxResultReceiver
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class TermuxCompanionApp : Application() {
    companion object {
        lateinit var instance: TermuxCompanionApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        TermuxResultReceiver.register(this)
    }
}

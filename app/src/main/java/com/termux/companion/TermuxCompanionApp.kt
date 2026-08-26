package com.termux.companion

import android.app.Application
import com.termux.companion.data.termux.TermuxCommandExecutor
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors

@HiltAndroidApp
class TermuxCompanionApp : Application() {
    companion object {
        lateinit var instance: TermuxCompanionApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TermuxCommandExecutorEntryPoint {
    fun termuxCommandExecutor(): TermuxCommandExecutor
}
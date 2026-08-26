package com.termux.companion

import android.app.Application
import com.termux.companion.data.termux.TermuxCommandExecutor
import com.termux.companion.data.widget.WidgetSettingsRepository
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class TermuxCompanionApp : Application() {
    companion object {
        lateinit var instance: TermuxCompanionApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Clean up orphaned result files from previous sessions (FIX-008).
        val executor = EntryPointAccessors.fromApplication(
            this,
            TermuxCommandExecutorEntryPoint::class.java
        ).termuxCommandExecutor()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            executor.sweepStaleResultFiles()
        }
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface TermuxCommandExecutorEntryPoint {
    fun termuxCommandExecutor(): TermuxCommandExecutor
    fun widgetSettingsRepository(): WidgetSettingsRepository
}

package com.termux.companion

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import com.termux.companion.data.settings.SettingsRepository
import com.termux.companion.ui.components.BiometricLockGate
import com.termux.companion.ui.navigation.AppNavHost
import com.termux.companion.ui.theme.TermuxCompanionTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Extends [FragmentActivity] because androidx.biometric requires it (FEAT-005).
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            TermuxCompanionTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val settings by settingsRepository.settings.collectAsState(initial = null)
                    when {
                        settings == null -> Unit
                        settings!!.biometricLock -> BiometricLockGate { AppNavHost() }
                        else -> AppNavHost()
                    }
                }
            }
        }
    }
}

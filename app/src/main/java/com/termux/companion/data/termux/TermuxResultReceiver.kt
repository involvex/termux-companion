package com.termux.companion.data.termux

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

class TermuxResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val executionId = intent.getIntExtra(EXTRA_EXECUTION_ID, -1)
        Log.d(TAG, "=== Received broadcast for execution #$executionId ===")

        // Log all extras for debugging
        intent.extras?.keySet()?.forEach { key ->
            val value = intent.extras?.getString(key)
            Log.d(TAG, "  Extra[$key] = ${value?.take(100)}")
        }

        // Try to get result from the Termux plugin result bundle
        val pluginResultBundle = intent.getBundleExtra(TERMUX_PLUGIN_RESULT_BUNDLE)
        if (pluginResultBundle != null) {
            val stdout = pluginResultBundle.getString("stdout", "")
            val stderr = pluginResultBundle.getString("stderr", "")
            val exitCode = pluginResultBundle.getInt("exit_code", -1)
            Log.d(TAG, "Plugin bundle result: exit=$exitCode stdout=${stdout.take(80)}")
            TermuxCommandExecutor.deliverResult(executionId, stdout, stderr, exitCode)
            return
        }

        // Fallback: try individual extras
        val stdout = intent.getStringExtra("stdout") ?: ""
        val stderr = intent.getStringExtra("stderr") ?: ""
        val exitCode = intent.getIntExtra("exit_code", -1)
        if (stdout.isNotBlank() || stderr.isNotBlank() || exitCode != -1) {
            Log.d(TAG, "Individual extras result: exit=$exitCode stdout=${stdout.take(80)}")
            TermuxCommandExecutor.deliverResult(executionId, stdout, stderr, exitCode)
            return
        }

        // Final fallback: treat whole intent as having no result
        Log.w(TAG, "No result data found in broadcast for #$executionId")
        TermuxCommandExecutor.deliverResult(executionId, "", "Result not found in broadcast", -1)
    }

    companion object {
        private const val TAG = "TermuxResultRecv"
        const val EXTRA_EXECUTION_ID = "execution_id"
        const val ACTION_TERMUX_RESULT = "com.termux.companion.TERMUX_RESULT"

        // Termux sends results in a bundle with this EXACT key:
        // https://github.com/termux/termux-app/blob/master/termux-shared/src/main/java/com/termux/shared/termux/TermuxConstants.java
        const val TERMUX_PLUGIN_RESULT_BUNDLE = "com.termux.service.extra.plugin_result_bundle"

        fun register(context: Context): TermuxResultReceiver {
            val receiver = TermuxResultReceiver()
            val filter = IntentFilter(ACTION_TERMUX_RESULT)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
            } else {
                context.registerReceiver(receiver, filter)
            }
            Log.d(TAG, "BroadcastReceiver registered with action: $ACTION_TERMUX_RESULT")
            return receiver
        }
    }
}

package com.termux.companion.data.termux

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TermuxCommandExecutor @Inject constructor(
    @ApplicationContext private val context: Context
) {
    companion object {
        const val TERMUX_PACKAGE = "com.termux"
        const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
        const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
        const val EXTRA_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
        const val EXTRA_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
        const val EXTRA_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
        const val EXTRA_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
        const val EXTRA_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
        const val EXTRA_PENDING_INTENT = "com.termux.RUN_COMMAND_EXTRA_PENDING_INTENT"

        private const val TAG = "TermuxCmdExec"
        private var nextId = 1
        private val callbacks = mutableMapOf<Int, (String, String, Int) -> Unit>()

        fun registerCallback(id: Int, cb: (String, String, Int) -> Unit) { callbacks[id] = cb }
        fun deliverResult(id: Int, stdout: String, stderr: String, exitCode: Int) {
            callbacks.remove(id)?.invoke(stdout, stderr, exitCode)
        }

        fun getDownloadDir(): File {
            return Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }
    }

    fun isTermuxInstalled() = try {
        context.packageManager.getPackageInfo(TERMUX_PACKAGE, 0); true
    } catch (e: Exception) { false }

    fun hasRunCommandPermission() =
        context.checkSelfPermission("com.termux.permission.RUN_COMMAND") ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun canReadSharedStorage(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager()
        }
        return context.checkSelfPermission(android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
    }

    suspend fun executeWithResult(
        command: String,
        workdir: String = "/data/data/com.termux/files/home",
        callback: (stdout: String, stderr: String, exitCode: Int) -> Unit
    ) {
        val id = nextId++
        registerCallback(id, callback)

        // Also try PendingIntent as a faster path
        val downloadDir = getDownloadDir()
        downloadDir.mkdirs()
        val resultFile = File(downloadDir, "tc-out-$id.txt")
        val doneFile = File(downloadDir, "tc-done-$id.txt")

        // Wrap command: write stdout+stderr to resultFile, exit code to doneFile
        val wrapped = "{ $command; } > \"${resultFile.absolutePath}\" 2>&1; echo \$? > \"${doneFile.absolutePath}\""
        sendToTermuxWithPI(command, workdir, id) // try PI with original command
        sendToTermux(wrapped, workdir)            // actual: file-based via wrapped command

        Log.d(TAG, "Command #$id wrapped to: $wrapped")
        Log.d(TAG, "Polling for: ${resultFile.absolutePath}")

        // Primary: file-based polling via shared storage
        withContext(Dispatchers.IO) {
            Log.d(TAG, "Polling for results in: ${downloadDir.absolutePath}")

            for (attempt in 1..20) {
                delay(500)
                try {
                    if (doneFile.exists()) {
                        val exitCode = doneFile.readText().trim().toIntOrNull() ?: -1
                        if (resultFile.exists()) {
                            val stdout = resultFile.readText()
                            resultFile.delete()
                            doneFile.delete()
                            Log.d(TAG, "Got result for #$id: ${stdout.take(80)}")
                            withContext(Dispatchers.Main) { callback(stdout, "", exitCode) }
                            callbacks.remove(id)
                            return@withContext
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Poll error for #$id: ${e.message}")
                }
            }
            Log.w(TAG, "Timeout for #$id")
            val hint = if (!canReadSharedStorage()) {
                "Storage permission needed. On Android 11+: Settings > Apps > Termux Companion > 'All files access'"
            } else {
                "Check: 1) 'termux-setup-storage' run in Termux? 2) File at ${resultFile.absolutePath} exists?"
            }
            withContext(Dispatchers.Main) {
                callback("", "Timeout: no result from Termux after 10s.\n\n$hint", -1)
            }
            callbacks.remove(id)
        }
    }

    fun executeCommandNoResult(command: String, workdir: String = "/data/data/com.termux/files/home") {
        sendToTermux(command, workdir)
    }

    private fun sendToTermuxWithPI(command: String, workdir: String, id: Int) {
        try {
            val resultIntent = Intent(TermuxResultReceiver.ACTION_TERMUX_RESULT).apply {
                setPackage(context.packageName)
                putExtra("execution_id", id)
            }
            val pi = PendingIntent.getBroadcast(context, id, resultIntent,
                PendingIntent.FLAG_ONE_SHOT or PendingIntent.FLAG_MUTABLE)
            val intent = Intent().apply {
                setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
                setAction(ACTION_RUN_COMMAND)
                putExtra(EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
                putExtra(EXTRA_ARGUMENTS, arrayOf("-c", command))
                putExtra(EXTRA_WORKDIR, workdir)
                putExtra(EXTRA_BACKGROUND, true)
                putExtra(EXTRA_SESSION_ACTION, "0")
                putExtra(EXTRA_PENDING_INTENT, pi)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
            else context.startService(intent)
        } catch (e: Exception) { Log.w(TAG, "PI send failed: ${e.message}") }
    }

    private fun sendToTermux(command: String, workdir: String) {
        val intent = Intent().apply {
            setClassName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            setAction(ACTION_RUN_COMMAND)
            putExtra(EXTRA_COMMAND_PATH, "/data/data/com.termux/files/usr/bin/bash")
            putExtra(EXTRA_ARGUMENTS, arrayOf("-c", command))
            putExtra(EXTRA_WORKDIR, workdir)
            putExtra(EXTRA_BACKGROUND, true)
            putExtra(EXTRA_SESSION_ACTION, "0")
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent)
        else context.startService(intent)
    }
}

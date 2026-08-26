package com.termux.companion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.termux.companion.R
import com.termux.companion.TermuxCommandExecutorEntryPoint
import com.termux.companion.data.widget.WidgetSettingsRepository
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

internal const val ACTION_WIDGET_CLICK = "com.termux.companion.widget.ACTION_WIDGET_CLICK"
internal const val EXTRA_WIDGET_ID = "appWidgetId"
internal const val DEFAULT_COMMAND = "ls -la"
internal const val DEFAULT_LABEL = "Terminal"

/** Reads the stored config and pushes fresh RemoteViews for one widget instance. */
internal suspend fun refreshWidget(context: Context, appWidgetId: Int) {
    val manager = AppWidgetManager.getInstance(context) ?: return
    if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

    val repository = entryPoint(context).widgetSettingsRepository()
    val config = runCatching { repository.getConfig(appWidgetId) }.getOrNull()

    val views = RemoteViews(context.packageName, R.layout.widget_termux)
    views.setTextViewText(R.id.widget_button, config?.label?.ifBlank { null } ?: DEFAULT_LABEL)
    views.setOnClickPendingIntent(R.id.widget_button, clickPendingIntent(context, appWidgetId))
    manager.updateAppWidget(appWidgetId, views)
}

private fun clickPendingIntent(context: Context, appWidgetId: Int): PendingIntent {
    val intent = Intent(context, TermuxWidgetProvider::class.java).apply {
        action = ACTION_WIDGET_CLICK
        putExtra(EXTRA_WIDGET_ID, appWidgetId)
    }
    return PendingIntent.getBroadcast(
        context,
        appWidgetId,
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

private fun entryPoint(context: Context): TermuxCommandExecutorEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, TermuxCommandExecutorEntryPoint::class.java)

/**
 * Home-screen widget executing a user-configured command (FEAT-004).
 * Per-widget command/label live in [WidgetSettingsRepository]; the click
 * broadcast carries only the widget id, never the command itself.
 */
class TermuxWidgetProvider : AppWidgetProvider() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (appWidgetId in appWidgetIds) {
            updateAsync(context, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?
    ) {
        updateAsync(context, appWidgetId)
        super.onAppWidgetOptionsChanged(context, appWidgetManager, appWidgetId, newOptions)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action != ACTION_WIDGET_CLICK) return

        val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return

        val pendingResult = goAsync()
        scope.launch {
            try {
                val repository = entryPoint(context).widgetSettingsRepository()
                val command = runCatching { repository.getConfig(appWidgetId)?.command }
                    .getOrNull()
                    ?.takeIf { it.isNotBlank() }
                    ?: DEFAULT_COMMAND

                entryPoint(context).termuxCommandExecutor().executeCommandNoResult(command = command)
            } finally {
                pendingResult.finish()
            }
        }
    }

    /** Purges per-widget keys so DataStore never accumulates stale entries. */
    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val repository = entryPoint(context).widgetSettingsRepository()
                appWidgetIds.forEach { id -> runCatching { repository.clear(id) } }
            } finally {
                pendingResult.finish()
            }
        }
        super.onDeleted(context, appWidgetIds)
    }

    private fun updateAsync(context: Context, appWidgetId: Int) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                refreshWidget(context.applicationContext, appWidgetId)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

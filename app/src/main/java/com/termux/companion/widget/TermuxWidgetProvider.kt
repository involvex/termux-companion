package com.termux.companion.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.termux.companion.R
import com.termux.companion.TermuxCommandExecutorEntryPoint
import com.termux.companion.data.termux.TermuxCommandExecutor
import dagger.hilt.android.EntryPointAccessors

/**
 * AppWidgetProvider for Termux Companion that allows executing commands from the home screen.
 */
class TermuxWidgetProvider : AppWidgetProvider() {

    companion object {
        private const val ACTION_WIDGET_CLICK = "com.termux.companion.widget.ACTION_WIDGET_CLICK"
        private const val EXTRA_WIDGET_ID = "appWidgetId"
        private const val EXTRA_COMMAND = "command"
        private const val DEFAULT_COMMAND = "ls -la"
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        // Update each widget instance
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
        super.onUpdate(context, appWidgetManager, appWidgetIds)
    }

    private fun updateAppWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // Create the RemoteViews for the widget layout
        val views = RemoteViews(context.packageName, R.layout.widget_termux)

        // Set up the click intent for the widget button
        val clickIntent = Intent(context, TermuxWidgetProvider::class.java).apply {
            action = ACTION_WIDGET_CLICK
            putExtra(EXTRA_WIDGET_ID, appWidgetId)
            putExtra(EXTRA_COMMAND, DEFAULT_COMMAND) // TODO: Make configurable
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            appWidgetId,
            clickIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        views.setOnClickPendingIntent(R.id.widget_button, pendingIntent)

        // Instruct the widget manager to update the widget
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_WIDGET_CLICK) {
            val appWidgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            val command = intent.getStringExtra(EXTRA_COMMAND) ?: DEFAULT_COMMAND

            if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                // Get the TermuxCommandExecutor via Hilt entry point
                val executor = EntryPointAccessors.fromApplication(
                    context,
                    TermuxCommandExecutorEntryPoint::class.java
                ).termuxCommandExecutor()

                // Execute the command in Termux (no result needed for widget)
                executor.executeCommandNoResult(
                    command = command,
                    workdir = "/data/data/com.termux/files/home"
                )
            }
        }
    }

    /**
     * Called when widgets are deleted. We don't need to do anything special here.
     */
    override fun onDeleted(
        context: Context,
        appWidgetIds: IntArray
    ) {
        super.onDeleted(context, appWidgetIds)
    }

    /**
     * Called when the first widget is created.
     */
    override fun onEnabled(context: Context) {
        super.onEnabled(context)
    }

    /**
     * Called when the last widget is deleted.
     */
    override fun onDisabled(context: Context) {
        super.onDisabled(context)
    }
}
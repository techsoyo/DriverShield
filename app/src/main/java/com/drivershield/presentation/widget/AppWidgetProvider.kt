package com.drivershield.presentation.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.widget.RemoteViews

/**
 * Widget de pantalla de inicio – reservado para v1.1.
 * Mostrará el estado del turno activo y el tiempo transcurrido.
 */
class AppWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        appWidgetIds.forEach { appWidgetId ->
            updateWidget(context, appWidgetManager, appWidgetId)
        }
    }

    private fun updateWidget(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int
    ) {
        // TODO (v1.1): Cargar estado del turno activo y actualizar RemoteViews
        val views = RemoteViews(context.packageName, android.R.layout.simple_list_item_1)
        appWidgetManager.updateAppWidget(appWidgetId, views)
    }
}

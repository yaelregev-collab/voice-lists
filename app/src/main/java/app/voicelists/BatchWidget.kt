package app.voicelists

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/** Home screen button for adding several items in a row: pick a list and a category, then say the items. */
class BatchWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        update(context, manager, ids)
    }

    companion object {
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(ComponentName(context, BatchWidget::class.java))
            if (ids.isNotEmpty()) update(context, manager, ids)
        }

        private fun update(context: Context, manager: AppWidgetManager, ids: IntArray) {
            // Its own action and request code, so this PendingIntent isn't merged with the other widget's.
            val intent = Intent(context, VoiceActivity::class.java)
                .setAction("app.voicelists.ADD_MANY")
                .putExtra(VoiceActivity.EXTRA_BATCH, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            val pending = PendingIntent.getActivity(context, 1, intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val views = RemoteViews(context.packageName, R.layout.widget_batch).apply {
                setContentDescription(R.id.widget_batch_icon, Lang.strings(context).batchTitle)
                setOnClickPendingIntent(R.id.widget_batch_root, pending)
            }
            manager.updateAppWidget(ids, views)
        }
    }
}

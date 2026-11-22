package com.personalfinances.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.personalfinances.FinanceApp
import com.personalfinances.R
import com.personalfinances.data.db.DetectionEntity
import com.personalfinances.domain.Money
import com.personalfinances.ui.QuickAddActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object DetectionNotifier {
    const val CHANNEL = "detections"
    const val ACTION_DISMISS = "com.personalfinances.DISMISS_DETECTION"
    const val EXTRA_ID = "detection_id"

    const val CHANNEL_REMINDERS = "reminders"

    fun createChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "Movimientos por confirmar", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Compras detectadas en tus bancos y movimientos programados"
        })
        nm.createNotificationChannel(NotificationChannel(CHANNEL_REMINDERS, "Recordatorios de tarjetas", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Vencimientos y cierres de tus tarjetas de crédito"
        })
    }

    private fun canPost(context: Context) = android.os.Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /** Recordatorio de tarjeta: abre la app. */
    fun showReminder(context: Context, id: Int, title: String, text: String) {
        if (!canPost(context)) return
        val open = PendingIntent.getActivity(
            context, id, Intent(context, com.personalfinances.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    fun show(context: Context, d: DetectionEntity) {
        if (!canPost(context)) return
        val amount = Money.format(d.amount, d.currency)
        val what = if (d.isIncome) "Ingreso" else "Gasto"
        val title = if (d.recurringId != null) "Programado: ${d.merchant ?: what} $amount"
        else "$what de $amount" + (d.merchant?.let { " en $it" } ?: "")
        val subtitle = if (d.recurringId != null) "${d.text} · Toca para confirmar" else "Detectado en ${d.appLabel} · Toca para confirmar"
        val open = PendingIntent.getActivity(
            context, d.id.toInt(),
            QuickAddActivity.intent(context, detectionId = d.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val dismiss = PendingIntent.getBroadcast(
            context, d.id.toInt(),
            Intent(context, DismissReceiver::class.java).setAction(ACTION_DISMISS).putExtra(EXTRA_ID, d.id),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Registrar", open)
            .addAction(0, "Ignorar", dismiss)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setGroup(GROUP)
            .build()
        NotificationManagerCompat.from(context).notify(d.id.toInt(), n)
        postSummary(context)
    }

    private const val GROUP = "detections_group"
    private const val SUMMARY_ID = 1

    /** Con 2+ movimientos por confirmar, se agrupan bajo un resumen. */
    private fun postSummary(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        val count = nm.activeNotifications.count { it.notification.group == GROUP && it.id != SUMMARY_ID }
        if (count < 2) return
        val open = PendingIntent.getActivity(
            context, SUMMARY_ID, Intent(context, com.personalfinances.ui.MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val summary = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$count movimientos por confirmar")
            .setContentIntent(open)
            .setGroup(GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(SUMMARY_ID, summary)
    }

    fun cancel(context: Context, id: Long) = NotificationManagerCompat.from(context).cancel(id.toInt())
}

class DismissReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getLongExtra(DetectionNotifier.EXTRA_ID, -1)
        if (id < 0) return
        DetectionNotifier.cancel(context, id)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            (context.applicationContext as FinanceApp).container.repository.dismissDetection(id)
            pending.finish()
        }
    }
}

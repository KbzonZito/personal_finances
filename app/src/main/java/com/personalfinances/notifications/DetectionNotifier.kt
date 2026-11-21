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

    fun createChannel(context: Context) {
        val channel = NotificationChannel(CHANNEL, "Movimientos detectados", NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = "Te pide confirmar compras detectadas en notificaciones de tus bancos"
        }
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    fun show(context: Context, d: DetectionEntity) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val amount = Money.format(d.amount, d.currency)
        val what = if (d.isIncome) "Ingreso" else "Gasto"
        val title = "$what de $amount" + (d.merchant?.let { " en $it" } ?: "")
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
            .setContentText("Detectado en ${d.appLabel} · Toca para confirmar")
            .setContentIntent(open)
            .setAutoCancel(true)
            .addAction(0, "Registrar", open)
            .addAction(0, "Ignorar", dismiss)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        NotificationManagerCompat.from(context).notify(d.id.toInt(), n)
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

package com.personalfinances.notifications

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import com.personalfinances.FinanceApp
import com.personalfinances.data.db.DetectionEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Escucha notificaciones SOLO de las apps que el usuario eligió, y solo si activó la detección.
 * Nada se registra automáticamente: cada compra detectada queda pendiente hasta que el usuario la confirma.
 */
class PurchaseNotificationListener : NotificationListenerService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val container = (application as FinanceApp).container
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString()
        if (sbn.notification.flags and Notification.FLAG_GROUP_SUMMARY != 0) return

        scope.launch {
            val settings = container.settings.current()
            if (!settings.detectionEnabled || sbn.packageName !in settings.watchedPackages) return@launch
            val repo = container.repository
            val rules = repo.parserRules()
            val label = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(sbn.packageName, 0)).toString()
            }.getOrDefault(sbn.packageName)
            if (NotificationParser.applyRules(sbn.packageName, title, text, rules, settings.mainCurrency) == RuleOutcome.Ignore) return@launch
            val parsed = NotificationParser.parse(sbn.packageName, title, text, settings.mainCurrency, rules)
            if (parsed == null) {
                // Tiene montos pero no se entendió: queda para "Enséñale al lector".
                val full = NotificationParser.fullText(title, text)
                if (NotificationParser.amounts(full, settings.mainCurrency).isNotEmpty()) {
                    repo.addUnrecognized(sbn.packageName, label, title.orEmpty(), text.orEmpty(), sbn.postTime)
                }
                return@launch
            }
            val detection = DetectionEntity(
                packageName = sbn.packageName,
                appLabel = label,
                title = title.orEmpty(),
                text = text.orEmpty(),
                amount = parsed.amount,
                currencyCode = parsed.currency.code,
                merchant = parsed.merchant,
                isIncome = parsed.isIncome,
                lastDigits = parsed.lastDigits,
                cardKind = parsed.cardKind?.name,
                txKind = parsed.kind.name,
                postedAt = sbn.postTime,
            )
            val id = container.repository.addDetection(detection) ?: return@launch
            DetectionNotifier.show(applicationContext, detection.copy(id = id))
        }
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

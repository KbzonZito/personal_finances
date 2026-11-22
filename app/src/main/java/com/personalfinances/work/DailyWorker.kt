package com.personalfinances.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personalfinances.AppContainer
import com.personalfinances.FinanceApp
import com.personalfinances.data.RatesFetcher
import com.personalfinances.data.backup.AutoBackup
import com.personalfinances.domain.CardForReminder
import com.personalfinances.domain.Reminders
import com.personalfinances.notifications.DetectionNotifier
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Tareas periódicas: rentabilidad, recurrentes, recordatorios, tasas y respaldo automático. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runDaily(applicationContext, (applicationContext as FinanceApp).container)
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val wm = WorkManager.getInstance(context)
            wm.cancelUniqueWork("yield") // worker anterior (v0.2)
            val request = PeriodicWorkRequestBuilder<DailyWorker>(6, TimeUnit.HOURS).build()
            wm.enqueueUniquePeriodicWork("daily", ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** También se llama al abrir la app. Cada paso es independiente: si uno falla, los demás siguen. */
        suspend fun runDaily(context: Context, c: AppContainer) {
            val repo = c.repository
            runCatching { repo.accrueYield() }
            runCatching { repo.expireOldPending() }
            runCatching { repo.processRecurring().forEach { DetectionNotifier.show(context, it) } }
            runCatching { sendReminders(context, c) }
            runCatching {
                val h = LocalTime.now().hour
                if (h in 8..21) sendBudgetAlerts(context, c, c.repository.snapshot.value)
            }
            runCatching {
                val s = c.settings.current()
                if (System.currentTimeMillis() - s.ratesUpdatedAt > 20 * 3600_000L) c.settings.setRates(RatesFetcher.fetch(s.rates))
            }
            runCatching { AutoBackup.runIfDue(context, c) }
        }

        /** Alertas de presupuesto (80% y 100%), una vez por mes cada una. */
        suspend fun sendBudgetAlerts(context: Context, c: AppContainer, snap: com.personalfinances.data.FinanceSnapshot) {
            val settings = c.settings.current()
            if (!settings.budgetAlerts || !snap.loaded) return
            val names = snap.categories.associate { it.id to it.name }
            val alerts = com.personalfinances.domain.BudgetCalc.alerts(snap.budgets, names, settings.mainCurrency, LocalDate.now(), settings.sentReminders)
            alerts.forEach { DetectionNotifier.showReminder(context, 200_000 + Math.floorMod(it.key.hashCode(), 100_000), it.title, it.text) }
            if (alerts.isNotEmpty()) c.settings.addSentReminders(alerts.map { it.key })
        }

        private suspend fun sendReminders(context: Context, c: AppContainer) {
            val settings = c.settings.current()
            if (!settings.remindersEnabled) return
            // No molestar de noche: la próxima ejecución lo enviará.
            val hour = LocalTime.now().hour
            if (hour < 8 || hour >= 22) return
            val snap = withTimeoutOrNull(5_000) { c.repository.snapshot.first { it.loaded } } ?: return
            val cards = snap.cards.mapNotNull { st -> st.card?.let { CardForReminder(st.account.id, st.account.name, st.account.currency, it) } }
            val reminders = Reminders.compute(cards, LocalDate.now(), settings.sentReminders)
            reminders.forEach { DetectionNotifier.showReminder(context, 100_000 + Math.floorMod(it.key.hashCode(), 100_000), it.title, it.text) }
            if (reminders.isNotEmpty()) c.settings.addSentReminders(reminders.map { it.key })
        }
    }
}

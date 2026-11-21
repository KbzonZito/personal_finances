package com.personalfinances

import android.app.Application
import android.content.Context
import com.personalfinances.data.FinanceRepository
import com.personalfinances.data.backup.BackupManager
import com.personalfinances.data.db.AppDatabase
import com.personalfinances.data.prefs.SettingsStore
import com.personalfinances.notifications.DetectionNotifier
import com.personalfinances.widget.QuickAddWidget
import com.personalfinances.work.DailyWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val db = AppDatabase.build(context)
    val settings = SettingsStore(context)
    val repository = FinanceRepository(db, settings, scope) { QuickAddWidget.refresh(context) }
    val backup = BackupManager(context, db)
}

class FinanceApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        DetectionNotifier.createChannel(this)
        runCatching { DailyWorker.schedule(this) }
        container.scope.launch {
            container.repository.ensureSeeded()
            DailyWorker.runDaily(this@FinanceApp, container)
        }
    }
}

val Context.container: AppContainer get() = (applicationContext as FinanceApp).container

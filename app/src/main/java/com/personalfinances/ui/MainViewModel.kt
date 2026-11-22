package com.personalfinances.ui

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.personalfinances.AppContainer
import com.personalfinances.data.FinanceSnapshot
import com.personalfinances.data.RatesFetcher
import com.personalfinances.data.TransactionDraft
import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.CategoryEntity
import com.personalfinances.data.db.RecurringEntity
import com.personalfinances.data.backup.AutoBackup
import com.personalfinances.data.prefs.ThemeMode
import com.personalfinances.domain.Currency
import com.personalfinances.domain.ExchangeRates
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val c: AppContainer) : ViewModel() {
    private val appContext = c.appContext
    val snapshot: StateFlow<FinanceSnapshot> = c.repository.snapshot
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    private fun io(block: suspend () -> Unit) = c.scope.launch {
        runCatching { block() }.onFailure { _messages.tryEmit(it.message ?: "Ocurrió un error") }
    }

    fun saveTransaction(d: TransactionDraft, onDone: () -> Unit = {}) = io {
        val id = c.repository.save(d)
        onDone()
        if (d.type == com.personalfinances.data.db.TxType.EXPENSE) {
            // Espera a que el estado incluya el gasto y revisa presupuestos al tiro.
            val snap = kotlinx.coroutines.withTimeoutOrNull(3_000) { c.repository.snapshot.first { s -> s.transactions.any { it.tx.id == id } } }
            if (snap != null) com.personalfinances.work.DailyWorker.sendBudgetAlerts(appContext, c, snap)
        }
    }
    fun deleteTransaction(id: Long) = io { c.repository.deleteTransaction(id) }

    /** Borra y ofrece deshacer en el snackbar. */
    fun deleteWithUndo(id: Long, showUndo: suspend () -> Boolean) = c.scope.launch {
        val deleted = c.repository.deleteForUndo(id) ?: return@launch
        if (kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) { showUndo() }) c.repository.restore(deleted)
    }
    fun saveAccount(a: AccountEntity) = io { c.repository.saveAccount(a) }

    /** Guarda la tarjeta y, si se ingresó su estado actual, la cuadra con el banco. */
    fun saveCard(a: AccountEntity, available: Long?, billed: Long?, next: Long?) = io {
        val id = c.repository.saveAccount(a)
        if (available != null) c.repository.reconcileCard(id, available, billed ?: 0, next)
    }

    fun reconcileCard(cardId: Long, available: Long, billed: Long, next: Long?) = io {
        val n = c.repository.reconcileCard(cardId, available, billed, next)
        _messages.tryEmit(if (n == 0) "Ya estaba cuadrada con tu banco" else "Tarjeta cuadrada con tu banco ✓")
    }
    fun deleteAccount(a: AccountEntity) = io { c.repository.deleteAccount(a) }
    fun saveCategory(cat: CategoryEntity) = io { c.repository.saveCategory(cat) }
    fun dismissDetection(id: Long) = io { c.repository.dismissDetection(id) }

    fun setMainCurrency(cur: Currency) = io { c.settings.setMainCurrency(cur) }
    fun setTheme(t: ThemeMode) = io { c.settings.setTheme(t) }
    fun setDynamic(v: Boolean) = io { c.settings.setDynamicColor(v) }
    fun setHideAmounts(v: Boolean) = io { c.settings.setHideAmounts(v) }
    fun setDetectionEnabled(v: Boolean) = io { c.settings.setDetectionEnabled(v) }
    fun setWatchedPackages(v: Set<String>) = io { c.settings.setWatchedPackages(v) }
    fun setRates(r: ExchangeRates) = io { c.settings.setRates(r) }
    fun setLockEnabled(v: Boolean) = io { c.settings.setLockEnabled(v) }
    fun setLockTimeout(minutes: Int) = io { c.settings.setLockTimeout(minutes) }
    fun setSecureScreen(v: Boolean) = io { c.settings.setSecureScreen(v) }
    fun setRemindersEnabled(v: Boolean) = io { c.settings.setRemindersEnabled(v) }
    fun setBudgetAlerts(v: Boolean) = io { c.settings.setBudgetAlerts(v) }
    fun saveBudget(categoryId: Long, amount: Long) = io { c.repository.saveBudget(com.personalfinances.data.db.BudgetEntity(categoryId, amount)) }
    fun deleteBudget(categoryId: Long) = io { c.repository.deleteBudget(categoryId) }
    val unrecognized = c.repository.unrecognized.stateIn(viewModelScope, kotlinx.coroutines.flow.SharingStarted.Eagerly, emptyList())

    fun teach(u: com.personalfinances.data.db.UnrecognizedEntity, kind: String, ordinal: Int, prefix: String?, suffix: String?) = io {
        val created = c.repository.teach(u, kind, ordinal, prefix, suffix)
        created.forEach { com.personalfinances.notifications.DetectionNotifier.show(appContext, it) }
        _messages.tryEmit(
            when {
                kind == "IGNORE" -> "Listo: no te avisaré de este tipo de notificación"
                created.isEmpty() -> "Aprendido ✓"
                else -> "Aprendido ✓ ${created.size} movimiento(s) listos para confirmar"
            }
        )
    }

    fun dismissUnrecognized(id: Long) = io { c.repository.dismissUnrecognized(id) }

    fun saveGoal(g: com.personalfinances.data.db.GoalEntity) = io { c.repository.saveGoal(g) }
    fun deleteGoal(id: Long) = io { c.repository.deleteGoal(id) }
    fun finishOnboarding() = io { c.settings.setOnboardingDone() }
    fun saveRecurring(r: RecurringEntity) = io { c.repository.saveRecurring(r) }
    fun deleteRecurring(id: Long) = io { c.repository.deleteRecurring(id) }

    fun enableAutoBackup(context: android.content.Context, tree: Uri) = viewModelScope.launch {
        runCatching {
            AutoBackup.persistPermission(context, tree)
            c.settings.setAutoBackupUri(tree.toString())
            AutoBackup.runNow(context, c, tree)
        }.onSuccess { _messages.emit("Respaldo automático activado") }
            .onFailure { _messages.emit("No se pudo usar esa carpeta: ${it.message}") }
    }

    fun backupNow(context: android.content.Context) = viewModelScope.launch {
        val uri = c.settings.current().autoBackupUri ?: return@launch
        runCatching { AutoBackup.runNow(context, c, Uri.parse(uri)) }
            .onSuccess { _messages.emit("Respaldo guardado ($it movimientos)") }
            .onFailure { _messages.emit("Error al respaldar: ${it.message}") }
    }

    fun disableAutoBackup() = io { c.settings.setAutoBackupUri(null) }

    fun refreshRates() = viewModelScope.launch {
        runCatching { RatesFetcher.fetch(c.settings.current().rates) }
            .onSuccess { c.settings.setRates(it); _messages.emit("Tasas actualizadas") }
            .onFailure { _messages.emit("No se pudieron actualizar las tasas") }
    }

    fun exportBackup(uri: Uri) = viewModelScope.launch {
        runCatching { c.backup.export(uri) }
            .onSuccess { _messages.emit("Respaldo guardado ($it movimientos)") }
            .onFailure { _messages.emit("Error al respaldar: ${it.message}") }
    }

    fun importBackup(uri: Uri) = viewModelScope.launch {
        runCatching { c.backup.import(uri) }
            .onSuccess { _messages.emit("Respaldo restaurado ($it movimientos)") }
            .onFailure { _messages.emit("Archivo inválido: ${it.message}") }
    }

    companion object {
        fun factory(c: AppContainer) = viewModelFactory { initializer { MainViewModel(c) } }
    }
}

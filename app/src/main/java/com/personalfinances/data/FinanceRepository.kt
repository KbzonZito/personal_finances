package com.personalfinances.data

import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.AccountType
import com.personalfinances.data.db.AppDatabase
import com.personalfinances.data.db.CategoryEntity
import com.personalfinances.data.db.DefaultCategories
import com.personalfinances.data.db.DetectionEntity
import com.personalfinances.data.db.DetectionStatus
import com.personalfinances.data.db.InstallmentEntity
import com.personalfinances.data.db.MerchantRuleEntity
import com.personalfinances.data.db.SourceRuleEntity
import com.personalfinances.data.db.RecurringEntity
import com.personalfinances.domain.Frequency
import com.personalfinances.domain.Recurrence
import com.personalfinances.data.db.SystemCategory
import com.personalfinances.data.db.toYearMonth
import androidx.room.withTransaction
import com.personalfinances.data.db.TransactionEntity
import com.personalfinances.data.db.TxSource
import com.personalfinances.data.db.TxType
import com.personalfinances.data.db.toPeriod
import com.personalfinances.data.prefs.SettingsStore
import com.personalfinances.domain.InstallmentPlanner
import com.personalfinances.domain.InterestSpec
import com.personalfinances.domain.YieldAccrual
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

data class TransactionDraft(
    val id: Long = 0,
    val type: TxType,
    val accountId: Long,
    val toAccountId: Long? = null,
    val amount: Long,
    val toAmount: Long? = null,
    val date: LocalDate,
    val description: String = "",
    val categoryId: Long? = null,
    val installments: Int = 1,
    val installmentsPaid: Int = 0,
    val interest: InterestSpec = InterestSpec.None,
    val deferMonths: Int = 0,
    val source: TxSource = TxSource.MANUAL,
    val detectionId: Long? = null,
    /** Si no es null, además crea un movimiento recurrente desde la próxima ocurrencia. */
    val repeat: Frequency? = null,
)

class FinanceRepository(
    private val db: AppDatabase,
    val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    private val onDataChanged: suspend () -> Unit,
) {
    private data class Raw(
        val accounts: List<AccountEntity>,
        val categories: List<CategoryEntity>,
        val txs: List<TransactionEntity>,
        val installments: List<InstallmentEntity>,
    )

    private val raw = combine(
        db.accounts().observeAll(),
        db.categories().observeAll(),
        db.transactions().observeAll(),
        db.installments().observeAll(),
    ) { a, c, t, i -> Raw(a, c, t, i) }

    /** Estado completo de la app, calculado una sola vez y compartido por todas las pantallas. */
    val snapshot: StateFlow<FinanceSnapshot> = combine(
        raw, settingsStore.settings, db.detections().observePending(), db.recurring().observeAll(),
    ) { r, s, p, rec -> FinanceSnapshot.build(s, r.accounts, r.categories, r.txs, r.installments, p, rec) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, FinanceSnapshot())

    suspend fun ensureSeeded() {
        if (db.categories().count() == 0) db.categories().insertAll(DefaultCategories.all())
    }

    // ---------- Cuentas ----------

    suspend fun account(id: Long) = db.accounts().get(id)

    suspend fun saveAccount(account: AccountEntity): Long {
        val toSave = if (account.type == AccountType.YIELD && account.lastAccrualEpochDay == 0L) {
            account.copy(lastAccrualEpochDay = LocalDate.now().toEpochDay())
        } else account
        val id = db.accounts().upsert(toSave).let { if (it == -1L) account.id else it }
        onDataChanged()
        return id
    }

    suspend fun deleteAccount(account: AccountEntity) {
        db.withTransaction {
            // Conserva el efecto en las OTRAS cuentas: sus transferencias con la cuenta eliminada
            // pasan a ser gastos/ingresos normales, así sus saldos no cambian.
            val label = "Transferencia ${account.name} (eliminada)"
            db.transactions().convertIncomingTransfers(account.id, label)
            db.installments().deleteForAccount(account.id)
            db.transactions().convertOutgoingTransfers(account.id, label)
            db.transactions().deleteOwned(account.id)
            db.rules().deleteSourceRulesFor(account.id)
            db.recurring().deleteForAccount(account.id)
            db.accounts().delete(account)
        }
        onDataChanged()
    }

    suspend fun saveCategory(category: CategoryEntity) = db.categories().upsert(category)

    // ---------- Movimientos ----------

    suspend fun transaction(id: Long) = db.transactions().get(id)

    suspend fun save(d: TransactionDraft): Long {
        val account = db.accounts().get(d.accountId) ?: error("Cuenta no existe")
        val previous = if (d.id != 0L) db.transactions().get(d.id) else null
        val chargesCard = account.type.isCard && (d.type == TxType.EXPENSE || d.type == TxType.TRANSFER)
        val count = d.installments.coerceAtLeast(1)
        val paid = d.installmentsPaid.coerceIn(0, count - 1)
        val first: YearMonth? = when {
            !chargesCard -> null
            paid > 0 -> {
                // Compra antigua: se ancla al mes en que se registró. Al editarla se conserva el ancla
                // (si no cambió el número de cuotas pagadas), así las cuotas no se corren de mes.
                val keep = previous?.takeIf { it.accountId == d.accountId && it.installmentsPaid == paid && it.firstPeriod != 0 }
                keep?.firstPeriod?.toYearMonth() ?: account.cycle.openPeriod(LocalDate.now()).minusMonths(paid.toLong())
            }
            else -> account.cycle.periodFor(d.date).plusMonths(d.deferMonths.toLong())
        }
        val (mode, value) = when (val i = d.interest) {
            InterestSpec.None -> "NONE" to 0.0
            is InterestSpec.FixedInstallment -> "INSTALLMENT" to i.installmentAmount.toDouble()
            is InterestSpec.Total -> "TOTAL" to i.totalAmount.toDouble()
            is InterestSpec.MonthlyRate -> "RATE" to i.rate
        }
        val entity = TransactionEntity(
            id = d.id,
            type = d.type,
            accountId = d.accountId,
            toAccountId = d.toAccountId,
            amount = d.amount,
            toAmount = d.toAmount,
            epochDay = d.date.toEpochDay(),
            description = d.description.trim(),
            categoryId = d.categoryId,
            installmentCount = d.installments.coerceAtLeast(1),
            installmentsPaid = d.installmentsPaid,
            interestMode = mode,
            interestValue = value,
            deferMonths = d.deferMonths,
            source = d.source,
            firstPeriod = first?.toPeriod() ?: 0,
        )
        val id = if (d.id == 0L) db.transactions().insert(entity) else { db.transactions().upsert(entity); d.id }
        db.installments().deleteForTransaction(id)

        if (first != null) {
            val plan = InstallmentPlanner.plan(d.amount, count, first, d.interest).filter { it.number > paid }
            db.installments().insertAll(plan.map {
                InstallmentEntity(transactionId = id, accountId = account.id, number = it.number, period = it.period.toPeriod(), amount = it.amount, principal = it.principal)
            })
        }

        if (d.repeat != null && d.id == 0L && d.type != TxType.ADJUSTMENT) {
            db.recurring().upsert(
                RecurringEntity(
                    type = d.type, accountId = d.accountId, toAccountId = d.toAccountId, amount = d.amount,
                    description = d.description.trim(), categoryId = d.categoryId, frequency = d.repeat,
                    dayOfMonth = d.date.dayOfMonth, nextEpochDay = Recurrence.next(d.date, d.repeat, d.date.dayOfMonth).toEpochDay(),
                )
            )
        }

        // Aprendizaje: comercio → categoría.
        if (d.categoryId != null && d.description.isNotBlank()) {
            db.rules().putMerchantRule(MerchantRuleEntity(merchantKey(d.description), d.categoryId))
        }
        d.detectionId?.let { detId ->
            db.detections().get(detId)?.let { det ->
                db.rules().putSourceRule(SourceRuleEntity(packageName = det.packageName, lastDigits = det.lastDigits.orEmpty(), accountId = d.accountId))
            }
            db.detections().setStatus(detId, DetectionStatus.CONFIRMED)
        }
        onDataChanged()
        return id
    }

    suspend fun deleteTransaction(id: Long) {
        db.transactions().delete(id)
        onDataChanged()
    }

    suspend fun suggestCategory(description: String): Long? =
        description.takeIf { it.isNotBlank() }?.let { db.rules().merchantRule(merchantKey(it))?.categoryId }

    private fun merchantKey(s: String) = s.lowercase().replace(Regex("[^a-z0-9áéíóúñ]"), "").take(24)

    // ---------- Detecciones (notificaciones) ----------

    suspend fun detection(id: Long) = db.detections().get(id)

    /** Inserta una detección si no es duplicada. Devuelve el id o null. */
    suspend fun addDetection(d: DetectionEntity): Long? {
        val window = 3 * 60_000L
        if (db.detections().countSimilar(d.amount, d.currencyCode, d.isIncome, d.postedAt - window, d.postedAt + window) > 0) return null
        db.detections().purgeOld(System.currentTimeMillis() - 60L * 24 * 3600_000)
        return db.detections().insert(d)
    }

    suspend fun dismissDetection(id: Long) = db.detections().setStatus(id, DetectionStatus.DISMISSED)

    /** Cuenta sugerida para una notificación según lo aprendido o los últimos dígitos. */
    suspend fun suggestAccount(packageName: String, lastDigits: String?): Long? {
        val accounts = db.accounts().getAll().filter { !it.archived }
        if (lastDigits != null) accounts.firstOrNull { it.lastDigits == lastDigits }?.let { return it.id }
        val rules = db.rules().sourceRules().filter { it.packageName == packageName }
        val rule = rules.firstOrNull { it.lastDigits == lastDigits.orEmpty() } ?: rules.firstOrNull()
        return rule?.accountId?.takeIf { id -> accounts.any { it.id == id } }
    }

    // ---------- Recurrentes ----------

    suspend fun recurring(id: Long) = db.recurring().get(id)
    suspend fun saveRecurring(r: RecurringEntity) { db.recurring().upsert(r) }
    suspend fun deleteRecurring(id: Long) { db.recurring().delete(id) }

    /**
     * Procesa los recurrentes vencidos: los automáticos se registran; el resto queda en la bandeja
     * de pendientes. Devuelve los pendientes creados (para notificar).
     */
    suspend fun processRecurring(today: LocalDate = LocalDate.now()): List<DetectionEntity> {
        val created = mutableListOf<DetectionEntity>()
        val accounts = db.accounts().getAll().associateBy { it.id }
        for (r in db.recurring().due(today.toEpochDay())) {
            val account = accounts[r.accountId]
            if (account == null || account.archived) continue
            val dates = Recurrence.pending(LocalDate.ofEpochDay(r.nextEpochDay), today, r.frequency, r.dayOfMonth)
            for (date in dates) {
                if (r.autoCreate) {
                    save(
                        TransactionDraft(
                            type = r.type, accountId = r.accountId, toAccountId = r.toAccountId, amount = r.amount,
                            date = date, description = r.description, categoryId = r.categoryId, source = TxSource.AUTO,
                        )
                    )
                } else {
                    val d = DetectionEntity(
                        packageName = "recurring", appLabel = "Programado", title = r.description,
                        text = "${r.frequency.label} · ${account.name}", amount = r.amount, currencyCode = account.currencyCode,
                        merchant = r.description.ifBlank { null }, isIncome = r.type == TxType.INCOME, lastDigits = null,
                        postedAt = date.atTime(9, 0).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        recurringId = r.id,
                    )
                    created += d.copy(id = db.detections().insert(d))
                }
            }
            val next = dates.lastOrNull()?.let { Recurrence.next(it, r.frequency, r.dayOfMonth) } ?: continue
            db.recurring().upsert(r.copy(nextEpochDay = next.toEpochDay()))
        }
        if (created.isNotEmpty()) onDataChanged()
        return created
    }

    // ---------- Rentabilidad diaria ----------

    suspend fun accrueYield(today: LocalDate = LocalDate.now()) {
        val upTo = today.minusDays(1)
        val accounts = db.accounts().getAll().filter { it.type == AccountType.YIELD && it.annualRate > 0 && !it.archived }
        if (accounts.isEmpty()) return
        val rentCategory = yieldCategoryId()
        val txs = db.transactions().getAll()
        var changed = false
        for (acc in accounts) {
            val last = if (acc.lastAccrualEpochDay == 0L) upTo else LocalDate.ofEpochDay(acc.lastAccrualEpochDay)
            if (!upTo.isAfter(last)) continue
            // Saldo al cierre de `last` y movimientos de cada día posterior: el interés de días atrasados
            // se calcula con el saldo que realmente había ese día.
            val flows = HashMap<Long, Long>()
            var current = acc.initialBalance
            for (t in txs) {
                val e = Balances.effectOn(acc.id, t)
                if (e == 0L) continue
                current += e
                if (t.epochDay > last.toEpochDay()) flows.merge(t.epochDay, e, Long::plus)
            }
            val endOfLast = current - flows.values.sum()
            val perMonth = YieldAccrual.accrueWithFlows(endOfLast, acc.annualRate, last, upTo, flows)
            for ((ym, amount) in perMonth) {
                val start = ym.atDay(1).toEpochDay()
                val end = ym.atEndOfMonth().toEpochDay()
                val day = minOf(end, upTo.toEpochDay())
                val existing = db.transactions().findInterest(acc.id, start, end)
                if (existing != null) {
                    db.transactions().upsert(existing.copy(amount = existing.amount + amount, epochDay = day))
                } else {
                    val monthName = ym.month.getDisplayName(TextStyle.FULL, Locale("es", "CL"))
                    db.transactions().insert(
                        TransactionEntity(
                            type = TxType.INTEREST, accountId = acc.id, amount = amount, epochDay = day,
                            description = "Rentabilidad $monthName", categoryId = rentCategory, source = TxSource.AUTO,
                        )
                    )
                }
            }
            db.accounts().upsert(acc.copy(lastAccrualEpochDay = upTo.toEpochDay()))
            changed = true
        }
        if (changed) onDataChanged()
    }

    private suspend fun yieldCategoryId(): Long =
        db.categories().bySystemKey(SystemCategory.YIELD)?.id
            ?: db.categories().upsert(
                CategoryEntity(name = "Rentabilidad", emoji = "📈", color = 0xFF55EFC4, isIncome = true, sortOrder = 3, systemKey = SystemCategory.YIELD)
            )
}

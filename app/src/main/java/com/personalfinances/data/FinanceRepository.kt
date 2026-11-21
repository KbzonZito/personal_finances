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
        raw, settingsStore.settings, db.detections().observePending(),
    ) { r, s, p -> FinanceSnapshot.build(s, r.accounts, r.categories, r.txs, r.installments, p) }
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
        db.transactions().deleteForAccount(account.id)
        db.accounts().delete(account)
        onDataChanged()
    }

    suspend fun saveCategory(category: CategoryEntity) = db.categories().upsert(category)

    // ---------- Movimientos ----------

    suspend fun transaction(id: Long) = db.transactions().get(id)

    suspend fun save(d: TransactionDraft): Long {
        val account = db.accounts().get(d.accountId) ?: error("Cuenta no existe")
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
        )
        val id = if (d.id == 0L) db.transactions().insert(entity) else { db.transactions().upsert(entity); d.id }
        db.installments().deleteForTransaction(id)

        val chargesCard = account.type.isCard && (d.type == TxType.EXPENSE || d.type == TxType.TRANSFER)
        if (chargesCard) {
            val count = entity.installmentCount
            val paid = d.installmentsPaid.coerceIn(0, count - 1)
            val first: YearMonth = if (paid > 0) {
                account.cycle.openPeriod(LocalDate.now()).minusMonths(paid.toLong())
            } else {
                account.cycle.periodFor(d.date).plusMonths(d.deferMonths.toLong())
            }
            val plan = InstallmentPlanner.plan(d.amount, count, first, d.interest).filter { it.number > paid }
            db.installments().insertAll(plan.map {
                InstallmentEntity(transactionId = id, accountId = account.id, number = it.number, period = it.period.toPeriod(), amount = it.amount, principal = it.principal)
            })
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
        val since = d.postedAt - 3 * 60_000
        if (db.detections().countSimilar(d.packageName, d.amount, since) > 0) return null
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

    // ---------- Rentabilidad diaria ----------

    suspend fun accrueYield(today: LocalDate = LocalDate.now()) {
        val upTo = today.minusDays(1)
        val accounts = db.accounts().getAll().filter { it.type == AccountType.YIELD && it.annualRate > 0 && !it.archived }
        if (accounts.isEmpty()) return
        val rentCategory = db.categories().getAll().firstOrNull { it.isIncome && it.name == "Rentabilidad" }?.id
        var changed = false
        for (acc in accounts) {
            val last = if (acc.lastAccrualEpochDay == 0L) upTo else LocalDate.ofEpochDay(acc.lastAccrualEpochDay)
            if (!upTo.isAfter(last)) continue
            val txs = db.transactions().getAll()
            val balance = Balances.compute(listOf(acc), txs, emptyList(), today).balances[acc.id] ?: 0
            val perMonth = YieldAccrual.accrue(balance, acc.annualRate, last, upTo)
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
}

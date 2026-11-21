package com.personalfinances.data

import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.CategoryEntity
import com.personalfinances.data.db.DetectionEntity
import com.personalfinances.data.db.InstallmentEntity
import com.personalfinances.data.db.RecurringEntity
import com.personalfinances.data.db.TransactionEntity
import com.personalfinances.data.db.TxType
import com.personalfinances.data.db.toYearMonth
import com.personalfinances.data.prefs.Settings
import com.personalfinances.domain.CardCalculator
import com.personalfinances.domain.CardSummary
import com.personalfinances.domain.InstallmentLine
import com.personalfinances.domain.YieldAccrual
import java.time.LocalDate
import java.time.YearMonth

data class AccountState(
    val account: AccountEntity,
    /** Saldo en la moneda de la cuenta. Para tarjetas: -(cupo utilizado). */
    val balance: Long,
    val card: CardSummary? = null,
    /** Ganancia estimada en 30 días (cuentas remuneradas). */
    val monthlyYield: Long = 0,
)

data class TxItem(
    val tx: TransactionEntity,
    val account: AccountEntity?,
    val toAccount: AccountEntity?,
    val category: CategoryEntity?,
    /** Cuotas ya facturadas / total (solo compras en cuotas). */
    val billedInstallments: Int = 0,
)

data class ActivePurchase(
    val item: TxItem,
    val total: Int,
    val billed: Int,
    val nextAmount: Long,
    val remainingAmount: Long,
)

data class FinanceSnapshot(
    val settings: Settings = Settings(),
    val today: LocalDate = LocalDate.now(),
    val accounts: List<AccountState> = emptyList(),
    val categories: List<CategoryEntity> = emptyList(),
    val transactions: List<TxItem> = emptyList(),
    val installments: List<InstallmentEntity> = emptyList(),
    val pending: List<DetectionEntity> = emptyList(),
    val netWorth: Long = 0,
    val monthExpense: Long = 0,
    val monthIncome: Long = 0,
    val expenseByCategory: List<Pair<CategoryEntity?, Long>> = emptyList(),
    val recurring: List<RecurringEntity> = emptyList(),
    val loaded: Boolean = false,
) {
    val activeAccounts get() = accounts.filter { !it.account.archived }
    val cards get() = activeAccounts.filter { it.account.type.isCard }

    fun account(id: Long?) = accounts.firstOrNull { it.account.id == id }

    fun activePurchases(cardId: Long): List<ActivePurchase> {
        val card = account(cardId)?.account ?: return emptyList()
        val open = card.cycle.openPeriod(today)
        val byTx = installments.filter { it.accountId == cardId }.groupBy { it.transactionId }
        return transactions.filter { it.tx.accountId == cardId && it.tx.installmentCount > 1 }.mapNotNull { item ->
            val lines = byTx[item.tx.id].orEmpty().sortedBy { it.number }
            val remaining = lines.filter { it.period.toYearMonth() >= open }
            if (remaining.isEmpty()) return@mapNotNull null
            ActivePurchase(
                item = item,
                total = item.tx.installmentCount,
                billed = item.tx.installmentCount - remaining.size,
                nextAmount = remaining.first().amount,
                remainingAmount = remaining.sumOf { it.amount },
            )
        }
    }

    companion object {
        fun build(
            settings: Settings,
            accounts: List<AccountEntity>,
            categories: List<CategoryEntity>,
            txs: List<TransactionEntity>,
            installments: List<InstallmentEntity>,
            pending: List<DetectionEntity>,
            recurring: List<RecurringEntity> = emptyList(),
            today: LocalDate = LocalDate.now(),
        ): FinanceSnapshot {
            val accById = accounts.associateBy { it.id }
            val catById = categories.associateBy { it.id }
            val balances = Balances.compute(accounts, txs, installments, today)
            val rates = settings.rates
            val main = settings.mainCurrency

            val states = accounts.map { a ->
                val card = if (a.type.isCard) balances.cards[a.id] else null
                val bal = if (card != null) -card.used else balances.balances[a.id] ?: 0
                AccountState(
                    account = a,
                    balance = bal,
                    card = card,
                    monthlyYield = if (a.annualRate > 0) YieldAccrual.project(bal, a.annualRate, 30) else 0,
                )
            }
            val netWorth = states.filter { !it.account.archived && it.account.includeInTotal }
                .sumOf { rates.convert(it.balance, it.account.currency, main) }

            val month = YearMonth.from(today)
            val instByTx = installments.groupBy { it.transactionId }
            val items = txs.map { tx ->
                val billed = if (tx.installmentCount > 1) {
                    val acc = accById[tx.accountId]
                    val closed = acc?.cycle?.lastClosedPeriod(today)
                    instByTx[tx.id].orEmpty().count { closed != null && it.period.toYearMonth() <= closed }
                } else 0
                TxItem(tx, accById[tx.accountId], tx.toAccountId?.let { accById[it] }, tx.categoryId?.let { catById[it] }, billed)
            }
            val monthTx = items.filter { YearMonth.from(LocalDate.ofEpochDay(it.tx.epochDay)) == month }
            fun inMain(i: TxItem) = rates.convert(i.tx.amount, i.account?.currency ?: main, main)
            val expenses = monthTx.filter { it.tx.type == TxType.EXPENSE }
            val incomes = monthTx.filter { it.tx.type == TxType.INCOME || it.tx.type == TxType.INTEREST }
            val byCat = expenses.groupBy { it.category }.map { (c, l) -> c to l.sumOf(::inMain) }.sortedByDescending { it.second }

            return FinanceSnapshot(
                settings = settings,
                today = today,
                accounts = states,
                categories = categories,
                transactions = items,
                installments = installments,
                pending = pending,
                netWorth = netWorth,
                monthExpense = expenses.sumOf(::inMain),
                monthIncome = incomes.sumOf(::inMain),
                expenseByCategory = byCat,
                recurring = recurring,
                loaded = true,
            )
        }
    }
}

object Balances {
    data class Result(val balances: Map<Long, Long>, val cards: Map<Long, CardSummary>)

    private fun anchorDay(a: AccountEntity): Long =
        if (a.balanceAnchorAt == 0L) Long.MIN_VALUE
        else java.time.Instant.ofEpochMilli(a.balanceAnchorAt).atZone(java.time.ZoneId.systemDefault()).toLocalDate().toEpochDay()

    /**
     * ¿El movimiento cambia el saldo de [a]? Los anteriores al momento en que se declaró el saldo
     * son historial (ya están incluidos en ese saldo).
     */
    fun counts(a: AccountEntity?, t: TransactionEntity, anchorDay: Long = a?.let(::anchorDay) ?: Long.MIN_VALUE): Boolean {
        if (a == null || a.balanceAnchorAt == 0L) return true
        return t.epochDay > anchorDay || (t.epochDay == anchorDay && t.createdAt > a.balanceAnchorAt)
    }

    /** Efecto neto de un movimiento sobre el saldo de [account] (cuentas que no son tarjeta). */
    fun effectOn(account: AccountEntity, t: TransactionEntity): Long {
        if (!counts(account, t)) return 0
        var e = 0L
        if (t.accountId == account.id) e += when (t.type) {
            TxType.EXPENSE, TxType.TRANSFER -> -t.amount
            TxType.INCOME, TxType.INTEREST, TxType.ADJUSTMENT -> t.amount
        }
        if (t.type == TxType.TRANSFER && t.toAccountId == account.id) e += t.toAmount ?: t.amount
        return e
    }

    fun compute(accounts: List<AccountEntity>, txs: List<TransactionEntity>, installments: List<InstallmentEntity>, today: LocalDate): Result {
        val bal = HashMap<Long, Long>()
        val credits = HashMap<Long, Long>()
        accounts.forEach { bal[it.id] = it.initialBalance }
        val byId = accounts.associateBy { it.id }
        val anchors = accounts.associate { it.id to anchorDay(it) }
        val cardIds = accounts.filter { it.type.isCard }.map { it.id }.toSet()
        fun ok(id: Long, t: TransactionEntity) = counts(byId[id], t, anchors[id] ?: Long.MIN_VALUE)

        for (t in txs) {
            when (t.type) {
                TxType.EXPENSE -> if (ok(t.accountId, t)) bal.merge(t.accountId, -t.amount, Long::plus)
                TxType.INCOME, TxType.INTEREST, TxType.ADJUSTMENT -> if (ok(t.accountId, t)) {
                    bal.merge(t.accountId, t.amount, Long::plus)
                    if (t.accountId in cardIds) credits.merge(t.accountId, t.amount, Long::plus)
                }
                TxType.TRANSFER -> {
                    if (ok(t.accountId, t)) bal.merge(t.accountId, -t.amount, Long::plus)
                    t.toAccountId?.let { to ->
                        if (ok(to, t)) {
                            val received = t.toAmount ?: t.amount
                            bal.merge(to, received, Long::plus)
                            if (to in cardIds) credits.merge(to, received, Long::plus)
                        }
                    }
                }
            }
        }

        // Tarjetas: las cuotas cuentan aunque la compra sea antigua (son deuda real), salvo las de
        // estados de cuenta ya pagados.
        val instByAcc = installments.groupBy { it.accountId }
        val cards = accounts.filter { it.type.isCard }.associate { a ->
            val lines = instByAcc[a.id].orEmpty()
                .filter { a.paidThroughPeriod == 0 || it.period > a.paidThroughPeriod }
                .map { InstallmentLine(it.period.toYearMonth(), it.amount, it.principal) }
            a.id to CardCalculator.summarize(a.cycle, a.creditLimit, lines, credits[a.id] ?: 0, today)
        }
        return Result(bal, cards)
    }
}

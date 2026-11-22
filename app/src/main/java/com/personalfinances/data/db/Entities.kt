package com.personalfinances.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.personalfinances.domain.CardCycle
import com.personalfinances.domain.Currency
import com.personalfinances.domain.Frequency
import kotlinx.serialization.Serializable

@Serializable
enum class AccountType(val label: String, val emoji: String) {
    CHECKING("Cuenta corriente", "🏦"),
    DEBIT("Cuenta vista / débito", "💳"),
    CASH("Efectivo", "💵"),
    SAVINGS("Ahorro", "🐷"),
    YIELD("Cuenta remunerada", "📈"),
    CREDIT_CARD("Tarjeta de crédito", "💳"),
    INVESTMENT("Inversión", "📊"),
    LOAN("Crédito / préstamo", "🏛️");

    val isCard get() = this == CREDIT_CARD
    val isLoan get() = this == LOAN
    /** Cuentas con dinero disponible (no tarjetas ni créditos). */
    val isLiquid get() = this != CREDIT_CARD && this != LOAN
}

@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    val currencyCode: String = Currency.CLP.code,
    val initialBalance: Long = 0,
    val color: Long = 0xFF6C5CE7,
    val creditLimit: Long = 0,
    val closingDay: Int = 20,
    val dueDay: Int = 5,
    /** Tasa anual en %, solo para cuentas remuneradas. */
    val annualRate: Double = 0.0,
    val lastAccrualEpochDay: Long = 0,
    /** Últimos 4 dígitos (uno o varios separados por coma: tarjeta física, virtual, débito…), para asociar notificaciones. */
    val lastDigits: String? = null,
    val archived: Boolean = false,
    val includeInTotal: Boolean = true,
    val sortOrder: Int = 0,
    /**
     * Momento en que el usuario declaró el saldo ([initialBalance] = saldo en ese momento).
     * Los movimientos con fecha anterior son historial: ya están reflejados y no cambian el saldo.
     * 0 = cuentas antiguas: [initialBalance] es el saldo antes de todos los movimientos.
     */
    @ColumnInfo(defaultValue = "0") val balanceAnchorAt: Long = 0,
    /** Tarjetas: estados de cuenta hasta este yyyymm se consideran pagados (0 = ninguno). */
    @ColumnInfo(defaultValue = "0") val paidThroughPeriod: Int = 0,
    /** Créditos: valor de la cuota (en la moneda de la cuenta, ej. UF). */
    @ColumnInfo(defaultValue = "0") val loanInstallment: Long = 0,
    @ColumnInfo(defaultValue = "0") val loanTotalInstallments: Int = 0,
    /** Cuotas ya pagadas al crear la cuenta; las siguientes se cuentan con cada pago (transferencia) registrado. */
    @ColumnInfo(defaultValue = "0") val loanPaidInstallments: Int = 0,
) {
    val currency: Currency get() = Currency.fromCode(currencyCode)
    val cycle: CardCycle get() = CardCycle(closingDay, dueDay)
    val digitsList: List<String> get() = lastDigits.orEmpty().split(Regex("[^0-9]+")).filter { it.length == 4 }
}

@Serializable
enum class TxType { EXPENSE, INCOME, TRANSFER, INTEREST, ADJUSTMENT }

@Serializable
enum class TxSource { MANUAL, NOTIFICATION, AUTO }

@Serializable
@Entity(
    tableName = "transactions",
    indices = [Index("accountId"), Index("toAccountId"), Index("epochDay")],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val accountId: Long,
    val toAccountId: Long? = null,
    /** Monto en la moneda de [accountId]. Para ADJUSTMENT puede ser negativo. */
    val amount: Long,
    /** Monto recibido en la moneda de [toAccountId] (transferencias entre monedas). */
    val toAmount: Long? = null,
    val epochDay: Long,
    val createdAt: Long = System.currentTimeMillis(),
    val description: String = "",
    val categoryId: Long? = null,
    val installmentCount: Int = 1,
    /** Cuotas ya pagadas al momento de registrar (compras antiguas). */
    val installmentsPaid: Int = 0,
    /** NONE, INSTALLMENT, TOTAL, RATE */
    val interestMode: String = "NONE",
    val interestValue: Double = 0.0,
    /** Meses de diferimiento de la primera cuota (0 = próximo estado de cuenta). */
    val deferMonths: Int = 0,
    val source: TxSource = TxSource.MANUAL,
    /** yyyymm del estado de cuenta de la primera cuota (0 = no aplica). Fijo al editar. */
    @ColumnInfo(defaultValue = "0") val firstPeriod: Int = 0,
    /** Monto en la moneda original de la compra (ej. US$ en una tarjeta en pesos). */
    val originalAmount: Long? = null,
    val originalCurrency: String? = null,
)

@Serializable
@Entity(
    tableName = "installments",
    foreignKeys = [ForeignKey(
        entity = TransactionEntity::class,
        parentColumns = ["id"],
        childColumns = ["transactionId"],
        onDelete = ForeignKey.CASCADE,
    )],
    indices = [Index("transactionId"), Index("accountId"), Index("period")],
)
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val transactionId: Long,
    val accountId: Long,
    val number: Int,
    /** yyyymm del estado de cuenta. */
    val period: Int,
    val amount: Long,
    val principal: Long,
)

@Serializable
@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String,
    val color: Long,
    val isIncome: Boolean = false,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    /** Identificador estable para categorías que usa la app (ej. "yield"), independiente del nombre. */
    val systemKey: String? = null,
)

object SystemCategory {
    const val YIELD = "yield"
}

@Serializable
enum class DetectionStatus { PENDING, CONFIRMED, DISMISSED }

@Serializable
@Entity(tableName = "detections", indices = [Index("status")])
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appLabel: String,
    val title: String,
    val text: String,
    val amount: Long,
    val currencyCode: String,
    val merchant: String?,
    val isIncome: Boolean,
    val lastDigits: String?,
    val postedAt: Long,
    val status: DetectionStatus = DetectionStatus.PENDING,
    /** Si viene de un movimiento recurrente por confirmar (no de una notificación). */
    val recurringId: Long? = null,
    /** CREDIT / DEBIT si la notificación lo indicaba. */
    val cardKind: String? = null,
) {
    val currency: Currency get() = Currency.fromCode(currencyCode)
}

/** Lo que la app aprendió: notificaciones de este paquete/tarjeta van a esta cuenta. */
@Serializable
@Entity(tableName = "source_rules", indices = [Index(value = ["packageName", "lastDigits"], unique = true)])
data class SourceRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val lastDigits: String = "",
    val accountId: Long,
)

/** Recuerda la última categoría usada para un comercio. */
@Serializable
@Entity(tableName = "merchant_rules")
data class MerchantRuleEntity(
    @PrimaryKey val merchantKey: String,
    val categoryId: Long,
)

/** Plantilla de un movimiento que se repite (sueldo, arriendo, suscripciones…). */
@Serializable
@Entity(tableName = "recurring")
data class RecurringEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: TxType,
    val accountId: Long,
    val toAccountId: Long? = null,
    val amount: Long,
    val description: String = "",
    val categoryId: Long? = null,
    val frequency: Frequency = Frequency.MONTHLY,
    /** Día del mes (1–31, se ajusta en meses cortos) para MONTHLY/YEARLY. */
    val dayOfMonth: Int = 1,
    /** Próxima fecha en que corresponde (epochDay). */
    val nextEpochDay: Long,
    /** true: se registra solo. false: queda pendiente y te pide confirmar. */
    val autoCreate: Boolean = false,
    val active: Boolean = true,
)

/** Presupuesto mensual por categoría, en la moneda principal. categoryId = 0 → presupuesto total. */
@Serializable
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val categoryId: Long,
    val amount: Long,
)

/** Meta de ahorro. Si tiene cuenta asociada, el avance es el saldo de esa cuenta; si no, lo abonado a mano. */
@Serializable
@Entity(tableName = "goals")
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "🎯",
    val target: Long,
    val currencyCode: String = Currency.CLP.code,
    val accountId: Long? = null,
    val manualSaved: Long = 0,
    val deadlineEpochDay: Long? = null,
    val color: Long = 0xFF00B894,
    val createdAt: Long = System.currentTimeMillis(),
) {
    val currency: Currency get() = Currency.fromCode(currencyCode)
}

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
    INVESTMENT("Inversión", "📊");

    val isCard get() = this == CREDIT_CARD
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
    /** Últimos 4 dígitos, para asociar notificaciones. */
    val lastDigits: String? = null,
    val archived: Boolean = false,
    val includeInTotal: Boolean = true,
    val sortOrder: Int = 0,
) {
    val currency: Currency get() = Currency.fromCode(currencyCode)
    val cycle: CardCycle get() = CardCycle(closingDay, dueDay)
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

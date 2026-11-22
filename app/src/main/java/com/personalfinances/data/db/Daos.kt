package com.personalfinances.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts ORDER BY archived, sortOrder, id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY sortOrder, id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun get(id: Long): AccountEntity?

    @Upsert suspend fun upsert(account: AccountEntity): Long
    @Delete suspend fun delete(account: AccountEntity)
}

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY epochDay DESC, createdAt DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun get(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE accountId = :accountId AND type = 'INTEREST' AND epochDay BETWEEN :fromDay AND :toDay LIMIT 1")
    suspend fun findInterest(accountId: Long, fromDay: Long, toDay: Long): TransactionEntity?

    @Insert suspend fun insert(tx: TransactionEntity): Long
    @Upsert suspend fun upsert(tx: TransactionEntity): Long

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun delete(id: Long)

    /** Transferencias desde otra cuenta hacia la eliminada → gasto en la cuenta de origen. */
    @Query("UPDATE transactions SET type = 'EXPENSE', toAccountId = NULL, toAmount = NULL, description = CASE WHEN description = '' THEN :label ELSE description END WHERE type = 'TRANSFER' AND toAccountId = :accountId AND accountId != :accountId")
    suspend fun convertIncomingTransfers(accountId: Long, label: String)

    /** Transferencias desde la eliminada hacia otra cuenta → ingreso en la cuenta de destino. */
    @Query("UPDATE transactions SET type = 'INCOME', accountId = toAccountId, amount = COALESCE(toAmount, amount), toAccountId = NULL, toAmount = NULL, installmentCount = 1, description = CASE WHEN description = '' THEN :label ELSE description END WHERE type = 'TRANSFER' AND accountId = :accountId AND toAccountId IS NOT NULL AND toAccountId != :accountId")
    suspend fun convertOutgoingTransfers(accountId: Long, label: String)

    @Query("DELETE FROM transactions WHERE accountId = :accountId")
    suspend fun deleteOwned(accountId: Long)
}

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installments")
    fun observeAll(): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments")
    suspend fun getAll(): List<InstallmentEntity>

    @Insert suspend fun insertAll(items: List<InstallmentEntity>)

    @Query("SELECT * FROM installments WHERE transactionId = :txId")
    suspend fun forTransaction(txId: Long): List<InstallmentEntity>

    @Query("DELETE FROM installments WHERE transactionId = :txId")
    suspend fun deleteForTransaction(txId: Long)

    @Query("DELETE FROM installments WHERE accountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY isIncome, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

    @Query("SELECT * FROM categories WHERE systemKey = :key LIMIT 1")
    suspend fun bySystemKey(key: String): CategoryEntity?

    @Upsert suspend fun upsert(c: CategoryEntity): Long
    @Insert suspend fun insertAll(c: List<CategoryEntity>)
}

@Dao
interface DetectionDao {
    @Query("SELECT * FROM detections WHERE status = 'PENDING' ORDER BY postedAt DESC")
    fun observePending(): Flow<List<DetectionEntity>>

    @Query("SELECT * FROM detections")
    suspend fun getAll(): List<DetectionEntity>

    @Query("SELECT * FROM detections WHERE id = :id")
    suspend fun get(id: Long): DetectionEntity?

    /** Misma compra avisada por varias apps (ej. banco + Google Wallet) o notificación repetida. */
    @Query("SELECT COUNT(*) FROM detections WHERE amount = :amount AND currencyCode = :currency AND isIncome = :isIncome AND postedAt BETWEEN :from AND :to")
    suspend fun countSimilar(amount: Long, currency: String, isIncome: Boolean, from: Long, to: Long): Int

    @Insert suspend fun insert(d: DetectionEntity): Long

    @Query("UPDATE detections SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: DetectionStatus)

    @Query("DELETE FROM detections WHERE status != 'PENDING' AND postedAt < :before")
    suspend fun purgeOld(before: Long)
}

@Dao
interface RuleDao {
    @Query("SELECT * FROM source_rules")
    suspend fun sourceRules(): List<SourceRuleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putSourceRule(rule: SourceRuleEntity)

    @Query("DELETE FROM source_rules WHERE accountId = :accountId")
    suspend fun deleteSourceRulesFor(accountId: Long)

    @Query("SELECT * FROM merchant_rules")
    suspend fun merchantRules(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key")
    suspend fun merchantRule(key: String): MerchantRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMerchantRule(rule: MerchantRuleEntity)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring ORDER BY active DESC, nextEpochDay")
    fun observeAll(): Flow<List<RecurringEntity>>

    @Query("SELECT * FROM recurring")
    suspend fun getAll(): List<RecurringEntity>

    @Query("SELECT * FROM recurring WHERE id = :id")
    suspend fun get(id: Long): RecurringEntity?

    @Query("SELECT * FROM recurring WHERE active = 1 AND nextEpochDay <= :day")
    suspend fun due(day: Long): List<RecurringEntity>

    @Upsert suspend fun upsert(r: RecurringEntity): Long

    @Query("DELETE FROM recurring WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM recurring WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>

    @Upsert suspend fun upsert(b: BudgetEntity)

    @Query("DELETE FROM budgets WHERE categoryId = :categoryId")
    suspend fun delete(categoryId: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY createdAt")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals")
    suspend fun getAll(): List<GoalEntity>

    @Upsert suspend fun upsert(g: GoalEntity): Long

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("UPDATE goals SET accountId = NULL WHERE accountId = :accountId")
    suspend fun unlinkAccount(accountId: Long)
}

@Dao
interface MaintenanceDao {
    @Query("DELETE FROM accounts") suspend fun clearAccounts()
    @Query("DELETE FROM transactions") suspend fun clearTransactions()
    @Query("DELETE FROM installments") suspend fun clearInstallments()
    @Query("DELETE FROM categories") suspend fun clearCategories()
    @Query("DELETE FROM detections") suspend fun clearDetections()
    @Query("DELETE FROM source_rules") suspend fun clearSourceRules()
    @Query("DELETE FROM merchant_rules") suspend fun clearMerchantRules()
    @Query("DELETE FROM recurring") suspend fun clearRecurring()
    @Query("DELETE FROM budgets") suspend fun clearBudgets()
    @Query("DELETE FROM goals") suspend fun clearGoals()
    @Insert suspend fun insertGoals(v: List<GoalEntity>)
    @Insert suspend fun insertBudgets(v: List<BudgetEntity>)

    @Insert suspend fun insertAccounts(v: List<AccountEntity>)
    @Insert suspend fun insertTransactions(v: List<TransactionEntity>)
    @Insert suspend fun insertInstallments(v: List<InstallmentEntity>)
    @Insert suspend fun insertCategories(v: List<CategoryEntity>)
    @Insert suspend fun insertDetections(v: List<DetectionEntity>)
    @Insert suspend fun insertSourceRules(v: List<SourceRuleEntity>)
    @Insert suspend fun insertMerchantRules(v: List<MerchantRuleEntity>)
    @Insert suspend fun insertRecurring(v: List<RecurringEntity>)

    @Transaction
    suspend fun replaceAll(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        installments: List<InstallmentEntity>,
        categories: List<CategoryEntity>,
        detections: List<DetectionEntity>,
        sourceRules: List<SourceRuleEntity>,
        merchantRules: List<MerchantRuleEntity>,
        recurring: List<RecurringEntity>,
        budgets: List<BudgetEntity>,
        goals: List<GoalEntity>,
    ) {
        clearInstallments(); clearTransactions(); clearAccounts(); clearCategories()
        clearDetections(); clearSourceRules(); clearMerchantRules(); clearRecurring()
        insertRecurring(recurring)
        clearBudgets(); insertBudgets(budgets)
        clearGoals(); insertGoals(goals)
        insertAccounts(accounts); insertCategories(categories); insertTransactions(transactions)
        insertInstallments(installments); insertDetections(detections)
        insertSourceRules(sourceRules); insertMerchantRules(merchantRules)
    }
}

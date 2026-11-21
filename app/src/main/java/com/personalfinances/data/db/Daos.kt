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

    @Query("DELETE FROM transactions WHERE accountId = :accountId OR toAccountId = :accountId")
    suspend fun deleteForAccount(accountId: Long)
}

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installments")
    fun observeAll(): Flow<List<InstallmentEntity>>

    @Query("SELECT * FROM installments")
    suspend fun getAll(): List<InstallmentEntity>

    @Insert suspend fun insertAll(items: List<InstallmentEntity>)

    @Query("DELETE FROM installments WHERE transactionId = :txId")
    suspend fun deleteForTransaction(txId: Long)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories WHERE archived = 0 ORDER BY isIncome, sortOrder, id")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT COUNT(*) FROM categories")
    suspend fun count(): Int

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

    @Query("SELECT COUNT(*) FROM detections WHERE packageName = :pkg AND amount = :amount AND postedAt > :since")
    suspend fun countSimilar(pkg: String, amount: Long, since: Long): Int

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

    @Query("SELECT * FROM merchant_rules")
    suspend fun merchantRules(): List<MerchantRuleEntity>

    @Query("SELECT * FROM merchant_rules WHERE merchantKey = :key")
    suspend fun merchantRule(key: String): MerchantRuleEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun putMerchantRule(rule: MerchantRuleEntity)
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

    @Insert suspend fun insertAccounts(v: List<AccountEntity>)
    @Insert suspend fun insertTransactions(v: List<TransactionEntity>)
    @Insert suspend fun insertInstallments(v: List<InstallmentEntity>)
    @Insert suspend fun insertCategories(v: List<CategoryEntity>)
    @Insert suspend fun insertDetections(v: List<DetectionEntity>)
    @Insert suspend fun insertSourceRules(v: List<SourceRuleEntity>)
    @Insert suspend fun insertMerchantRules(v: List<MerchantRuleEntity>)

    @Transaction
    suspend fun replaceAll(
        accounts: List<AccountEntity>,
        transactions: List<TransactionEntity>,
        installments: List<InstallmentEntity>,
        categories: List<CategoryEntity>,
        detections: List<DetectionEntity>,
        sourceRules: List<SourceRuleEntity>,
        merchantRules: List<MerchantRuleEntity>,
    ) {
        clearInstallments(); clearTransactions(); clearAccounts(); clearCategories()
        clearDetections(); clearSourceRules(); clearMerchantRules()
        insertAccounts(accounts); insertCategories(categories); insertTransactions(transactions)
        insertInstallments(installments); insertDetections(detections)
        insertSourceRules(sourceRules); insertMerchantRules(merchantRules)
    }
}

package com.personalfinances.data.backup

import android.content.Context
import android.net.Uri
import com.personalfinances.data.db.AccountEntity
import com.personalfinances.data.db.AppDatabase
import com.personalfinances.data.db.CategoryEntity
import com.personalfinances.data.db.DetectionEntity
import com.personalfinances.data.db.InstallmentEntity
import com.personalfinances.data.db.MerchantRuleEntity
import com.personalfinances.data.db.RecurringEntity
import com.personalfinances.data.db.SourceRuleEntity
import com.personalfinances.data.db.TransactionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class BackupFile(
    val version: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    val accounts: List<AccountEntity>,
    val transactions: List<TransactionEntity>,
    val installments: List<InstallmentEntity>,
    val categories: List<CategoryEntity>,
    val detections: List<DetectionEntity> = emptyList(),
    val sourceRules: List<SourceRuleEntity> = emptyList(),
    val merchantRules: List<MerchantRuleEntity> = emptyList(),
    val recurring: List<RecurringEntity> = emptyList(),
)

class BackupManager(private val context: Context, private val db: AppDatabase) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }

    suspend fun export(uri: Uri): Int = withContext(Dispatchers.IO) {
        val file = BackupFile(
            accounts = db.accounts().getAll(),
            transactions = db.transactions().getAll(),
            installments = db.installments().getAll(),
            categories = db.categories().getAll(),
            detections = db.detections().getAll(),
            sourceRules = db.rules().sourceRules(),
            merchantRules = db.rules().merchantRules(),
            recurring = db.recurring().getAll(),
        )
        context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.encodeToString(BackupFile.serializer(), file).toByteArray()) }
        file.transactions.size
    }

    suspend fun import(uri: Uri): Int = withContext(Dispatchers.IO) {
        val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
        val f = json.decodeFromString(BackupFile.serializer(), text)
        db.maintenance().replaceAll(f.accounts, f.transactions, f.installments, f.categories, f.detections, f.sourceRules, f.merchantRules, f.recurring)
        f.transactions.size
    }
}

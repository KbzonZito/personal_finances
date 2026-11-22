package com.personalfinances.data.db

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.migration.AutoMigrationSpec
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        AccountEntity::class, TransactionEntity::class, InstallmentEntity::class, CategoryEntity::class,
        DetectionEntity::class, SourceRuleEntity::class, MerchantRuleEntity::class, RecurringEntity::class, BudgetEntity::class, GoalEntity::class,
    ],
    version = 7,
    exportSchema = true,
    autoMigrations = [
        AutoMigration(from = 1, to = 2, spec = Migrations.From1To2::class),
        AutoMigration(from = 2, to = 3),
        AutoMigration(from = 3, to = 4),
        AutoMigration(from = 4, to = 5),
        AutoMigration(from = 5, to = 6),
        AutoMigration(from = 6, to = 7),
    ],
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun accounts(): AccountDao
    abstract fun transactions(): TransactionDao
    abstract fun installments(): InstallmentDao
    abstract fun categories(): CategoryDao
    abstract fun detections(): DetectionDao
    abstract fun rules(): RuleDao
    abstract fun recurring(): RecurringDao
    abstract fun budgets(): BudgetDao
    abstract fun goals(): GoalDao
    abstract fun maintenance(): MaintenanceDao

    companion object {
        fun build(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "finances.db").build()
    }
}

object Migrations {
    /** v2: firstPeriod en transacciones y systemKey en categorías. */
    class From1To2 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("UPDATE categories SET systemKey = '${SystemCategory.YIELD}' WHERE isIncome = 1 AND name = 'Rentabilidad'")
            // Fija el primer periodo de las compras en cuotas ya registradas, para que no se corran al editarlas.
            db.execSQL("UPDATE transactions SET firstPeriod = (SELECT MIN(period) FROM installments i WHERE i.transactionId = transactions.id) WHERE id IN (SELECT transactionId FROM installments)")
        }
    }
}

object DefaultCategories {
    fun all(): List<CategoryEntity> = listOf(
        CategoryEntity(name = "Comida", emoji = "🍔", color = 0xFFFF7675, sortOrder = 0),
        CategoryEntity(name = "Supermercado", emoji = "🛒", color = 0xFF00B894, sortOrder = 1),
        CategoryEntity(name = "Transporte", emoji = "🚌", color = 0xFF0984E3, sortOrder = 2),
        CategoryEntity(name = "Hogar", emoji = "🏠", color = 0xFFE17055, sortOrder = 3),
        CategoryEntity(name = "Cuentas", emoji = "💡", color = 0xFFFDCB6E, sortOrder = 4),
        CategoryEntity(name = "Salud", emoji = "💊", color = 0xFF55EFC4, sortOrder = 5),
        CategoryEntity(name = "Entretención", emoji = "🎬", color = 0xFFA29BFE, sortOrder = 6),
        CategoryEntity(name = "Compras", emoji = "🛍️", color = 0xFFFD79A8, sortOrder = 7),
        CategoryEntity(name = "Suscripciones", emoji = "📺", color = 0xFF6C5CE7, sortOrder = 8),
        CategoryEntity(name = "Educación", emoji = "📚", color = 0xFF74B9FF, sortOrder = 9),
        CategoryEntity(name = "Viajes", emoji = "✈️", color = 0xFF00CEC9, sortOrder = 10),
        CategoryEntity(name = "Mascotas", emoji = "🐾", color = 0xFFB2BEC3, sortOrder = 11),
        CategoryEntity(name = "Otros", emoji = "📦", color = 0xFF636E72, sortOrder = 12),
        CategoryEntity(name = "Sueldo", emoji = "💼", color = 0xFF00B894, isIncome = true, sortOrder = 0),
        CategoryEntity(name = "Ingreso extra", emoji = "💰", color = 0xFFFDCB6E, isIncome = true, sortOrder = 1),
        CategoryEntity(name = "Reembolso", emoji = "↩️", color = 0xFF74B9FF, isIncome = true, sortOrder = 2),
        CategoryEntity(name = "Rentabilidad", emoji = "📈", color = 0xFF55EFC4, isIncome = true, sortOrder = 3, systemKey = SystemCategory.YIELD),
    )
}

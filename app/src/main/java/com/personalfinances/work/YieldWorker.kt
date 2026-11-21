package com.personalfinances.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.personalfinances.FinanceApp
import java.util.concurrent.TimeUnit

/** Suma diariamente la rentabilidad de las cuentas remuneradas. */
class YieldWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        (applicationContext as FinanceApp).container.repository.accrueYield()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<YieldWorker>(12, TimeUnit.HOURS).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("yield", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

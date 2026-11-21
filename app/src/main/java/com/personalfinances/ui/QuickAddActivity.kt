package com.personalfinances.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.fragment.app.FragmentActivity
import com.personalfinances.ui.lock.LockGate
import com.personalfinances.ui.lock.WindowSettingsEffect
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personalfinances.container
import com.personalfinances.data.db.TxType
import com.personalfinances.notifications.DetectionNotifier
import com.personalfinances.ui.screens.AddRequest
import com.personalfinances.ui.screens.AddTransactionContent
import com.personalfinances.ui.theme.FinanceTheme
import kotlinx.coroutines.launch

/**
 * Actividad liviana para agregar un movimiento rápido desde el widget, el tile de ajustes rápidos,
 * los accesos directos del ícono o una notificación detectada. Se muestra como hoja inferior.
 */
class QuickAddActivity : FragmentActivity() {
    private val vm: MainViewModel by viewModels { MainViewModel.factory(container) }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val detectionId = intent.getLongExtra(EXTRA_DETECTION, -1).takeIf { it > 0 }
        val type = intent.getStringExtra(EXTRA_TYPE)?.let { runCatching { TxType.valueOf(it) }.getOrNull() }
            ?: if (intent.action == ACTION_INCOME) TxType.INCOME else TxType.EXPENSE
        val request = AddRequest(type = type, detectionId = detectionId)

        setContent {
            val snapshot by vm.snapshot.collectAsStateWithLifecycle()
            WindowSettingsEffect(this, snapshot.settings, snapshot.loaded)
            FinanceTheme(snapshot.settings.themeMode, snapshot.settings.dynamicColor) {
              LockGate(this, snapshot.settings, snapshot.loaded) {
                val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val scope = rememberCoroutineScope()
                fun close() { scope.launch { sheetState.hide() }.invokeOnCompletion { finish() } }
                ModalBottomSheet(
                    onDismissRequest = { finish() },
                    sheetState = sheetState,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentWindowInsets = { androidx.compose.foundation.layout.WindowInsets(0) },
                ) {
                    AddTransactionContent(
                        request = request,
                        snapshot = snapshot,
                        onSave = { draft ->
                            vm.saveTransaction(draft)
                            draft.detectionId?.let { DetectionNotifier.cancel(this@QuickAddActivity, it) }
                            Toast.makeText(this@QuickAddActivity, "Guardado ✓", Toast.LENGTH_SHORT).show()
                            close()
                        },
                        onDelete = { id -> vm.deleteTransaction(id); close() },
                        onDismissDetection = { id -> vm.dismissDetection(id); DetectionNotifier.cancel(this@QuickAddActivity, id); close() },
                        onClose = ::close,
                    )
                }
              }
            }
        }
    }

    override fun finish() {
        super.finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, android.R.anim.fade_out)
    }

    companion object {
        const val EXTRA_DETECTION = "detection_id"
        const val EXTRA_TYPE = "type"
        const val ACTION_EXPENSE = "com.personalfinances.ADD_EXPENSE"
        const val ACTION_INCOME = "com.personalfinances.ADD_INCOME"

        fun intent(context: Context, type: TxType = TxType.EXPENSE, detectionId: Long? = null): Intent =
            Intent(context, QuickAddActivity::class.java)
                .setAction(if (type == TxType.INCOME) ACTION_INCOME else ACTION_EXPENSE)
                .putExtra(EXTRA_TYPE, type.name)
                .apply { detectionId?.let { putExtra(EXTRA_DETECTION, it); data = android.net.Uri.parse("finanzas://detection/$it") } }
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
    }
}

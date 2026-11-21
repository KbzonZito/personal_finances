package com.personalfinances.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.personalfinances.container
import com.personalfinances.data.db.TxType
import com.personalfinances.domain.Money
import com.personalfinances.ui.MainActivity
import com.personalfinances.ui.QuickAddActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

class QuickAddWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = withTimeoutOrNull(3_000) { context.container.repository.snapshot.first { it.loaded } }
        val total = snap?.let {
            if (it.settings.hideAmounts) "•••••" else Money.format(it.netWorth, it.settings.mainCurrency)
        } ?: "—"
        val pending = snap?.pending?.size ?: 0
        provideContent { GlanceTheme { Content(context, total, pending) } }
    }

    @Composable
    private fun Content(context: Context, total: String, pending: Int) {
        Column(
            modifier = GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = GlanceModifier.fillMaxWidth().clickable(actionStartActivity(android.content.Intent(context, MainActivity::class.java)))) {
                Text(
                    if (pending > 0) "Patrimonio · $pending por confirmar" else "Patrimonio",
                    style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp),
                )
                Text(total, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold))
            }
            Spacer(GlanceModifier.height(10.dp))
            Row(modifier = GlanceModifier.fillMaxWidth()) {
                Pill("− Gasto", GlanceTheme.colors.primary, GlanceTheme.colors.onPrimary, QuickAddActivity.intent(context, TxType.EXPENSE), GlanceModifier.defaultWeight())
                Spacer(GlanceModifier.width(8.dp))
                Pill("+ Ingreso", GlanceTheme.colors.secondaryContainer, GlanceTheme.colors.onSecondaryContainer, QuickAddActivity.intent(context, TxType.INCOME), GlanceModifier.defaultWeight())
            }
        }
    }

    @Composable
    private fun Pill(label: String, bg: ColorProvider, fg: ColorProvider, intent: android.content.Intent, modifier: GlanceModifier) {
        Box(
            modifier = modifier.height(40.dp).background(bg).cornerRadius(20.dp).clickable(actionStartActivity(intent)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, style = TextStyle(color = fg, fontWeight = FontWeight.Medium, fontSize = 14.sp))
        }
    }

    companion object {
        suspend fun refresh(context: Context) {
            runCatching { QuickAddWidget().updateAll(context) }
        }
    }
}

class QuickAddWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = QuickAddWidget()
}


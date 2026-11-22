package com.personalfinances.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.personalfinances.container
import com.personalfinances.domain.Money
import com.personalfinances.ui.MainActivity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import java.time.format.DateTimeFormatter
import java.util.Locale

private data class CardLine(val name: String, val available: String, val sub: String, val used: Float)

/** Widget con el cupo disponible y el próximo pago de cada tarjeta. */
class CardsWidget : GlanceAppWidget() {
    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = withTimeoutOrNull(3_000) { context.container.repository.snapshot.first { it.loaded } }
        val hide = snap?.settings?.let { it.hideAmounts || it.lockEnabled } ?: true
        val fmt = DateTimeFormatter.ofPattern("d MMM", Locale("es", "CL"))
        val lines = snap?.cards?.take(3)?.mapNotNull { st ->
            val c = st.card ?: return@mapNotNull null
            val cur = st.account.currency
            CardLine(
                st.account.name,
                if (hide) "•••••" else Money.format(c.available, cur),
                when {
                    c.billedDue <= 0 -> "Al día ✓"
                    hide -> "Pagar antes del ${c.dueDate.format(fmt)}"
                    else -> "Pagar ${Money.format(c.billedDue, cur)} · ${c.dueDate.format(fmt)}"
                },
                c.usedFraction,
            )
        }.orEmpty()
        provideContent { GlanceTheme { Content(context, lines) } }
    }

    @Composable
    private fun Content(context: Context, lines: List<CardLine>) {
        Column(
            GlanceModifier.fillMaxSize().background(GlanceTheme.colors.widgetBackground).cornerRadius(24.dp).padding(14.dp)
                .clickable(actionStartActivity(Intent(context, MainActivity::class.java))),
        ) {
            Text("Tarjetas · disponible", style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            if (lines.isEmpty()) {
                Spacer(GlanceModifier.height(8.dp))
                Text("Agrega una tarjeta en la app", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
            }
            lines.forEach { l ->
                Spacer(GlanceModifier.height(8.dp))
                Row(GlanceModifier.fillMaxWidth()) {
                    Text(l.name, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp), modifier = GlanceModifier.defaultWeight())
                    Text(l.available, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                }
                LinearProgressIndicator(l.used, GlanceModifier.fillMaxWidth().height(4.dp).padding(vertical = 2.dp), GlanceTheme.colors.primary, GlanceTheme.colors.surfaceVariant)
                Text(l.sub, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 11.sp))
            }
        }
    }

    companion object {
        suspend fun refresh(context: Context) { runCatching { CardsWidget().updateAll(context) } }
    }
}

class CardsWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CardsWidget()
}

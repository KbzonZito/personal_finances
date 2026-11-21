package com.personalfinances.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.personalfinances.data.TxItem
import com.personalfinances.data.db.TxType
import com.personalfinances.domain.Currency
import com.personalfinances.domain.Money
import com.personalfinances.ui.theme.AmountStyle
import com.personalfinances.ui.theme.LocalFinanceColors
import java.time.YearMonth
import java.time.format.TextStyle as JTextStyle
import java.util.Locale

val esCL: Locale = Locale("es", "CL")

fun monthShort(ym: YearMonth): String = ym.month.getDisplayName(JTextStyle.SHORT, esCL).replace(".", "").replaceFirstChar { it.uppercase() }
fun monthLong(ym: YearMonth): String = ym.month.getDisplayName(JTextStyle.FULL, esCL).replaceFirstChar { it.uppercase() }

/** Monto que anima suavemente cuando cambia su valor. */
@Composable
fun AnimatedAmount(
    value: Long,
    currency: Currency,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    hidden: Boolean = false,
    showSign: Boolean = false,
) {
    var start by remember { mutableLongStateOf(value) }
    var target by remember { mutableLongStateOf(value) }
    val progress = remember { Animatable(1f) }
    LaunchedEffect(value) {
        if (value == target) return@LaunchedEffect
        start = start + ((target - start) * progress.value.toDouble()).toLong()
        target = value
        progress.snapTo(0f)
        progress.animateTo(1f, tween(650, easing = FastOutSlowInEasing))
    }
    val p = progress.value
    val shown = if (p >= 1f) target else start + ((target - start) * p.toDouble()).toLong()
    Text(
        text = if (hidden) currency.symbol + "•••••" else Money.format(shown, currency, showSign),
        style = style.merge(AmountStyle),
        color = color,
        modifier = modifier,
        maxLines = 1,
    )
}

@Composable
fun AmountText(value: Long, currency: Currency, style: TextStyle, color: Color = Color.Unspecified, hidden: Boolean = false, showSign: Boolean = false, modifier: Modifier = Modifier) {
    Text(
        if (hidden) "•••••" else Money.format(value, currency, showSign),
        style = style.merge(AmountStyle), color = color, maxLines = 1, modifier = modifier,
    )
}

/** Barra de progreso redondeada con animación de resorte. */
@Composable
fun ProgressPill(fraction: Float, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary, track: Color = MaterialTheme.colorScheme.surfaceContainerHighest, height: Int = 10) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow), label = "progress")
    Canvas(modifier.fillMaxWidth().height(height.dp)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (f > 0f) drawRoundRect(color, size = Size(size.width * f.coerceAtLeast(0.02f), size.height), cornerRadius = r)
    }
}

/** Card con efecto de "press" (escala) para sensación táctil. */
@Composable
fun PressableCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.large,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(stiffness = Spring.StiffnessMediumLow), label = "press")
    Surface(
        modifier = modifier.scale(scale).clip(shape).clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick),
        color = color,
        shape = shape,
        content = content,
    )
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(
                action, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
fun EmojiBadge(emoji: String, color: Color, size: Int = 42) {
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun TransactionRow(item: TxItem, hidden: Boolean, onClick: () -> Unit, showAccount: Boolean = true) {
    val fc = LocalFinanceColors.current
    val tx = item.tx
    val currency = item.account?.currency ?: Currency.CLP
    val (emoji, color, title) = when (tx.type) {
        TxType.TRANSFER -> Triple(if (item.toAccount?.type?.isCard == true) "💳" else "⇄", MaterialTheme.colorScheme.primary,
            tx.description.ifBlank { if (item.toAccount?.type?.isCard == true) "Pago ${item.toAccount.name}" else "Transferencia" })
        TxType.INTEREST -> Triple("📈", fc.income, tx.description.ifBlank { "Rentabilidad" })
        TxType.ADJUSTMENT -> Triple("⚖️", fc.muted, tx.description.ifBlank { "Ajuste de saldo" })
        else -> Triple(item.category?.emoji ?: if (tx.type == TxType.INCOME) "💰" else "📦", Color(item.category?.color ?: 0xFF888888),
            tx.description.ifBlank { item.category?.name ?: if (tx.type == TxType.INCOME) "Ingreso" else "Gasto" })
    }
    val subtitle = buildString {
        if (showAccount) {
            append(item.account?.name ?: "")
            if (tx.type == TxType.TRANSFER && item.toAccount != null) append(" → ${item.toAccount.name}")
        }
        if (tx.installmentCount > 1) {
            if (isNotEmpty()) append(" · ")
            append("${tx.installmentCount} cuotas")
            if (item.billedInstallments > 0) append(" (${item.billedInstallments}/${tx.installmentCount})")
        }
        if (tx.originalCurrency != null && tx.originalAmount != null) {
            if (isNotEmpty()) append(" · ")
            append(Money.format(tx.originalAmount, Currency.fromCode(tx.originalCurrency)))
        }
        if (isEmpty() && item.category != null && tx.description.isNotBlank()) append(item.category.name)
    }
    val signed = when (tx.type) {
        TxType.EXPENSE -> -tx.amount
        TxType.TRANSFER -> tx.amount
        else -> tx.amount
    }
    val amountColor = when (tx.type) {
        TxType.INCOME, TxType.INTEREST -> fc.income
        TxType.TRANSFER -> MaterialTheme.colorScheme.onSurfaceVariant
        TxType.ADJUSTMENT -> if (tx.amount >= 0) fc.income else MaterialTheme.colorScheme.onSurface
        TxType.EXPENSE -> MaterialTheme.colorScheme.onSurface
    }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiBadge(emoji, color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        AmountText(signed, currency, MaterialTheme.typography.bodyLarge, amountColor, hidden, showSign = tx.type == TxType.INCOME || tx.type == TxType.INTEREST)
    }
}

/** Gráfico de barras mensual animado (proyección de cuotas). */
@Composable
fun MonthBars(data: List<Pair<YearMonth, Long>>, currency: Currency, hidden: Boolean, modifier: Modifier = Modifier, highlightFirst: Boolean = true) {
    val max = (data.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.Bottom) {
        data.forEachIndexed { i, (ym, v) ->
            val target = v.toFloat() / max
            val h by animateFloatAsState(target, tween(600, delayMillis = i * 35, easing = FastOutSlowInEasing), label = "bar$i")
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Canvas(Modifier.fillMaxWidth().height(96.dp)) {
                    val r = CornerRadius(8.dp.toPx())
                    drawRoundRect(track, cornerRadius = r)
                    val bh = size.height * h
                    if (v > 0) drawRoundRect(if (i == 0 && highlightFirst) primary else muted, topLeft = Offset(0f, size.height - bh), size = Size(size.width, bh), cornerRadius = r)
                }
                Spacer(Modifier.height(4.dp))
                Text(monthShort(ym), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

/** Contador con animación vertical (p.ej. número de pendientes). */
@Composable
fun AnimatedCount(count: Int, style: TextStyle) {
    AnimatedContent(count, transitionSpec = {
        (slideInVertically { it } + fadeIn()) togetherWith (slideOutVertically { -it } + fadeOut())
    }, label = "count") { Text("$it", style = style) }
}

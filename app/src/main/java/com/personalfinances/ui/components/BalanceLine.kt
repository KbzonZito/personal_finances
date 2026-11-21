package com.personalfinances.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.dp

/** Línea de saldo con relleno degradado que se dibuja de izquierda a derecha. Marca el cero y el punto más bajo. */
@Composable
fun BalanceLine(values: List<Long>, modifier: Modifier = Modifier, negativeColor: Color = MaterialTheme.colorScheme.error) {
    if (values.size < 2) return
    val line = MaterialTheme.colorScheme.primary
    val zero = MaterialTheme.colorScheme.outline
    val progress = remember(values) { Animatable(0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        // Escala al rango de los datos (con un margen) para que se noten los cambios.
        val lo = values.min().toFloat()
        val hi = values.max().toFloat()
        val margin = ((hi - lo) * 0.15f).coerceAtLeast(1f)
        val min = lo - margin
        val max = hi + margin
        val pad = 6.dp.toPx()
        fun x(i: Int) = i * size.width / (values.size - 1)
        fun y(v: Long) = pad + (size.height - 2 * pad) * (1 - (v - min) / (max - min))
        val path = Path().apply {
            moveTo(x(0), y(values[0]))
            for (i in 1 until values.size) lineTo(x(i), y(values[i]))
        }
        val fill = Path().apply { addPath(path); lineTo(size.width, size.height); lineTo(0f, size.height); close() }
        clipRect(right = size.width * progress.value) {
            drawPath(fill, Brush.verticalGradient(listOf(line.copy(alpha = 0.25f), Color.Transparent)))
            drawPath(path, line, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
            if (values.min() < 0 && values.max() > 0) {
                val zy = y(0)
                drawLine(zero, Offset(0f, zy), Offset(size.width, zy), strokeWidth = 1.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
            }
            val iMin = values.indexOf(values.min())
            drawCircle(if (values[iMin] < 0) negativeColor else line, radius = 5.dp.toPx(), center = Offset(x(iMin), y(values[iMin])))
        }
    }
}

package com.personalfinances.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Dona animada (se dibuja en sentido horario). */
@Composable
fun Donut(slices: List<Pair<Float, Color>>, modifier: Modifier = Modifier, track: Color = Color.Gray.copy(alpha = 0.15f)) {
    val progress = remember(slices) { Animatable(0f) }
    LaunchedEffect(slices) { progress.animateTo(1f, tween(800, easing = FastOutSlowInEasing)) }
    val total = slices.sumOf { it.first.toDouble() }.toFloat()
    Canvas(modifier) {
        val stroke = 22.dp.toPx()
        val d = size.minDimension - stroke
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
        if (total <= 0f) return@Canvas
        val gap = if (slices.size > 1) 2f else 0f
        var start = -90f
        val sweepTotal = 360f * progress.value
        for ((v, c) in slices) {
            val sweep = v / total * 360f
            val visible = (minOf(start + sweep, -90f + sweepTotal) - start).coerceAtLeast(0f)
            if (visible > gap) drawArc(c, start + gap / 2, visible - gap, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

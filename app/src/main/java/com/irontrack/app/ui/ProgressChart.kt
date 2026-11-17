package com.irontrack.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.irontrack.app.ProgressPoint

@Composable
fun ProgressChart(points: List<ProgressPoint>, modifier: Modifier = Modifier) {
    val line = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outline
    val hole = MaterialTheme.colorScheme.surface
    Canvas(modifier.fillMaxWidth().height(148.dp)) {
        val left = 2.dp.toPx()
        val right = size.width - 2.dp.toPx()
        val top = 10.dp.toPx()
        val bottom = size.height - 10.dp.toPx()

        for (index in 0..2) {
            val y = top + (bottom - top) * index / 2f
            drawLine(
                color = grid.copy(alpha = 0.45f),
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = 1.dp.toPx(),
            )
        }

        if (points.isEmpty()) return@Canvas
        val min = points.minOf { it.e1rm }
        val max = points.maxOf { it.e1rm }
        val range = (max - min).coerceAtLeast(1.0)

        fun pointAt(index: Int): Offset {
            val x = if (points.size == 1) (left + right) / 2f
            else left + (right - left) * index / (points.size - 1).toFloat()
            val y = bottom - ((points[index].e1rm - min) / range).toFloat() * (bottom - top)
            return Offset(x, y)
        }

        if (points.size == 1) {
            drawCircle(line, radius = 4.dp.toPx(), center = pointAt(0))
            return@Canvas
        }

        val path = Path()
        val first = pointAt(0)
        path.moveTo(first.x, first.y)
        for (index in 1 until points.size) {
            val previous = pointAt(index - 1)
            val current = pointAt(index)
            val midX = (previous.x + current.x) / 2f
            path.cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
        }
        drawPath(
            path = path,
            color = line,
            style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
        val last = pointAt(points.lastIndex)
        drawCircle(hole, radius = 5.dp.toPx(), center = last)
        drawCircle(line, radius = 3.dp.toPx(), center = last)
    }
}

/** A quiet barbell mark: the signature detail of this app. */
@Composable
fun BarbellMark(modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier) {
        val centerY = size.height / 2f
        val barStart = size.width * 0.18f
        val barEnd = size.width * 0.82f
        drawLine(color, Offset(barStart, centerY), Offset(barEnd, centerY), strokeWidth = 1.5.dp.toPx())
        val plateWidth = 3.dp.toPx()
        val inner = size.width * 0.16f
        val outer = size.width * 0.30f
        listOf(inner, outer).forEach { offset ->
            drawLine(color, Offset(size.width / 2f - offset, centerY - size.height * 0.32f),
                Offset(size.width / 2f - offset, centerY + size.height * 0.32f),
                strokeWidth = plateWidth, cap = StrokeCap.Round)
            drawLine(color, Offset(size.width / 2f + offset, centerY - size.height * 0.32f),
                Offset(size.width / 2f + offset, centerY + size.height * 0.32f),
                strokeWidth = plateWidth, cap = StrokeCap.Round)
        }
        drawCircle(color, radius = 1.6.dp.toPx(), center = Offset(size.width / 2f, centerY))
    }
}

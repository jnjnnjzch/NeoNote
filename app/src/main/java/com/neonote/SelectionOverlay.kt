package com.neonote

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasRect

@Composable
internal fun SelectionOverlay(
    activeLassoPath: List<CanvasPoint>,
    selectedBounds: CanvasRect?,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        selectedBounds?.let { bounds ->
            drawRect(
                color = Color(0xFF2563EB),
                topLeft = Offset(bounds.left, bounds.top),
                size = Size(
                    width = bounds.right - bounds.left,
                    height = bounds.bottom - bounds.top,
                ),
                style = Stroke(width = 2f),
            )
        }
        activeLassoPath.zipWithNext { start, end ->
            drawLine(
                color = Color(0xFF2563EB),
                start = Offset(start.x, start.y),
                end = Offset(end.x, end.y),
                strokeWidth = 2f,
                cap = StrokeCap.Round,
            )
        }
    }
}

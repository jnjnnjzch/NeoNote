package com.neonote

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import com.neonote.engine.InkStrokeWidthMapper
import com.neonote.ink.InkRenderCache
import com.neonote.model.CanvasRect
import com.neonote.model.InkBrush
import com.neonote.model.InkLayer
import com.neonote.model.InkStroke

@Composable
public fun InkLayerView(
    pageId: String?,
    inkLayer: InkLayer,
    activeStroke: InkStroke?,
    visibleBounds: CanvasRect? = null,
    modifier: Modifier = Modifier,
) {
    val cache = remember(pageId) { InkRenderCache() }
    DisposableEffect(cache) { onDispose { cache.clear() } }
    LaunchedEffect(cache, inkLayer.strokes) { cache.sync(inkLayer.strokes) }

    val committedTiles = cache.tiles
    val cacheVersion = cache.version
    Canvas(modifier = modifier) {
        cacheVersion
        committedTiles.forEach { tile ->
            val tileBounds = CanvasRect(
                tile.originX,
                tile.originY,
                tile.originX + cache.tileSize,
                tile.originY + cache.tileSize,
            )
            if (visibleBounds == null || tileBounds.intersects(visibleBounds)) {
                drawImage(tile.imageBitmap, topLeft = Offset(tile.originX, tile.originY))
            }
        }
        activeStroke?.let { drawInkStroke(it) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInkStroke(stroke: InkStroke) {
    val style = stroke.style.normalized()
    val alpha = when (style.brush) {
        InkBrush.Pen -> style.opacity
        InkBrush.Highlighter -> style.opacity.coerceAtMost(0.45f)
    }
    val color = Color(style.colorArgb).copy(alpha = alpha)
    if (stroke.points.size == 1) {
        val point = stroke.points.single()
        drawCircle(
            color = color,
            radius = InkStrokeWidthMapper.widthForPressure(point.pressure, style) / 2f,
            center = Offset(point.x, point.y),
        )
        return
    }
    stroke.points.zipWithNext { start, end ->
        drawLine(
            color = color,
            start = Offset(start.x, start.y),
            end = Offset(end.x, end.y),
            strokeWidth = InkStrokeWidthMapper.widthForSegment(start, end, style),
            cap = StrokeCap.Round,
        )
    }
}

private fun CanvasRect.intersects(other: CanvasRect): Boolean =
    right >= other.left && left <= other.right && bottom >= other.top && top <= other.bottom

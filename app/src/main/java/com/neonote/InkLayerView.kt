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
import com.neonote.model.InkLayer
import com.neonote.model.InkStroke

@Composable
public fun InkLayerView(
    pageId: String?,
    inkLayer: InkLayer,
    activeStroke: InkStroke?,
    modifier: Modifier = Modifier,
) {
    val cache = remember(pageId) { InkRenderCache() }
    DisposableEffect(cache) {
        onDispose { cache.clear() }
    }

    LaunchedEffect(cache, inkLayer.strokes) {
        cache.sync(strokes = inkLayer.strokes)
    }

    val committedTiles = cache.tiles
    val cacheVersion = cache.version
    Canvas(
        modifier = modifier,
    ) {
        // Read the version so Compose redraws when the same bitmap memory is updated.
        cacheVersion
        committedTiles.forEach { tile ->
            drawImage(
                image = tile.imageBitmap,
                topLeft = Offset(tile.originX, tile.originY),
            )
        }
        activeStroke?.let { stroke -> drawInkStroke(stroke) }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawInkStroke(stroke: InkStroke) {
    stroke.points.zipWithNext { start, end ->
        drawLine(
            color = Color(0xFF0F172A),
            start = Offset(start.x, start.y),
            end = Offset(end.x, end.y),
            strokeWidth = InkStrokeWidthMapper.widthForSegment(start, end),
            cap = StrokeCap.Round,
        )
    }
}

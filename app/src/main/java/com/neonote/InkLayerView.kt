package com.neonote

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntSize
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
    var layerSize by remember(pageId) { mutableStateOf(IntSize.Zero) }

    DisposableEffect(cache) {
        onDispose { cache.clear() }
    }

    LaunchedEffect(cache, inkLayer.strokes, layerSize) {
        cache.sync(
            strokes = inkLayer.strokes,
            width = layerSize.width,
            height = layerSize.height,
        )
    }

    val committedBitmap = cache.imageBitmap
    val cacheVersion = cache.version
    Canvas(
        modifier = modifier.onSizeChanged { size -> layerSize = size },
    ) {
        // Read the version so Compose redraws when the same bitmap memory is updated.
        cacheVersion
        if (committedBitmap != null) {
            drawImage(image = committedBitmap, topLeft = Offset.Zero)
        }
        activeStroke?.drawInkStroke()
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

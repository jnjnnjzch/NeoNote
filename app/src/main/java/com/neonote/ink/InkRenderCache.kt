package com.neonote.ink

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color as AndroidColor
import android.graphics.Paint
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.neonote.engine.InkStrokeWidthMapper
import com.neonote.model.InkStroke

/**
 * Page-level bitmap cache for committed ink strokes.
 *
 * The vector [InkStroke] list remains the source of truth. This cache only keeps
 * a rasterized copy of committed strokes so active in-progress strokes can be
 * drawn without replaying the complete stroke history on every frame.
 */
public class InkRenderCache(
    private val strokeRenderer: InkStrokeBitmapRenderer = AndroidInkStrokeBitmapRenderer(),
) {
    private var target: Bitmap? = null
    private var cachedStrokes: List<InkStroke> = emptyList()
    private var targetWidth: Int = 0
    private var targetHeight: Int = 0

    public var imageBitmap: ImageBitmap? by mutableStateOf(null)
        private set

    /** Monotonic counter that Compose can observe to redraw reused bitmap memory. */
    public var version: Int by mutableIntStateOf(0)
        private set

    public fun sync(strokes: List<InkStroke>, width: Int, height: Int): InkRenderCacheUpdate {
        if (width <= 0 || height <= 0) {
            clear()
            return InkRenderCacheUpdate.Cleared
        }

        val sizeChanged = width != targetWidth || height != targetHeight || target == null
        ensureTarget(width, height)
        val bitmap = requireNotNull(target)
        val update = planInkRenderCacheUpdate(
            cachedStrokes = cachedStrokes,
            nextStrokes = strokes,
            sameSize = !sizeChanged,
        )

        when (update) {
            is InkRenderCacheUpdate.Appended -> {
                strokeRenderer.render(bitmap, update.strokes)
                cachedStrokes = strokes.toList()
                publish(bitmap)
            }
            is InkRenderCacheUpdate.Rebuilt -> {
                bitmap.eraseColor(AndroidColor.TRANSPARENT)
                strokeRenderer.render(bitmap, strokes)
                cachedStrokes = strokes.toList()
                publish(bitmap)
            }
            InkRenderCacheUpdate.Cleared -> {
                bitmap.eraseColor(AndroidColor.TRANSPARENT)
                cachedStrokes = emptyList()
                publish(bitmap)
            }
            InkRenderCacheUpdate.Unchanged -> Unit
        }

        return update
    }

    public fun clear() {
        target?.recycle()
        target = null
        targetWidth = 0
        targetHeight = 0
        cachedStrokes = emptyList()
        imageBitmap = null
        version++
    }

    private fun ensureTarget(width: Int, height: Int) {
        if (target != null && targetWidth == width && targetHeight == height) return
        target?.recycle()
        target = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(AndroidColor.TRANSPARENT)
        }
        targetWidth = width
        targetHeight = height
        cachedStrokes = emptyList()
        imageBitmap = null
    }

    private fun publish(bitmap: Bitmap) {
        imageBitmap = bitmap.asImageBitmap()
        version++
    }
}

public sealed interface InkRenderCacheUpdate {
    public data object Unchanged : InkRenderCacheUpdate
    public data object Cleared : InkRenderCacheUpdate
    public data class Appended(val strokes: List<InkStroke>) : InkRenderCacheUpdate
    public data object Rebuilt : InkRenderCacheUpdate
}

public fun planInkRenderCacheUpdate(
    cachedStrokes: List<InkStroke>,
    nextStrokes: List<InkStroke>,
    sameSize: Boolean,
): InkRenderCacheUpdate {
    if (nextStrokes.isEmpty()) {
        return if (cachedStrokes.isEmpty() && sameSize) InkRenderCacheUpdate.Unchanged else InkRenderCacheUpdate.Cleared
    }
    if (!sameSize || cachedStrokes.isEmpty()) return InkRenderCacheUpdate.Rebuilt
    if (cachedStrokes == nextStrokes) return InkRenderCacheUpdate.Unchanged
    if (cachedStrokes.size < nextStrokes.size && nextStrokes.subList(0, cachedStrokes.size) == cachedStrokes) {
        return InkRenderCacheUpdate.Appended(nextStrokes.drop(cachedStrokes.size))
    }
    return InkRenderCacheUpdate.Rebuilt
}

public interface InkStrokeBitmapRenderer {
    public fun render(bitmap: Bitmap, strokes: List<InkStroke>)
}

public class AndroidInkStrokeBitmapRenderer(
    private val color: Int = AndroidColor.rgb(0x0F, 0x17, 0x2A),
) : InkStrokeBitmapRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        this.color = this@AndroidInkStrokeBitmapRenderer.color
    }

    override fun render(bitmap: Bitmap, strokes: List<InkStroke>) {
        val canvas = Canvas(bitmap)
        strokes.forEach { stroke ->
            stroke.points.zipWithNext { start, end ->
                paint.strokeWidth = InkStrokeWidthMapper.widthForSegment(start, end)
                canvas.drawLine(start.x, start.y, end.x, end.y, paint)
            }
        }
    }
}

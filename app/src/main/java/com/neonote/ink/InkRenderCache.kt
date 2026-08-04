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
import com.neonote.model.InkBrush
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

public data class TileKey(val tileX: Int, val tileY: Int)

public data class InkRenderTile(
    val key: TileKey,
    val originX: Float,
    val originY: Float,
    val imageBitmap: ImageBitmap,
)

public data class InkStrokeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/** Document-space tile cache. Vector strokes remain the source of truth. */
public class InkRenderCache(
    private val strokeRenderer: InkStrokeBitmapRenderer = AndroidInkStrokeBitmapRenderer(),
    public val tileSize: Float = DEFAULT_TILE_SIZE,
) {
    private val tileBitmaps = linkedMapOf<TileKey, Bitmap>()
    private var cachedStrokes: List<InkStroke> = emptyList()

    public var tiles: List<InkRenderTile> by mutableStateOf(emptyList())
        private set
    public var version: Int by mutableIntStateOf(0)
        private set

    public fun sync(strokes: List<InkStroke>): InkRenderCacheUpdate {
        val update = planInkRenderCacheUpdate(cachedStrokes, strokes)
        when (update) {
            is InkRenderCacheUpdate.Appended -> {
                renderStrokes(update.strokes)
                cachedStrokes = strokes.toList()
                publish()
            }
            InkRenderCacheUpdate.Rebuilt -> {
                clearTiles()
                renderStrokes(strokes)
                cachedStrokes = strokes.toList()
                publish()
            }
            InkRenderCacheUpdate.Cleared -> {
                clearTiles()
                cachedStrokes = emptyList()
                publish()
            }
            InkRenderCacheUpdate.Unchanged -> Unit
        }
        return update
    }

    public fun clear() {
        clearTiles()
        cachedStrokes = emptyList()
        tiles = emptyList()
        version++
    }

    private fun renderStrokes(strokes: List<InkStroke>) {
        strokes.forEach { stroke ->
            intersectingTileKeys(stroke, tileSize).forEach { key ->
                val bitmap = tileBitmaps.getOrPut(key) { createTileBitmap() }
                strokeRenderer.render(bitmap, listOf(stroke.toTileLocal(key, tileSize)))
            }
        }
    }

    private fun createTileBitmap(): Bitmap =
        Bitmap.createBitmap(tileSize.toInt(), tileSize.toInt(), Bitmap.Config.ARGB_8888).also {
            it.eraseColor(AndroidColor.TRANSPARENT)
        }

    private fun clearTiles() {
        tileBitmaps.values.forEach(Bitmap::recycle)
        tileBitmaps.clear()
    }

    private fun publish() {
        tiles = tileBitmaps
            .toSortedMap(compareBy<TileKey> { it.tileY }.thenBy { it.tileX })
            .map { (key, bitmap) -> InkRenderTile(
                key = key,
                originX = key.originX(tileSize),
                originY = key.originY(tileSize),
                imageBitmap = bitmap.asImageBitmap(),
            ) }
        version++
    }

    public companion object { public const val DEFAULT_TILE_SIZE: Float = 1024f }
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
): InkRenderCacheUpdate {
    if (nextStrokes.isEmpty()) return if (cachedStrokes.isEmpty()) InkRenderCacheUpdate.Unchanged else InkRenderCacheUpdate.Cleared
    if (cachedStrokes.isEmpty()) return InkRenderCacheUpdate.Rebuilt
    if (cachedStrokes == nextStrokes) return InkRenderCacheUpdate.Unchanged
    if (cachedStrokes.size < nextStrokes.size && nextStrokes.subList(0, cachedStrokes.size) == cachedStrokes) {
        return InkRenderCacheUpdate.Appended(nextStrokes.drop(cachedStrokes.size))
    }
    return InkRenderCacheUpdate.Rebuilt
}

public fun tileKeyForPoint(x: Float, y: Float, tileSize: Float = InkRenderCache.DEFAULT_TILE_SIZE): TileKey =
    TileKey(floor(x / tileSize).toInt(), floor(y / tileSize).toInt())

public fun TileKey.originX(tileSize: Float = InkRenderCache.DEFAULT_TILE_SIZE): Float = tileX * tileSize
public fun TileKey.originY(tileSize: Float = InkRenderCache.DEFAULT_TILE_SIZE): Float = tileY * tileSize

public fun InkStroke.bounds(): InkStrokeBounds? {
    if (points.isEmpty()) return null
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    points.forEach { point ->
        left = min(left, point.x)
        top = min(top, point.y)
        right = max(right, point.x)
        bottom = max(bottom, point.y)
    }
    val radius = maxStrokeRadius()
    return InkStrokeBounds(left - radius, top - radius, right + radius, bottom + radius)
}

public fun intersectingTileKeys(
    stroke: InkStroke,
    tileSize: Float = InkRenderCache.DEFAULT_TILE_SIZE,
): Set<TileKey> {
    val bounds = stroke.bounds() ?: return emptySet()
    val minX = floor(bounds.left / tileSize).toInt()
    val maxX = floor(bounds.right / tileSize).toInt()
    val minY = floor(bounds.top / tileSize).toInt()
    val maxY = floor(bounds.bottom / tileSize).toInt()
    return buildSet {
        for (tileY in minY..maxY) for (tileX in minX..maxX) add(TileKey(tileX, tileY))
    }
}

public fun InkStroke.toTileLocal(
    key: TileKey,
    tileSize: Float = InkRenderCache.DEFAULT_TILE_SIZE,
): InkStroke = copy(points = points.map { point ->
    point.copy(x = point.x - key.originX(tileSize), y = point.y - key.originY(tileSize))
})

private fun InkStroke.maxStrokeRadius(): Float {
    val normalized = style.normalized()
    if (points.size < 2) return ceil(normalized.baseWidth / 2f)
    var maxWidth = 0f
    points.zipWithNext { start, end ->
        maxWidth = max(maxWidth, InkStrokeWidthMapper.widthForSegment(start, end, normalized))
    }
    return ceil(maxWidth / 2f)
}

public interface InkStrokeBitmapRenderer {
    public fun render(bitmap: Bitmap, strokes: List<InkStroke>)
}

/** Android bitmap renderer matching the live Compose renderer's style contract. */
public class AndroidInkStrokeBitmapRenderer : InkStrokeBitmapRenderer {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    override fun render(bitmap: Bitmap, strokes: List<InkStroke>) {
        val canvas = Canvas(bitmap)
        strokes.forEach { stroke ->
            val style = stroke.style.normalized()
            paint.color = style.colorArgb
            val sourceAlpha = AndroidColor.alpha(style.colorArgb)
            val brushOpacity = when (style.brush) {
                InkBrush.Pen -> style.opacity
                InkBrush.Highlighter -> style.opacity.coerceAtMost(0.45f)
            }
            paint.alpha = (sourceAlpha * brushOpacity).toInt().coerceIn(0, 255)
            if (stroke.points.size == 1) {
                val point = stroke.points.single()
                paint.style = Paint.Style.FILL
                canvas.drawCircle(
                    point.x,
                    point.y,
                    InkStrokeWidthMapper.widthForPressure(point.pressure, style) / 2f,
                    paint,
                )
                paint.style = Paint.Style.STROKE
            } else {
                stroke.points.zipWithNext { start, end ->
                    paint.strokeWidth = InkStrokeWidthMapper.widthForSegment(start, end, style)
                    canvas.drawLine(start.x, start.y, end.x, end.y, paint)
                }
            }
        }
    }
}

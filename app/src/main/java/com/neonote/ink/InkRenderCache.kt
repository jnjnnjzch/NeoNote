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
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/** Identifies a document-space ink cache tile. */
public data class TileKey(val tileX: Int, val tileY: Int)

/** Rasterized committed ink for a single document-space tile. */
public data class InkRenderTile(
    val key: TileKey,
    val originX: Float,
    val originY: Float,
    val imageBitmap: ImageBitmap,
)

/** Document-space bounds for an [InkStroke]. */
public data class InkStrokeBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

/**
 * Document-space tile bitmap cache for committed ink strokes.
 *
 * The vector [InkStroke] list remains the source of truth. This cache only keeps
 * rasterized copies of committed strokes in fixed-size document tiles so active
 * in-progress strokes can be drawn without replaying the complete stroke history
 * on every frame. Because tiles live in document coordinates, far-away strokes
 * are not clipped by the current layer size and continue to align with document
 * content after pan/zoom transforms.
 */
public class InkRenderCache(
    private val strokeRenderer: InkStrokeBitmapRenderer = AndroidInkStrokeBitmapRenderer(),
    public val tileSize: Float = DEFAULT_TILE_SIZE,
) {
    private val tileBitmaps: MutableMap<TileKey, Bitmap> = linkedMapOf()
    private var cachedStrokes: List<InkStroke> = emptyList()

    public var tiles: List<InkRenderTile> by mutableStateOf(emptyList())
        private set

    /** Monotonic counter that Compose can observe to redraw reused bitmap memory. */
    public var version: Int by mutableIntStateOf(0)
        private set

    public fun sync(strokes: List<InkStroke>): InkRenderCacheUpdate {
        val update = planInkRenderCacheUpdate(
            cachedStrokes = cachedStrokes,
            nextStrokes = strokes,
        )

        when (update) {
            is InkRenderCacheUpdate.Appended -> {
                renderStrokes(update.strokes)
                cachedStrokes = strokes.toList()
                publish()
            }
            is InkRenderCacheUpdate.Rebuilt -> {
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
                strokeRenderer.render(
                    bitmap = bitmap,
                    strokes = listOf(stroke.toTileLocal(key, tileSize)),
                )
            }
        }
    }

    private fun createTileBitmap(): Bitmap =
        Bitmap.createBitmap(tileSize.toInt(), tileSize.toInt(), Bitmap.Config.ARGB_8888).also { bitmap ->
            bitmap.eraseColor(AndroidColor.TRANSPARENT)
        }

    private fun clearTiles() {
        tileBitmaps.values.forEach { bitmap -> bitmap.recycle() }
        tileBitmaps.clear()
    }

    private fun publish() {
        tiles = tileBitmaps
            .toSortedMap(compareBy<TileKey> { it.tileY }.thenBy { it.tileX })
            .map { (key, bitmap) ->
                InkRenderTile(
                    key = key,
                    originX = key.originX(tileSize),
                    originY = key.originY(tileSize),
                    imageBitmap = bitmap.asImageBitmap(),
                )
            }
        version++
    }

    public companion object {
        public const val DEFAULT_TILE_SIZE: Float = 1024f
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
): InkRenderCacheUpdate {
    if (nextStrokes.isEmpty()) {
        return if (cachedStrokes.isEmpty()) InkRenderCacheUpdate.Unchanged else InkRenderCacheUpdate.Cleared
    }
    if (cachedStrokes.isEmpty()) return InkRenderCacheUpdate.Rebuilt
    if (cachedStrokes == nextStrokes) return InkRenderCacheUpdate.Unchanged
    if (cachedStrokes.size < nextStrokes.size && nextStrokes.subList(0, cachedStrokes.size) == cachedStrokes) {
        return InkRenderCacheUpdate.Appended(nextStrokes.drop(cachedStrokes.size))
    }
    return InkRenderCacheUpdate.Rebuilt
}

public fun tileKeyForPoint(x: Float, y: Float, tileSize: Float = InkRenderCache.DEFAULT_TILE_SIZE): TileKey =
    TileKey(tileX = floor(x / tileSize).toInt(), tileY = floor(y / tileSize).toInt())

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
    return InkStrokeBounds(
        left = left - radius,
        top = top - radius,
        right = right + radius,
        bottom = bottom + radius,
    )
}

public fun intersectingTileKeys(
    stroke: InkStroke,
    tileSize: Float = InkRenderCache.DEFAULT_TILE_SIZE,
): Set<TileKey> {
    val bounds = stroke.bounds() ?: return emptySet()
    val minTileX = floor(bounds.left / tileSize).toInt()
    val maxTileX = floor(bounds.right / tileSize).toInt()
    val minTileY = floor(bounds.top / tileSize).toInt()
    val maxTileY = floor(bounds.bottom / tileSize).toInt()
    return buildSet {
        for (tileY in minTileY..maxTileY) {
            for (tileX in minTileX..maxTileX) {
                add(TileKey(tileX, tileY))
            }
        }
    }
}

public fun InkStroke.toTileLocal(
    key: TileKey,
    tileSize: Float = InkRenderCache.DEFAULT_TILE_SIZE,
): InkStroke {
    val tileOriginX = key.originX(tileSize)
    val tileOriginY = key.originY(tileSize)
    return copy(
        points = points.map { point ->
            InkPoint(
                x = point.x - tileOriginX,
                y = point.y - tileOriginY,
                pressure = point.pressure,
                rawPressure = point.rawPressure,
            )
        },
    )
}

private fun InkStroke.maxStrokeRadius(): Float {
    if (points.size < 2) return 0f
    var maxWidth = 0f
    points.zipWithNext { start, end ->
        maxWidth = max(maxWidth, InkStrokeWidthMapper.widthForSegment(start, end))
    }
    return ceil(maxWidth / 2f)
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

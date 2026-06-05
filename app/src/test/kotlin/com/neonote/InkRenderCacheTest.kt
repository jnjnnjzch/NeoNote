package com.neonote

import com.neonote.ink.InkRenderCache
import com.neonote.ink.InkRenderCacheUpdate
import com.neonote.ink.TileKey
import com.neonote.ink.intersectingTileKeys
import com.neonote.ink.originX
import com.neonote.ink.originY
import com.neonote.ink.planInkRenderCacheUpdate
import com.neonote.ink.tileKeyForPoint
import com.neonote.ink.toTileLocal
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InkRenderCacheTest {
    @Test
    fun `document point maps to stable tile key and origin`() {
        assertEquals(TileKey(0, 0), tileKeyForPoint(0f, 0f, tileSize = TILE_SIZE))
        assertEquals(TileKey(0, 0), tileKeyForPoint(1023.9f, 1023.9f, tileSize = TILE_SIZE))
        assertEquals(TileKey(1, 2), tileKeyForPoint(1024f, 2048f, tileSize = TILE_SIZE))
        assertEquals(TileKey(-1, -1), tileKeyForPoint(-0.1f, -0.1f, tileSize = TILE_SIZE))

        val farTile = TileKey(12, -4)
        assertEquals(12_288f, farTile.originX(TILE_SIZE))
        assertEquals(-4_096f, farTile.originY(TILE_SIZE))
    }

    @Test
    fun `far away stroke maps to document space tile instead of layer size bounds`() {
        val farStroke = stroke(
            id = "far-stroke",
            startX = 10_500f,
            startY = 7_300f,
            endX = 10_700f,
            endY = 7_500f,
        )

        assertEquals(setOf(TileKey(10, 7)), intersectingTileKeys(farStroke, tileSize = TILE_SIZE))
    }

    @Test
    fun `stroke crossing tile boundary maps to every intersecting tile`() {
        val crossingStroke = stroke(
            id = "crossing-stroke",
            startX = 1_020f,
            startY = 20f,
            endX = 1_030f,
            endY = 20f,
        )

        assertEquals(setOf(TileKey(0, 0), TileKey(1, 0)), intersectingTileKeys(crossingStroke, tileSize = TILE_SIZE))
    }

    @Test
    fun `tile local coordinate conversion subtracts tile origin`() {
        val documentStroke = stroke(
            id = "local-stroke",
            startX = 2_100f,
            startY = 3_100f,
            endX = 2_150f,
            endY = 3_125f,
        )

        val localStroke = documentStroke.toTileLocal(TileKey(2, 3), tileSize = TILE_SIZE)

        assertEquals(
            listOf(
                InkPoint(x = 52f, y = 28f, pressure = 0.5f),
                InkPoint(x = 102f, y = 53f, pressure = 0.75f),
            ),
            localStroke.points,
        )
    }

    @Test
    fun `new committed strokes append to existing tile cache`() {
        val firstStroke = stroke("stroke-1")
        val secondStroke = stroke("stroke-2", endX = 20f)

        val update = planInkRenderCacheUpdate(
            cachedStrokes = listOf(firstStroke),
            nextStrokes = listOf(firstStroke, secondStroke),
        )

        val appended = assertIs<InkRenderCacheUpdate.Appended>(update)
        assertEquals(listOf(secondStroke), appended.strokes)
    }

    @Test
    fun `page switch or load rebuilds cache from vector strokes when cache is empty`() {
        val committedStrokes = listOf(stroke("stroke-1"), stroke("stroke-2", endY = 20f))

        val update = planInkRenderCacheUpdate(
            cachedStrokes = emptyList(),
            nextStrokes = committedStrokes,
        )

        assertEquals(InkRenderCacheUpdate.Rebuilt, update)
    }

    @Test
    fun `changed vector stroke with same id rebuilds the tile cache`() {
        val originalStroke = stroke("stroke-1")
        val movedStroke = stroke("stroke-1", startX = 5f, endX = 15f)

        val update = planInkRenderCacheUpdate(
            cachedStrokes = listOf(originalStroke),
            nextStrokes = listOf(movedStroke),
        )

        assertEquals(InkRenderCacheUpdate.Rebuilt, update)
    }

    @Test
    fun `deleted strokes clear or rebuild the tile cache instead of using stale bitmaps`() {
        val firstStroke = stroke("stroke-1")
        val secondStroke = stroke("stroke-2")

        val update = planInkRenderCacheUpdate(
            cachedStrokes = listOf(firstStroke, secondStroke),
            nextStrokes = listOf(firstStroke),
        )

        assertEquals(InkRenderCacheUpdate.Rebuilt, update)
        assertEquals(
            InkRenderCacheUpdate.Cleared,
            planInkRenderCacheUpdate(cachedStrokes = listOf(firstStroke), nextStrokes = emptyList()),
        )
    }

    @Test
    fun `unchanged empty vectors keep the cleared tile cache`() {
        assertEquals(
            InkRenderCacheUpdate.Unchanged,
            planInkRenderCacheUpdate(cachedStrokes = emptyList(), nextStrokes = emptyList()),
        )
    }

    @Test
    fun `unchanged vectors keep the existing committed tile bitmaps`() {
        val committedStrokes = listOf(stroke("stroke-1"))

        val update = planInkRenderCacheUpdate(
            cachedStrokes = committedStrokes,
            nextStrokes = committedStrokes,
        )

        assertEquals(InkRenderCacheUpdate.Unchanged, update)
    }

    private fun stroke(
        id: String,
        startX: Float = 0f,
        startY: Float = 0f,
        endX: Float = 10f,
        endY: Float = 10f,
    ): InkStroke = InkStroke(
        id = id,
        points = listOf(
            InkPoint(x = startX, y = startY, pressure = 0.5f),
            InkPoint(x = endX, y = endY, pressure = 0.75f),
        ),
    )

    private companion object {
        private const val TILE_SIZE = InkRenderCache.DEFAULT_TILE_SIZE
    }
}

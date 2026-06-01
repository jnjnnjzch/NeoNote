package com.neonote

import com.neonote.ink.InkRenderCacheUpdate
import com.neonote.ink.planInkRenderCacheUpdate
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InkRenderCacheTest {
    @Test
    fun `new committed strokes append to existing page cache`() {
        val firstStroke = stroke("stroke-1")
        val secondStroke = stroke("stroke-2", endX = 20f)

        val update = planInkRenderCacheUpdate(
            cachedStrokes = listOf(firstStroke),
            nextStrokes = listOf(firstStroke, secondStroke),
            sameSize = true,
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
            sameSize = true,
        )

        assertEquals(InkRenderCacheUpdate.Rebuilt, update)
    }

    @Test
    fun `changed vector stroke with same id rebuilds the page cache`() {
        val originalStroke = stroke("stroke-1")
        val movedStroke = stroke("stroke-1", startX = 5f, endX = 15f)

        val update = planInkRenderCacheUpdate(
            cachedStrokes = listOf(originalStroke),
            nextStrokes = listOf(movedStroke),
            sameSize = true,
        )

        assertEquals(InkRenderCacheUpdate.Rebuilt, update)
    }

    @Test
    fun `deleted strokes clear or rebuild the page cache instead of using stale bitmap`() {
        val firstStroke = stroke("stroke-1")
        val secondStroke = stroke("stroke-2")

        val update = planInkRenderCacheUpdate(
            cachedStrokes = listOf(firstStroke, secondStroke),
            nextStrokes = listOf(firstStroke),
            sameSize = true,
        )

        assertEquals(InkRenderCacheUpdate.Rebuilt, update)
        assertEquals(
            InkRenderCacheUpdate.Cleared,
            planInkRenderCacheUpdate(cachedStrokes = listOf(firstStroke), nextStrokes = emptyList(), sameSize = true),
        )
    }

    @Test
    fun `size changes rebuild page cache at page level`() {
        val committedStrokes = listOf(stroke("stroke-1"))

        val update = planInkRenderCacheUpdate(
            cachedStrokes = committedStrokes,
            nextStrokes = committedStrokes,
            sameSize = false,
        )

        assertEquals(InkRenderCacheUpdate.Rebuilt, update)
    }

    @Test
    fun `unchanged vectors keep the existing committed bitmap`() {
        val committedStrokes = listOf(stroke("stroke-1"))

        val update = planInkRenderCacheUpdate(
            cachedStrokes = committedStrokes,
            nextStrokes = committedStrokes,
            sameSize = true,
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
}

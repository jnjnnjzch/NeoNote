package com.example.cahier.features.drawing

import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.ImmutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StrokeIdMapperTest {
    private fun stroke() = Stroke(StockBrushes.markerLatest(), ImmutableStrokeInputBatch.EMPTY)

    @Test
    fun remapIds_preservesIdsForUnchangedOrder() {
        val s1 = stroke()
        val s2 = stroke()
        val out = StrokeIdMapper.remapIds(
            oldStrokes = listOf(s1, s2),
            newStrokes = listOf(s1, s2),
            oldIds = listOf("a", "b")
        )
        assertEquals(listOf("a", "b"), out)
    }

    @Test
    fun remapIds_preservesRemainingIdsAfterDelete() {
        val s1 = stroke()
        val s2 = stroke()
        val s3 = stroke()
        val out = StrokeIdMapper.remapIds(
            oldStrokes = listOf(s1, s2, s3),
            newStrokes = listOf(s1, s3),
            oldIds = listOf("a", "b", "c")
        )
        assertEquals(listOf("a", "c"), out)
    }

    @Test
    fun remapIds_assignsNewIdForNewStroke() {
        val s1 = stroke()
        val s2 = stroke()
        val inserted = stroke()
        val out = StrokeIdMapper.remapIds(
            oldStrokes = listOf(s1, s2),
            newStrokes = listOf(s1, inserted, s2),
            oldIds = listOf("a", "b")
        )
        assertEquals("a", out[0])
        assertEquals("b", out[2])
        assertNotEquals("a", out[1])
        assertNotEquals("b", out[1])
    }
}

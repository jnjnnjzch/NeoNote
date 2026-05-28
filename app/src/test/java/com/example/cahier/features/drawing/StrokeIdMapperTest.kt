package com.example.cahier.features.drawing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class StrokeIdMapperTest {
    @Test
    fun remapIds_preservesIdsForUnchangedOrder() {
        val s1 = "s1"
        val s2 = "s2"
        val out = StrokeIdMapper.remapIds(
            oldStrokes = listOf(s1, s2),
            newStrokes = listOf(s1, s2),
            oldIds = listOf("a", "b")
        )
        assertEquals(listOf("a", "b"), out)
    }

    @Test
    fun remapIds_preservesRemainingIdsAfterDelete() {
        val s1 = "s1"
        val s2 = "s2"
        val s3 = "s3"
        val out = StrokeIdMapper.remapIds(
            oldStrokes = listOf(s1, s2, s3),
            newStrokes = listOf(s1, s3),
            oldIds = listOf("a", "b", "c")
        )
        assertEquals(listOf("a", "c"), out)
    }

    @Test
    fun remapIds_assignsNewIdForNewStroke() {
        val s1 = "s1"
        val s2 = "s2"
        val inserted = "inserted"
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

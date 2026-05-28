package com.example.cahier.features.drawing

import org.junit.Assert.assertEquals
import org.junit.Test

class CanvasTransformMapperTest {

    @Test
    fun docToScreen_mapsWithScaleAndPan() {
        val transform = CanvasTransform(scale = 2f, panX = 10f, panY = -4f)
        assertEquals(30f, CanvasTransformMapper.docToScreenX(10f, transform))
        assertEquals(16f, CanvasTransformMapper.docToScreenY(10f, transform))
    }

    @Test
    fun screenToDocDelta_invertsScale() {
        val transform = CanvasTransform(scale = 4f, panX = 0f, panY = 0f)
        assertEquals(5f, CanvasTransformMapper.screenToDocDelta(20f, transform))
    }
}

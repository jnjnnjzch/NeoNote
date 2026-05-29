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

    @Test
    fun screenToCanvas_roundTripsCanvasCoordinates() {
        val transform = CanvasTransform(scale = 1.75f, panX = 32f, panY = -18f)
        val canvasX = 120f
        val canvasY = 88f

        val screenX = CanvasTransformMapper.canvasToScreenX(canvasX, transform)
        val screenY = CanvasTransformMapper.canvasToScreenY(canvasY, transform)

        assertEquals(canvasX, CanvasTransformMapper.screenToCanvasX(screenX, transform), 0.0001f)
        assertEquals(canvasY, CanvasTransformMapper.screenToCanvasY(screenY, transform), 0.0001f)
    }

    @Test
    fun repeatedScreenCanvasConversion_hasNoDrift() {
        val transform = CanvasTransform(scale = 2.2f, panX = -120f, panY = 44f)
        var canvasX = 413.5f
        var canvasY = -27.25f

        repeat(100) {
            val sx = CanvasTransformMapper.canvasToScreenX(canvasX, transform)
            val sy = CanvasTransformMapper.canvasToScreenY(canvasY, transform)
            canvasX = CanvasTransformMapper.screenToCanvasX(sx, transform)
            canvasY = CanvasTransformMapper.screenToCanvasY(sy, transform)
        }

        assertEquals(413.5f, canvasX, 0.0001f)
        assertEquals(-27.25f, canvasY, 0.0001f)
    }

    @Test
    fun screenToDocumentComposeMatrix_mapsPanZoomedInputIntoDocumentSpace() {
        val transform = CanvasTransform(scale = 2.5f, panX = -80f, panY = 45f)
        val documentX = 128f
        val documentY = -24f
        val screen = androidx.compose.ui.geometry.Offset(
            CanvasTransformMapper.docToScreenX(documentX, transform),
            CanvasTransformMapper.docToScreenY(documentY, transform)
        )

        val mapped = CanvasTransformMapper.screenToDocumentComposeMatrix(transform).map(screen)

        assertEquals(documentX, mapped.x, 0.0001f)
        assertEquals(documentY, mapped.y, 0.0001f)
    }
}

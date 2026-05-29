package com.neonote

import androidx.compose.ui.geometry.Offset
import com.neonote.model.CanvasPoint
import com.neonote.model.ViewportState
import kotlin.test.Test
import kotlin.test.assertEquals

class ViewportTransformsTest {
    @Test
    fun `round trip transforms preserve document point with pan and zoom`() {
        val viewport = ViewportState(panOffsetX = 80f, panOffsetY = -30f, zoomScale = 2.5f)
        val documentPoint = CanvasPoint(42f, -16f)

        val screenPoint = viewport.documentToScreen(documentPoint)
        val roundTripPoint = viewport.screenToDocument(screenPoint)

        assertEquals(documentPoint, roundTripPoint)
    }

    @Test
    fun `screen delta to document delta ignores pan and accounts for zoom`() {
        val viewport = ViewportState(panOffsetX = 500f, panOffsetY = -250f, zoomScale = 4f)

        val documentDelta = viewport.screenDeltaToDocumentDelta(Offset(20f, -12f))

        assertEquals(Offset(5f, -3f), documentDelta)
    }

    @Test
    fun `zoom around centroid keeps centroid document point stable`() {
        val viewport = ViewportState(panOffsetX = 30f, panOffsetY = -20f, zoomScale = 1.5f)
        val screenCentroid = CanvasPoint(150f, 100f)
        val documentAtCentroidBeforeZoom = viewport.screenToDocument(screenCentroid)

        val zoomed = viewport.zoomAroundScreenPoint(
            zoomChange = 2f,
            screenCentroid = screenCentroid,
            minZoomScale = 0.25f,
            maxZoomScale = 4f,
        )

        assertEquals(3f, zoomed.zoomScale)
        assertEquals(documentAtCentroidBeforeZoom, zoomed.screenToDocument(screenCentroid))
    }
}

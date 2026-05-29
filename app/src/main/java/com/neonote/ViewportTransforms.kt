package com.neonote

import androidx.compose.ui.geometry.Offset
import com.neonote.model.CanvasPoint
import com.neonote.model.ViewportState

/**
 * Pure transform helpers for the existing viewport model.
 *
 * Screen coordinates are Compose viewport pixels. Document coordinates are the
 * stable infinite-canvas units stored in canvas objects and ink. Keep all
 * screen/document conversions centralized here so input paths convert exactly
 * once at the controller boundary without introducing a second viewport model.
 */
public object ViewportTransforms {
    public fun screenToDocument(viewport: ViewportState, screenPosition: CanvasPoint): CanvasPoint = CanvasPoint(
        x = (screenPosition.x - viewport.panOffsetX) / viewport.zoomScale,
        y = (screenPosition.y - viewport.panOffsetY) / viewport.zoomScale,
    )

    public fun documentToScreen(viewport: ViewportState, documentPosition: CanvasPoint): CanvasPoint = CanvasPoint(
        x = documentPosition.x * viewport.zoomScale + viewport.panOffsetX,
        y = documentPosition.y * viewport.zoomScale + viewport.panOffsetY,
    )

    public fun screenDeltaToDocumentDelta(viewport: ViewportState, screenDelta: Offset): Offset = Offset(
        x = screenDelta.x / viewport.zoomScale,
        y = screenDelta.y / viewport.zoomScale,
    )

    public fun zoomAroundScreenPoint(
        viewport: ViewportState,
        zoomChange: Float,
        screenCentroid: CanvasPoint,
        minZoomScale: Float,
        maxZoomScale: Float,
    ): ViewportState {
        if (zoomChange == 1f) return viewport
        val oldZoom = viewport.zoomScale
        val newZoom = (oldZoom * zoomChange).coerceIn(minZoomScale, maxZoomScale)
        if (newZoom == oldZoom) return viewport

        val scaleChange = newZoom / oldZoom
        return viewport.copy(
            panOffsetX = screenCentroid.x - (screenCentroid.x - viewport.panOffsetX) * scaleChange,
            panOffsetY = screenCentroid.y - (screenCentroid.y - viewport.panOffsetY) * scaleChange,
            zoomScale = newZoom,
        )
    }
}

public fun ViewportState.screenToDocument(screenPosition: CanvasPoint): CanvasPoint =
    ViewportTransforms.screenToDocument(this, screenPosition)

public fun ViewportState.documentToScreen(documentPosition: CanvasPoint): CanvasPoint =
    ViewportTransforms.documentToScreen(this, documentPosition)

public fun ViewportState.screenDeltaToDocumentDelta(screenDelta: Offset): Offset =
    ViewportTransforms.screenDeltaToDocumentDelta(this, screenDelta)

public fun ViewportState.zoomAroundScreenPoint(
    zoomChange: Float,
    screenCentroid: CanvasPoint,
    minZoomScale: Float,
    maxZoomScale: Float,
): ViewportState = ViewportTransforms.zoomAroundScreenPoint(
    viewport = this,
    zoomChange = zoomChange,
    screenCentroid = screenCentroid,
    minZoomScale = minZoomScale,
    maxZoomScale = maxZoomScale,
)

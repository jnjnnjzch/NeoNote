package com.example.cahier.features.drawing

data class CanvasTransform(
    val scale: Float = 1.0f,
    val panX: Float = 0f,
    val panY: Float = 0f
)

object CanvasTransformMapper {
    fun canvasToScreenX(canvasX: Float, transform: CanvasTransform): Float {
        return canvasX * transform.scale + transform.panX
    }

    fun canvasToScreenY(canvasY: Float, transform: CanvasTransform): Float {
        return canvasY * transform.scale + transform.panY
    }

    fun screenToCanvasX(screenX: Float, transform: CanvasTransform): Float {
        return if (transform.scale == 0f) screenX else (screenX - transform.panX) / transform.scale
    }

    fun screenToCanvasY(screenY: Float, transform: CanvasTransform): Float {
        return if (transform.scale == 0f) screenY else (screenY - transform.panY) / transform.scale
    }

    fun docToScreenX(docX: Float, transform: CanvasTransform): Float {
        return canvasToScreenX(docX, transform)
    }

    fun docToScreenY(docY: Float, transform: CanvasTransform): Float {
        return canvasToScreenY(docY, transform)
    }

    fun screenToDocDelta(screenDelta: Float, transform: CanvasTransform): Float {
        return if (transform.scale == 0f) screenDelta else screenDelta / transform.scale
    }
}

package com.example.cahier.features.drawing

data class CanvasTransform(
    val scale: Float = 1.0f,
    val panX: Float = 0f,
    val panY: Float = 0f
)

object CanvasTransformMapper {
    fun docToScreenX(docX: Float, transform: CanvasTransform): Float {
        return docX * transform.scale + transform.panX
    }

    fun docToScreenY(docY: Float, transform: CanvasTransform): Float {
        return docY * transform.scale + transform.panY
    }

    fun screenToDocDelta(screenDelta: Float, transform: CanvasTransform): Float {
        return if (transform.scale == 0f) screenDelta else screenDelta / transform.scale
    }
}

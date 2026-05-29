package com.example.cahier.features.drawing

data class GesturePoint(
    val x: Float,
    val y: Float
)

object CanvasTransformGesture {
    fun applyOneFingerPan(
        current: CanvasTransform,
        dxScreen: Float,
        dyScreen: Float
    ): CanvasTransform {
        return current.copy(
            panX = current.panX + dxScreen,
            panY = current.panY + dyScreen
        )
    }

    fun applyTwoFingerPinchPan(
        current: CanvasTransform,
        previousCentroid: GesturePoint,
        currentCentroid: GesturePoint,
        previousDistance: Float,
        currentDistance: Float,
        minScale: Float = 0.5f,
        maxScale: Float = 4.0f
    ): CanvasTransform {
        if (previousDistance <= 0f || currentDistance <= 0f) return current
        val ratio = (currentDistance / previousDistance).coerceIn(0.5f, 2.0f)
        val newScale = (current.scale * ratio).coerceIn(minScale, maxScale)

        // Keep the same document-space point under the moving centroid.
        val docX = CanvasTransformMapper.screenToDocX(previousCentroid.x, current)
        val docY = CanvasTransformMapper.screenToDocY(previousCentroid.y, current)
        val newPanX = currentCentroid.x - docX * newScale
        val newPanY = currentCentroid.y - docY * newScale

        return current.copy(scale = newScale, panX = newPanX, panY = newPanY)
    }
}


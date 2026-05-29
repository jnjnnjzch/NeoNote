package com.example.cahier.features.drawing

import android.graphics.Matrix as AndroidMatrix
import androidx.compose.ui.graphics.Matrix as ComposeMatrix

data class CanvasTransform(
    val scale: Float = 1.0f,
    val panX: Float = 0f,
    val panY: Float = 0f
)

/**
 * The single conversion gateway between NeoNote screen coordinates and document/canvas space.
 *
 * Stored ink strokes and document blocks live in document/canvas space. Rendering and pointer
 * authoring must derive all screen-space transforms from this mapper so pan/zoom math stays
 * consistent across dry strokes, wet Jetpack Ink strokes, hit testing, erasing, and debug UI.
 */
object CanvasTransformMapper {
    fun canvasToScreenX(canvasX: Float, transform: CanvasTransform): Float {
        return canvasX * safeScale(transform) + transform.panX
    }

    fun canvasToScreenY(canvasY: Float, transform: CanvasTransform): Float {
        return canvasY * safeScale(transform) + transform.panY
    }

    fun screenToCanvasX(screenX: Float, transform: CanvasTransform): Float {
        val scale = safeScale(transform)
        return (screenX - transform.panX) / scale
    }

    fun screenToCanvasY(screenY: Float, transform: CanvasTransform): Float {
        val scale = safeScale(transform)
        return (screenY - transform.panY) / scale
    }

    fun docToScreenX(docX: Float, transform: CanvasTransform): Float {
        return canvasToScreenX(docX, transform)
    }

    fun docToScreenY(docY: Float, transform: CanvasTransform): Float {
        return canvasToScreenY(docY, transform)
    }

    fun screenToDocX(screenX: Float, transform: CanvasTransform): Float {
        return screenToCanvasX(screenX, transform)
    }

    fun screenToDocY(screenY: Float, transform: CanvasTransform): Float {
        return screenToCanvasY(screenY, transform)
    }

    fun screenToDocDelta(screenDelta: Float, transform: CanvasTransform): Float {
        return screenDelta / safeScale(transform)
    }

    fun documentToScreenAndroidMatrix(
        transform: CanvasTransform,
        documentTranslationX: Float = 0f,
        documentTranslationY: Float = 0f,
    ): AndroidMatrix {
        return AndroidMatrix().apply {
            postTranslate(documentTranslationX, documentTranslationY)
            postScale(safeScale(transform), safeScale(transform))
            postTranslate(transform.panX, transform.panY)
        }
    }

    fun screenToDocumentComposeMatrix(transform: CanvasTransform): ComposeMatrix {
        val scale = safeScale(transform)
        val inverseScale = 1f / scale
        return ComposeMatrix().apply {
            values[ComposeMatrix.ScaleX] = inverseScale
            values[ComposeMatrix.ScaleY] = inverseScale
            values[ComposeMatrix.TranslateX] = -transform.panX * inverseScale
            values[ComposeMatrix.TranslateY] = -transform.panY * inverseScale
        }
    }

    fun identityComposeMatrix(): ComposeMatrix = ComposeMatrix()

    private fun safeScale(transform: CanvasTransform): Float {
        return if (transform.scale == 0f) 1f else transform.scale
    }
}

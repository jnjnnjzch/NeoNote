package com.example.cahier.features.drawing

import android.view.MotionEvent
import com.example.cahier.core.document.TextContainerBlock
import com.example.cahier.core.ui.DrawingInputRoute
import com.example.cahier.core.ui.DrawingInputRouter
import com.example.cahier.core.ui.DrawingInputRoutingConfig
import kotlin.math.hypot

data class FingerTapThresholds(
    val touchSlop: Float,
    val maxDurationMillis: Long
)

class FingerTapGestureTracker(
    private val thresholds: FingerTapThresholds
) {
    private var downX = 0f
    private var downY = 0f
    private var downTimeMillis = 0L
    private var candidate = false

    fun onDown(x: Float, y: Float, eventTimeMillis: Long) {
        downX = x
        downY = y
        downTimeMillis = eventTimeMillis
        candidate = true
    }

    fun onMove(x: Float, y: Float) {
        if (hypot(x - downX, y - downY) > thresholds.touchSlop) {
            candidate = false
        }
    }

    fun onUp(x: Float, y: Float, eventTimeMillis: Long): Boolean {
        val moved = hypot(x - downX, y - downY)
        val duration = eventTimeMillis - downTimeMillis
        val shouldTap = candidate && moved <= thresholds.touchSlop && duration <= thresholds.maxDurationMillis
        candidate = false
        return shouldTap
    }
}

sealed interface FingerTapTextTarget {
    data object FocusExisting : FingerTapTextTarget
    data class PlaceAt(val docX: Float, val docY: Float) : FingerTapTextTarget
}

fun resolveFingerTapTextTarget(
    sx: Float,
    sy: Float,
    textContainer: TextContainerBlock?,
    canvasTransform: CanvasTransform
): FingerTapTextTarget {
    if (textContainer != null) {
        val left = (textContainer.x * canvasTransform.scale) + canvasTransform.panX
        val top = (textContainer.y * canvasTransform.scale) + canvasTransform.panY
        val right = left + (textContainer.width * canvasTransform.scale)
        val bottom = top + (textContainer.height * canvasTransform.scale)
        if (sx >= left && sx < right && sy >= top && sy < bottom) {
            return FingerTapTextTarget.FocusExisting
        }
    }
    val docX = (sx - canvasTransform.panX) / canvasTransform.scale
    val docY = (sy - canvasTransform.panY) / canvasTransform.scale
    return FingerTapTextTarget.PlaceAt(docX = docX, docY = docY)
}

fun routeForDrawingCanvasFingerEvent(
    event: MotionEvent,
    config: DrawingInputRoutingConfig
): DrawingInputRoute {
    return DrawingInputRouter.routeFor(event, config)
}

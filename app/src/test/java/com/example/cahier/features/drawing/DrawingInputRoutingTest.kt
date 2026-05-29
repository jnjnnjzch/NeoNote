package com.example.cahier.features.drawing

import android.view.MotionEvent
import com.example.cahier.core.document.TextContainerBlock
import com.example.cahier.core.ui.DrawingInputRoute
import com.example.cahier.core.ui.DrawingInputRoutingConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DrawingInputRoutingTest {

    @Test
    fun fingerDrag_doesNotCreateOrMoveTextContainer() {
        val tracker = FingerTapGestureTracker(FingerTapThresholds(touchSlop = 8f, maxDurationMillis = 200L))
        val existing = TextContainerBlock(x = 100f, y = 100f, width = 300f, height = 200f)

        tracker.onDown(x = 20f, y = 20f, eventTimeMillis = 0L)
        tracker.onMove(x = 80f, y = 20f)
        val shouldTap = tracker.onUp(x = 80f, y = 20f, eventTimeMillis = 80L)

        assertFalse(shouldTap)
        assertEquals(100f, existing.x, 0.0001f)
        assertEquals(100f, existing.y, 0.0001f)
    }

    @Test
    fun fingerTap_focusesExistingTextContainer_whenTapHitsContainer() {
        val tracker = FingerTapGestureTracker(FingerTapThresholds(touchSlop = 8f, maxDurationMillis = 200L))
        val existing = TextContainerBlock(x = 100f, y = 100f, width = 300f, height = 200f)

        tracker.onDown(x = 120f, y = 130f, eventTimeMillis = 0L)
        assertTrue(tracker.onUp(x = 122f, y = 131f, eventTimeMillis = 80L))

        val target = resolveFingerTapTextTarget(
            sx = 122f,
            sy = 131f,
            textContainer = existing,
            canvasTransform = CanvasTransform()
        )

        assertEquals(FingerTapTextTarget.FocusExisting, target)
        assertEquals(100f, existing.x, 0.0001f)
        assertEquals(100f, existing.y, 0.0001f)
    }

    @Test
    fun fingerTap_placesTextContainer_whenTapMissesContainer() {
        val tracker = FingerTapGestureTracker(FingerTapThresholds(touchSlop = 8f, maxDurationMillis = 200L))

        tracker.onDown(x = 420f, y = 330f, eventTimeMillis = 0L)
        assertTrue(tracker.onUp(x = 421f, y = 331f, eventTimeMillis = 80L))

        val target = resolveFingerTapTextTarget(
            sx = 421f,
            sy = 331f,
            textContainer = TextContainerBlock(x = 100f, y = 100f, width = 100f, height = 100f),
            canvasTransform = CanvasTransform(scale = 2f, panX = 21f, panY = 31f)
        )

        val placeAt = target as FingerTapTextTarget.PlaceAt
        assertEquals(200f, placeAt.docX, 0.0001f)
        assertEquals(150f, placeAt.docY, 0.0001f)
    }

    @Test
    fun stylusDown_entersInkRoute_whenDocumentSettingsAllowStylusWriting() {
        val event = motionEvent(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_STYLUS)

        val route = routeForDrawingCanvasFingerEvent(
            event = event,
            config = DrawingInputRoutingConfig(
                stylusWritesByDefault = true,
                fingerPansByDefault = true
            )
        )

        assertEquals(DrawingInputRoute.StylusInk, route)
        event.recycle()
    }

    @Test
    fun fingerDown_entersPanZoomRoute_whenDocumentSettingsAllowFingerPanning() {
        val event = motionEvent(MotionEvent.ACTION_DOWN, MotionEvent.TOOL_TYPE_FINGER)

        val route = routeForDrawingCanvasFingerEvent(
            event = event,
            config = DrawingInputRoutingConfig(
                stylusWritesByDefault = true,
                fingerPansByDefault = true
            )
        )

        assertEquals(DrawingInputRoute.FingerPanZoom, route)
        event.recycle()
    }

    private fun motionEvent(action: Int, toolType: Int): MotionEvent {
        return MotionEvent.obtain(0L, 0L, action, 10f, 10f, 0).apply {
            setToolType(0, toolType)
        }
    }
}

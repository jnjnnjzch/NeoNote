package com.example.cahier.features.drawing

import com.example.cahier.core.document.TextContainerBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class CanvasTransformGestureTest {

    @Test
    fun pinchZoom_changes_canvas_transform_not_textcontainer_dimensions() {
        val block = TextContainerBlock(
            x = 100f,
            y = 100f,
            width = 500f,
            height = 300f
        )
        val before = CanvasTransform(scale = 1f, panX = 0f, panY = 0f)

        val after = CanvasTransformGesture.applyTwoFingerPinchPan(
            current = before,
            previousCentroid = GesturePoint(300f, 300f),
            currentCentroid = GesturePoint(320f, 320f),
            previousDistance = 100f,
            currentDistance = 180f
        )

        assertNotEquals(before.scale, after.scale)
        assertNotEquals(before.panX, after.panX)
        assertNotEquals(before.panY, after.panY)

        assertEquals(500f, block.width, 0.0001f)
        assertEquals(300f, block.height, 0.0001f)
        assertEquals(100f, block.x, 0.0001f)
        assertEquals(100f, block.y, 0.0001f)
    }

    @Test
    fun oneFingerPan_changes_canvas_offset_not_object_position() {
        val block = TextContainerBlock(x = 100f, y = 100f)
        val before = CanvasTransform(scale = 1.5f, panX = 20f, panY = 30f)

        val after = CanvasTransformGesture.applyOneFingerPan(before, 40f, -15f)

        assertEquals(1.5f, after.scale, 0.0001f)
        assertEquals(60f, after.panX, 0.0001f)
        assertEquals(15f, after.panY, 0.0001f)
        assertEquals(100f, block.x, 0.0001f)
        assertEquals(100f, block.y, 0.0001f)
    }
}


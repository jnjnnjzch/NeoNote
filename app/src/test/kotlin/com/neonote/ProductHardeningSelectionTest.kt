package com.neonote

import com.neonote.engine.SelectionEngine
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasSize
import com.neonote.model.FloatingImage
import com.neonote.model.InfiniteCanvas
import com.neonote.model.RichContentBox
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProductHardeningSelectionTest {
    @Test
    fun `vertical manual resize disables content driven height but width-only resize keeps it`() {
        val engine = SelectionEngine()
        val box = RichContentBox(
            id = "box",
            position = CanvasPoint(10f, 10f),
            size = CanvasSize(200f, 100f),
            autoSizeHeight = true,
        )
        val canvas = InfiniteCanvas(objects = listOf(box))
        val selection = engine.select(canvas, objectIds = setOf("box"))

        val widthOnly = engine.scaleSelection(canvas, selection, 1.5f, 1f, CanvasPoint(10f, 10f))
        assertTrue((widthOnly.objects.single() as RichContentBox).autoSizeHeight)

        val both = engine.scaleSelection(canvas, selection, 1.5f, 1.5f, CanvasPoint(10f, 10f))
        assertFalse((both.objects.single() as RichContentBox).autoSizeHeight)
    }

    @Test
    fun `mixed selection rotation updates object rotations and positions`() {
        val engine = SelectionEngine()
        val canvas = InfiniteCanvas(objects = listOf(
            RichContentBox("box", CanvasPoint(0f, 0f), CanvasSize(100f, 50f)),
            FloatingImage("image", CanvasPoint(200f, 0f), CanvasSize(100f, 50f), assetId = "asset"),
        ))
        val selection = engine.select(canvas, objectIds = setOf("box", "image"))

        val rotated = engine.rotateSelection(canvas, selection, 90f)
        val box = rotated.objects.filterIsInstance<RichContentBox>().single()
        val image = rotated.objects.filterIsInstance<FloatingImage>().single()

        assertEquals(90f, box.rotationDegrees)
        assertEquals(90f, image.rotationDegrees)
        assertTrue(abs(box.position.x - image.position.x) < 1f)
        assertTrue(box.position.y < image.position.y)
    }

}

package com.neonote

import com.neonote.engine.SelectionEngine
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasRect
import com.neonote.model.CanvasSize
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkLayer
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.SelectionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SelectionEngineTest {
    @Test
    fun `selection can include rich content boxes and ink strokes and move both`() {
        val canvas = InfiniteCanvas(
            objects = listOf(
                RichContentBox(id = "box-1", content = RichContent(), position = CanvasPoint(10f, 20f)),
            ),
            inkLayer = InkLayer(
                strokes = listOf(
                    InkStroke(
                        id = "stroke-1",
                        points = listOf(InkPoint(1f, 2f), InkPoint(3f, 4f)),
                    ),
                ),
            ),
        )
        val engine = SelectionEngine()

        val selection = engine.select(canvas, objectIds = setOf("box-1"), strokeIds = setOf("stroke-1"))
        val movedCanvas = engine.moveSelection(canvas, selection, dx = 5f, dy = -2f)

        assertTrue(selection.isObjectSelected("box-1"))
        assertTrue(selection.isStrokeSelected("stroke-1"))
        assertEquals(CanvasPoint(15f, 18f), (movedCanvas.objects.single() as RichContentBox).position)
        assertEquals(
            listOf(InkPoint(6f, 0f), InkPoint(8f, 2f)),
            movedCanvas.inkLayer.strokes.single().points,
        )
    }

    @Test
    fun `object hit-test uses canvas object bounds`() {
        val engine = SelectionEngine()
        val box = RichContentBox(
            id = "box-1",
            position = CanvasPoint(10f, 20f),
            size = CanvasSize(100f, 50f),
        )

        assertTrue(engine.hitTestCanvasObject(box, CanvasPoint(10f, 20f)))
        assertTrue(engine.hitTestCanvasObject(box, CanvasPoint(110f, 70f)))
        assertFalse(engine.hitTestCanvasObject(box, CanvasPoint(111f, 70f)))
    }

    @Test
    fun `stroke hit-test uses point and segment proximity`() {
        val engine = SelectionEngine()
        val stroke = InkStroke(
            id = "stroke-1",
            points = listOf(InkPoint(0f, 0f), InkPoint(100f, 0f), InkPoint(100f, 100f)),
        )

        assertTrue(engine.hitTestInkStroke(stroke, CanvasPoint(50f, 3f), tolerance = 4f))
        assertTrue(engine.hitTestInkStroke(stroke, CanvasPoint(103f, 50f), tolerance = 4f))
        assertFalse(engine.hitTestInkStroke(stroke, CanvasPoint(50f, 8f), tolerance = 4f))
    }

    @Test
    fun `lasso selection returns mixed canvas object and ink stroke refs`() {
        val engine = SelectionEngine()
        val canvas = InfiniteCanvas(
            objects = listOf(
                RichContentBox(
                    id = "inside-box",
                    position = CanvasPoint(20f, 20f),
                    size = CanvasSize(20f, 20f),
                ),
                RichContentBox(
                    id = "outside-box",
                    position = CanvasPoint(140f, 140f),
                    size = CanvasSize(20f, 20f),
                ),
            ),
            inkLayer = InkLayer(
                strokes = listOf(
                    InkStroke(
                        id = "crossing-stroke",
                        points = listOf(InkPoint(5f, 55f), InkPoint(80f, 55f)),
                    ),
                    InkStroke(
                        id = "outside-stroke",
                        points = listOf(InkPoint(140f, 10f), InkPoint(180f, 10f)),
                    ),
                ),
            ),
        )
        val lasso = listOf(
            CanvasPoint(0f, 0f),
            CanvasPoint(100f, 0f),
            CanvasPoint(100f, 100f),
            CanvasPoint(0f, 100f),
        )

        val selection = engine.selectWithLasso(canvas, lasso)

        assertEquals(setOf("inside-box"), selection.selectedObjectIds)
        assertEquals(setOf("crossing-stroke"), selection.selectedStrokeIds)
    }
    @Test
    fun `selected bounds union selected objects and ink stroke point extents`() {
        val engine = SelectionEngine()
        val canvas = InfiniteCanvas(
            objects = listOf(
                RichContentBox(
                    id = "box-1",
                    position = CanvasPoint(10f, 20f),
                    size = CanvasSize(30f, 40f),
                ),
                RichContentBox(
                    id = "box-2",
                    position = CanvasPoint(-100f, -100f),
                    size = CanvasSize(10f, 10f),
                ),
            ),
            inkLayer = InkLayer(
                strokes = listOf(
                    InkStroke(
                        id = "stroke-1",
                        points = listOf(InkPoint(5f, 75f), InkPoint(70f, 15f), InkPoint(45f, 90f)),
                    ),
                    InkStroke(
                        id = "stroke-2",
                        points = listOf(InkPoint(-50f, -50f), InkPoint(-40f, -40f)),
                    ),
                ),
            ),
        )
        val selection = engine.select(canvas, objectIds = setOf("box-1"), strokeIds = setOf("stroke-1"))

        val bounds = engine.selectedBounds(canvas, selection)

        assertEquals(CanvasRect(left = 5f, top = 15f, right = 70f, bottom = 90f), bounds)
    }

    @Test
    fun `selected bounds are null for empty selection and empty selected strokes`() {
        val engine = SelectionEngine()
        val canvas = InfiniteCanvas(
            inkLayer = InkLayer(strokes = listOf(InkStroke(id = "empty-stroke"))),
        )
        val selection = engine.select(canvas, strokeIds = setOf("empty-stroke"))

        assertEquals(null, engine.selectedBounds(canvas, SelectionState()))
        assertEquals(null, engine.selectedBounds(canvas, selection))
    }
}

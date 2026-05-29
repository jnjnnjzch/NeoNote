package com.neonote

import com.neonote.engine.SelectionEngine
import com.neonote.model.CanvasPoint
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkLayer
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import kotlin.test.Test
import kotlin.test.assertEquals
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
}

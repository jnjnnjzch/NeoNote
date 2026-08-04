package com.neonote

import com.neonote.engine.IdGenerator
import com.neonote.engine.InkCommand
import com.neonote.engine.InkCommandResult
import com.neonote.engine.InkEngine
import com.neonote.engine.InkSession
import com.neonote.engine.SelectionEngine
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasSize
import com.neonote.model.FloatingImage
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkBrush
import com.neonote.model.InkLayer
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import com.neonote.model.InkStrokeStyle
import com.neonote.model.RichContentBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CompleteInkAndSelectionEngineTest {
    private class TestIds : IdGenerator {
        private var next = 0
        override fun nextId(prefix: String): String = "$prefix-${next++}"
    }

    @Test
    fun `stroke persists selected brush style and pressure policy`() {
        val engine = InkEngine(TestIds())
        val style = InkStrokeStyle(
            colorArgb = 0xFF0066CC.toInt(),
            baseWidth = 14f,
            opacity = 0.35f,
            pressureEnabled = false,
            brush = InkBrush.Highlighter,
        )
        var session = (engine.execute(
            InkSession(),
            InkCommand.BeginStroke(InkPoint(0f, 0f, pressure = 0.2f), style = style),
        ) as InkCommandResult.StrokeBegun).session
        session = (engine.execute(session, InkCommand.AppendPoint(InkPoint(10f, 0f, pressure = 0.8f)))
            as InkCommandResult.PointAppended).session
        val ended = engine.execute(session, InkCommand.EndStroke) as InkCommandResult.StrokeEnded

        assertEquals(style, ended.stroke.style)
        assertTrue(ended.stroke.points.all { it.pressure == 1f })
    }

    @Test
    fun `segment eraser splits touched stroke while preserving style`() {
        val ids = TestIds()
        val engine = InkEngine(ids)
        val style = InkStrokeStyle(colorArgb = 123, baseWidth = 5f)
        val stroke = InkStroke(
            id = "source",
            points = (0..10).map { InkPoint(it.toFloat() * 10f, 0f) },
            style = style,
        )
        val result = engine.execute(
            InkSession(InkLayer(listOf(stroke))),
            InkCommand.EraseSegments(listOf(InkPoint(50f, 0f)), radius = 11f),
        ) as InkCommandResult.InkErased

        assertEquals(setOf("source"), result.changedStrokeIds)
        assertEquals(2, result.session.inkLayer.strokes.size)
        assertTrue(result.session.inkLayer.strokes.all { it.style == style })
        assertTrue(result.session.inkLayer.strokes.none { candidate -> candidate.points.any { it.x in 40f..60f } })
    }

    @Test
    fun `mixed selection duplicates and scales text image and ink together`() {
        val selectionEngine = SelectionEngine()
        val canvas = InfiniteCanvas(
            objects = listOf(
                RichContentBox(id = "text", position = CanvasPoint(10f, 10f), size = CanvasSize(100f, 50f)),
                FloatingImage(id = "image", position = CanvasPoint(150f, 10f), size = CanvasSize(80f, 60f), assetId = "asset"),
            ),
            inkLayer = InkLayer(listOf(InkStroke("stroke", listOf(InkPoint(20f, 100f), InkPoint(40f, 120f))))),
        )
        val selection = selectionEngine.select(canvas, setOf("text", "image"), setOf("stroke"))
        val duplicate = selectionEngine.duplicateSelection(canvas, selection, TestIds(), offsetX = 20f, offsetY = 30f)

        assertEquals(4, duplicate.canvas.objects.size)
        assertEquals(2, duplicate.canvas.inkLayer.strokes.size)
        assertEquals(3, duplicate.selection.selectedRefs.size)

        val before = requireNotNull(selectionEngine.selectedBounds(duplicate.canvas, duplicate.selection))
        val scaled = selectionEngine.scaleSelection(duplicate.canvas, duplicate.selection, 2f, 2f, before.center)
        val after = requireNotNull(selectionEngine.selectedBounds(scaled, duplicate.selection))
        assertEquals(before.width * 2f, after.width)
        assertEquals(before.height * 2f, after.height)
    }

    @Test
    fun `locked canvas object ignores geometry transform`() {
        val engine = SelectionEngine()
        val locked = FloatingImage(
            id = "locked",
            position = CanvasPoint(20f, 20f),
            size = CanvasSize(100f, 80f),
            assetId = "asset",
            isLocked = true,
        )
        val canvas = InfiniteCanvas(objects = listOf(locked))
        val selection = engine.select(canvas, objectIds = setOf("locked"))
        val moved = engine.moveSelection(canvas, selection, 100f, 100f)

        assertEquals(locked, moved.objects.single())
        assertFalse(moved.objects.single().position == CanvasPoint(120f, 120f))
    }
}

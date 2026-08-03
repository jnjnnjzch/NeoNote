package com.neonote

import com.neonote.engine.InputAction
import com.neonote.engine.InputEvent
import com.neonote.engine.InputMode
import com.neonote.engine.InputPointer
import com.neonote.engine.InputRouter
import com.neonote.engine.PointerEventType
import com.neonote.engine.PointerTool
import com.neonote.model.CanvasPoint
import com.neonote.model.EditorTool
import com.neonote.model.InfiniteCanvas
import com.neonote.model.RichContentBox
import com.neonote.model.ViewportState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ProductInteractionTest {
    @Test
    fun `pen tool keeps finger taps in navigation instead of creating text`() {
        val router = InputRouter()
        val canvas = InfiniteCanvas()
        val point = CanvasPoint(32f, 64f)

        router.route(
            canvas,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = point, tool = PointerTool.Finger)),
            ),
            mode = InputMode.Navigate,
        )
        val result = router.route(
            canvas,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = point, tool = PointerTool.Finger)),
            ),
            mode = InputMode.Navigate,
        )

        assertIs<InputAction.Ignored>(result.action)
        assertTrue(result.canvas.objects.isEmpty())
    }

    @Test
    fun `physical s pen eraser always routes to erase`() {
        val action = InputRouter().route(
            canvas = InfiniteCanvas(),
            event = InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(
                    InputPointer(
                        id = 3,
                        position = CanvasPoint(40f, 50f),
                        tool = PointerTool.Eraser,
                    ),
                ),
            ),
            mode = InputMode.Write,
        ).action

        val erase = assertIs<InputAction.EraseAt>(action)
        assertEquals(listOf(CanvasPoint(40f, 50f)), erase.positions)
    }

    @Test
    fun `eraser tool turns s pen movement into erase`() {
        val action = InputRouter().route(
            canvas = InfiniteCanvas(),
            event = InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(
                    InputPointer(
                        id = 7,
                        position = CanvasPoint(14f, 18f),
                        tool = PointerTool.SPen,
                    ),
                ),
            ),
            mode = InputMode.Erase,
        ).action

        assertIs<InputAction.EraseAt>(action)
    }

    @Test
    fun `eraser tool still lets a finger pan`() {
        val router = InputRouter(tapSlop = 4f)
        val canvas = InfiniteCanvas()
        router.route(
            canvas,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(0f, 0f), tool = PointerTool.Finger)),
            ),
            mode = InputMode.Erase,
        )

        val action = router.route(
            canvas,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(12f, 8f), tool = PointerTool.Finger)),
            ),
            mode = InputMode.Erase,
        ).action

        val pan = assertIs<InputAction.PanBy>(action)
        assertEquals(12f, pan.dx)
        assertEquals(8f, pan.dy)
    }

    @Test
    fun `switching away from text commits focus and clears selection`() {
        val controller = NeoNoteEditorController()
        controller.setTool(EditorTool.Text)
        controller.focusOrCreateRichContentBox(CanvasPoint(50f, 60f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.selectCanvasObject(boxId)

        controller.setTool(EditorTool.Pen)

        assertEquals(EditorTool.Pen, controller.state.currentTool)
        assertNull(controller.state.focusedRichContentBoxId)
        assertTrue(controller.state.selection.selectedRefs.isEmpty())
        assertTrue((controller.currentCanvas.objects.single() as RichContentBox).isFocused.not())
    }

    @Test
    fun `delete selection removes selected objects and supports undo`() {
        val controller = NeoNoteEditorController()
        controller.setTool(EditorTool.Text)
        controller.focusOrCreateRichContentBox(CanvasPoint(50f, 60f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.setTool(EditorTool.Selection)
        controller.selectCanvasObject(boxId)

        controller.deleteSelection()

        assertTrue(controller.currentCanvas.objects.isEmpty())
        assertTrue(controller.state.selection.selectedRefs.isEmpty())
        assertTrue(controller.canUndo)

        controller.undo()

        assertEquals(boxId, (controller.currentCanvas.objects.single() as RichContentBox).id)
    }

    @Test
    fun `reset viewport returns to a predictable one hundred percent view`() {
        val controller = NeoNoteEditorController()
        controller.panViewportBy(dx = 140f, dy = -80f)
        controller.zoomViewportBy(2f, CanvasPoint(100f, 100f))

        controller.resetViewport()

        assertEquals(ViewportState(), controller.state.viewport)
    }

    @Test
    fun `eraser removes a hit stroke and the operation can be undone`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, CanvasPoint(20f, 20f), PointerTool.SPen, pressure = 0.5f)),
            ),
            mode = InputMode.Navigate,
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(1, CanvasPoint(50f, 50f), PointerTool.SPen, pressure = 0.8f)),
            ),
            mode = InputMode.Navigate,
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, CanvasPoint(50f, 50f), PointerTool.SPen, pressure = 0.8f)),
            ),
            mode = InputMode.Navigate,
        )
        assertEquals(1, controller.currentCanvas.inkLayer.strokes.size)

        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(2, CanvasPoint(35f, 35f), PointerTool.SPen)),
            ),
            mode = InputMode.Erase,
        )

        assertTrue(controller.currentCanvas.inkLayer.strokes.isEmpty())
        controller.undo()
        assertEquals(1, controller.currentCanvas.inkLayer.strokes.size)
    }
}

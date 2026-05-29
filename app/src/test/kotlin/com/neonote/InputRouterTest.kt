package com.neonote

import com.neonote.engine.InputAction
import com.neonote.engine.InputEvent
import com.neonote.engine.InputMode
import com.neonote.engine.InputPointer
import com.neonote.engine.InputRouter
import com.neonote.engine.PointerEventType
import com.neonote.engine.PointerTool
import com.neonote.model.CanvasPoint
import com.neonote.model.InfiniteCanvas
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InputRouterTest {
    @Test
    fun `s pen events route to ink`() {
        val router = InputRouter()
        val canvas = InfiniteCanvas()
        val event = InputEvent(
            type = PointerEventType.Move,
            pointers = listOf(InputPointer(id = 1, position = CanvasPoint(4f, 5f), tool = PointerTool.SPen, pressure = 0.75f)),
        )

        val result = router.route(canvas, event)

        val action = assertIs<InputAction.ContinueInk>(result.action)
        assertEquals(CanvasPoint(4f, 5f), action.position)
        assertEquals(0.75f, action.pressure)
    }

    @Test
    fun `finger tap blank routes create rich content box intent without mutating canvas`() {
        val router = InputRouter()
        val canvas = InfiniteCanvas()

        router.route(
            canvas,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(32f, 64f), tool = PointerTool.Finger)),
            ),
        )
        val result = router.route(
            canvas,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(32f, 64f), tool = PointerTool.Finger)),
            ),
        )

        val action = assertIs<InputAction.CreateOrFocusRichContentBox>(result.action)
        assertEquals(CanvasPoint(32f, 64f), action.position)
        assertTrue(result.canvas.objects.isEmpty())
    }

    @Test
    fun `finger drag beyond threshold routes to pan not tap`() {
        val router = InputRouter(tapSlop = 8f)
        val canvas = InfiniteCanvas()

        router.route(
            canvas,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(0f, 0f), tool = PointerTool.Finger)),
            ),
        )
        val moveResult = router.route(
            canvas,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(12f, 0f), tool = PointerTool.Finger)),
            ),
        )
        val upResult = router.route(
            canvas,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(12f, 0f), tool = PointerTool.Finger)),
            ),
        )

        val pan = assertIs<InputAction.PanBy>(moveResult.action)
        assertEquals(12f, pan.dx)
        assertEquals(0f, pan.dy)
        assertIs<InputAction.EndInteraction>(upResult.action)
        assertTrue(upResult.canvas.objects.isEmpty())
    }

    @Test
    fun `two finger gesture routes to zoom`() {
        val router = InputRouter()
        val canvas = InfiniteCanvas()
        val event = InputEvent(
            type = PointerEventType.Move,
            pointers = listOf(
                InputPointer(id = 1, position = CanvasPoint(0f, 0f), tool = PointerTool.Finger),
                InputPointer(id = 2, position = CanvasPoint(10f, 10f), tool = PointerTool.Finger),
            ),
        )

        val result = router.route(canvas, event)

        val zoom = assertIs<InputAction.Zoom>(result.action)
        assertEquals(CanvasPoint(5f, 5f), zoom.centroid)
    }

    @Test
    fun `selection mode routes gestures to selection placeholders`() {
        val router = InputRouter()
        val canvas = InfiniteCanvas()
        val event = InputEvent(
            type = PointerEventType.Down,
            pointers = listOf(InputPointer(id = 1, position = CanvasPoint(3f, 4f), tool = PointerTool.Finger)),
        )

        val result = router.route(canvas, event, mode = InputMode.Selection)

        val action = assertIs<InputAction.BeginSelectionGesture>(result.action)
        assertEquals(CanvasPoint(3f, 4f), action.position)
    }
}

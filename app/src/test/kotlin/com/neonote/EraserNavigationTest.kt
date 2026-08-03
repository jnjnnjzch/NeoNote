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

class EraserNavigationTest {
    @Test
    fun `finger tap on content stays passive in eraser mode`() {
        val router = InputRouter()
        val canvas = InfiniteCanvas()
        val point = CanvasPoint(120f, 140f)

        router.route(
            canvas,
            InputEvent(
                PointerEventType.Down,
                listOf(InputPointer(1, point, PointerTool.Finger)),
                targetObjectId = "content",
            ),
            InputMode.Erase,
        )
        val result = router.route(
            canvas,
            InputEvent(
                PointerEventType.Up,
                listOf(InputPointer(1, point, PointerTool.Finger)),
                targetObjectId = "content",
            ),
            InputMode.Erase,
        )

        assertIs<InputAction.Ignored>(result.action)
    }

    @Test
    fun `finger drag across content still pans in eraser mode`() {
        val router = InputRouter(tapSlop = 4f)
        val canvas = InfiniteCanvas()

        router.route(
            canvas,
            InputEvent(
                PointerEventType.Down,
                listOf(InputPointer(1, CanvasPoint(10f, 10f), PointerTool.Finger)),
                targetObjectId = "content",
            ),
            InputMode.Erase,
        )
        val result = router.route(
            canvas,
            InputEvent(
                PointerEventType.Move,
                listOf(InputPointer(1, CanvasPoint(30f, 25f), PointerTool.Finger)),
                targetObjectId = "content",
            ),
            InputMode.Erase,
        )

        val pan = assertIs<InputAction.PanBy>(result.action)
        assertEquals(20f, pan.dx)
        assertEquals(15f, pan.dy)
    }
}

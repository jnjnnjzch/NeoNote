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
import kotlin.test.assertIs

class NavigationToolTest {
    @Test
    fun `finger tap on text remains passive in pen navigation mode`() {
        val router = InputRouter()
        val canvas = InfiniteCanvas()
        val point = CanvasPoint(90f, 110f)

        router.route(
            canvas = canvas,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, point, PointerTool.Finger)),
                targetObjectId = "text-box",
            ),
            mode = InputMode.Navigate,
        )
        val result = router.route(
            canvas = canvas,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, point, PointerTool.Finger)),
                targetObjectId = "text-box",
            ),
            mode = InputMode.Navigate,
        )

        assertIs<InputAction.Ignored>(result.action)
    }

    @Test
    fun `text mode still activates an existing text object`() {
        val router = InputRouter()
        val canvas = InfiniteCanvas()
        val point = CanvasPoint(90f, 110f)

        router.route(
            canvas = canvas,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, point, PointerTool.Finger)),
                targetObjectId = "text-box",
            ),
            mode = InputMode.Write,
        )
        val result = router.route(
            canvas = canvas,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, point, PointerTool.Finger)),
                targetObjectId = "text-box",
            ),
            mode = InputMode.Write,
        )

        assertIs<InputAction.FocusExisting>(result.action)
    }
}

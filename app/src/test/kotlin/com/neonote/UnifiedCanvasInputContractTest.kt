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

class UnifiedCanvasInputContractTest {
    @Test
    fun fingerTapCreatesTextWhileFingerDragPansAndSPenWrites() {
        val canvas = InfiniteCanvas()
        val router = InputRouter(tapSlop = 4f)

        router.route(
            canvas,
            InputEvent(
                PointerEventType.Down,
                listOf(InputPointer(1, CanvasPoint(80f, 90f), PointerTool.Finger)),
            ),
            InputMode.Write,
        )
        val tap = router.route(
            canvas,
            InputEvent(
                PointerEventType.Up,
                listOf(InputPointer(1, CanvasPoint(80f, 90f), PointerTool.Finger)),
            ),
            InputMode.Write,
        )
        assertIs<InputAction.CreateOrFocusRichContentBox>(tap.action)

        router.route(
            canvas,
            InputEvent(
                PointerEventType.Down,
                listOf(InputPointer(2, CanvasPoint(20f, 20f), PointerTool.Finger)),
            ),
            InputMode.Write,
        )
        val drag = router.route(
            canvas,
            InputEvent(
                PointerEventType.Move,
                listOf(InputPointer(2, CanvasPoint(40f, 55f), PointerTool.Finger)),
            ),
            InputMode.Write,
        )
        assertIs<InputAction.PanBy>(drag.action)

        val pen = router.route(
            canvas,
            InputEvent(
                PointerEventType.Down,
                listOf(InputPointer(3, CanvasPoint(100f, 120f), PointerTool.SPen, pressure = 0.7f)),
            ),
            InputMode.Write,
        )
        assertIs<InputAction.BeginInk>(pen.action)
    }
}

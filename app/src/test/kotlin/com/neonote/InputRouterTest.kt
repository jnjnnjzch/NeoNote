package com.neonote

import com.neonote.input.InkEngine
import com.neonote.input.InputEvent
import com.neonote.input.InputPointer
import com.neonote.input.InputRouter
import com.neonote.input.PointerTool
import com.neonote.input.RouteResult
import com.neonote.model.InfiniteCanvas
import com.neonote.model.RichContentBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class InputRouterTest {
    @Test
    fun `s pen events route to ink engine`() {
        val inkEngine = InkEngine()
        val router = InputRouter(inkEngine)
        val event = InputEvent.PointerMove(
            pointers = listOf(InputPointer(id = 1, x = 4f, y = 5f, tool = PointerTool.SPEN)),
        )

        val result = router.route(event)

        assertIs<RouteResult.Ink>(result)
        assertEquals(listOf(event), inkEngine.events)
    }

    @Test
    fun `single finger touch routes to pan and two finger touch routes to zoom`() {
        val router = InputRouter(InkEngine())
        val singleFinger = InputEvent.PointerMove(
            pointers = listOf(InputPointer(id = 1, x = 1f, y = 1f, tool = PointerTool.TOUCH)),
        )
        val twoFinger = InputEvent.PointerMove(
            pointers = listOf(
                InputPointer(id = 1, x = 1f, y = 1f, tool = PointerTool.TOUCH),
                InputPointer(id = 2, x = 10f, y = 10f, tool = PointerTool.TOUCH),
            ),
        )

        assertIs<RouteResult.Pan>(router.route(singleFinger))
        assertIs<RouteResult.Zoom>(router.route(twoFinger))
    }

    @Test
    fun `blank canvas tap creates and focuses a rich content box`() {
        val router = InputRouter(InkEngine())
        val tap = InputEvent.Tap(
            pointers = listOf(InputPointer(id = 1, x = 32f, y = 64f, tool = PointerTool.TOUCH)),
            x = 32f,
            y = 64f,
            canvas = InfiniteCanvas(),
        )

        val result = assertIs<RouteResult.CreateOrFocusRichContentBox>(router.route(tap))

        val box = result.canvas.objects.single() as RichContentBox
        assertEquals("rich-content-1", box.id)
        assertEquals(32f, box.x)
        assertEquals(64f, box.y)
        assertTrue(box.isFocused)
    }
}

package com.neonote

import androidx.compose.ui.geometry.Offset
import com.neonote.engine.InputEvent
import com.neonote.engine.InputPointer
import com.neonote.engine.InputRouter
import com.neonote.engine.PointerEventType
import com.neonote.engine.PointerTool
import com.neonote.model.CanvasPoint
import com.neonote.model.RichContentBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class NeoNoteEditorControllerTest {
    @Test
    fun `panning viewport does not move document objects`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(100f, 150f))
        val originalPosition = (controller.currentCanvas.objects.single() as RichContentBox).position

        controller.panViewportBy(screenDx = 40f, screenDy = -12f)

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(originalPosition, box.position)
        assertEquals(40f, controller.state.viewport.panOffsetX)
        assertEquals(-12f, controller.state.viewport.panOffsetY)
        assertEquals(CanvasPoint(140f, 138f), controller.documentToScreen(box.position))
    }

    @Test
    fun `zooming viewport around centroid keeps centroid document point stable without resizing objects`() {
        val controller = NeoNoteEditorController()
        controller.panViewportBy(screenDx = 20f, screenDy = -10f)
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 20f))
        val originalBox = controller.currentCanvas.objects.single() as RichContentBox
        val screenCentroid = CanvasPoint(60f, 50f)
        val documentAtCentroidBeforeZoom = controller.screenToDocument(screenCentroid)

        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = screenCentroid)

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(2f, controller.state.viewport.zoomScale)
        assertEquals(documentAtCentroidBeforeZoom, controller.screenToDocument(screenCentroid))
        assertEquals(originalBox.position, box.position)
        assertEquals(originalBox.size, box.size)
    }


    @Test
    fun `routed blank tap converts screen point to document point once`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.panViewportBy(screenDx = 50f, screenDy = 20f)
        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(0f, 0f))
        val screenTap = CanvasPoint(150f, 220f)

        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = screenTap, tool = PointerTool.Finger)),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = screenTap, tool = PointerTool.Finger)),
            ),
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(CanvasPoint(25f, 90f), box.position)
    }

    @Test
    fun `selection mode can move a selected rich content box in document coordinates under zoom`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        controller.panViewportBy(screenDx = 50f, screenDy = -20f)
        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(0f, 0f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id

        controller.setSelectionMode(true)
        controller.selectCanvasObject(boxId)
        controller.moveSelectedObjectsByScreenDelta(Offset(20f, 10f))

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertTrue(controller.state.selection.isObjectSelected(boxId))
        assertEquals(CanvasPoint(35f, 35f), box.position)
    }
}

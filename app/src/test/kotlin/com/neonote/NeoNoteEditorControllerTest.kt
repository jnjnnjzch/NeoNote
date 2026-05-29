package com.neonote

import androidx.compose.ui.geometry.Offset
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
    fun `zooming viewport changes zoom without resizing document objects`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 20f))
        val originalBox = controller.currentCanvas.objects.single() as RichContentBox

        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(0f, 0f))

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(2f, controller.state.viewport.zoomScale)
        assertEquals(originalBox.position, box.position)
        assertEquals(originalBox.size, box.size)
    }

    @Test
    fun `selection mode can move a selected rich content box in document coordinates`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
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

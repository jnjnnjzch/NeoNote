package com.neonote

import com.neonote.model.CanvasPoint
import com.neonote.model.EditorTool
import com.neonote.model.RichContentBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RichContentToolBoundaryTest {
    @Test
    fun `pen and eraser tools cannot activate a text box`() {
        val controller = NeoNoteEditorController()
        controller.setTool(EditorTool.Text)
        controller.focusOrCreateRichContentBox(CanvasPoint(60f, 70f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id

        controller.setTool(EditorTool.Pen)
        controller.activateRichContentBox(boxId)

        assertEquals(EditorTool.Pen, controller.state.currentTool)
        assertNull(controller.state.focusedRichContentBoxId)
        assertFalse((controller.currentCanvas.objects.single() as RichContentBox).isFocused)

        controller.setTool(EditorTool.Eraser)
        controller.activateRichContentBox(boxId)

        assertEquals(EditorTool.Eraser, controller.state.currentTool)
        assertNull(controller.state.focusedRichContentBoxId)
        assertFalse((controller.currentCanvas.objects.single() as RichContentBox).isFocused)
    }

    @Test
    fun `text tool can still activate a text box`() {
        val controller = NeoNoteEditorController()
        controller.setTool(EditorTool.Text)
        controller.focusOrCreateRichContentBox(CanvasPoint(60f, 70f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.setTool(EditorTool.Pen)
        controller.setTool(EditorTool.Text)

        controller.activateRichContentBox(boxId)

        assertEquals(EditorTool.Text, controller.state.currentTool)
        assertEquals(boxId, controller.state.focusedRichContentBoxId)
        assertTrue((controller.currentCanvas.objects.single() as RichContentBox).isFocused)
    }

    @Test
    fun `pen tool blocks rich content mutations from child callbacks`() {
        val controller = NeoNoteEditorController()
        controller.setTool(EditorTool.Text)
        controller.focusOrCreateRichContentBox(CanvasPoint(60f, 70f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "Before")
        controller.setTool(EditorTool.Pen)
        val documentBeforeCallbacks = controller.state.document

        controller.updateRichContentText(boxId, "After")
        controller.toggleRichContentTodoCheckedState(boxId, blockIndex = 0)

        assertEquals(documentBeforeCallbacks, controller.state.document)
        assertEquals(EditorTool.Pen, controller.state.currentTool)
    }
}

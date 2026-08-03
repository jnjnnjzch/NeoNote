package com.neonote

import com.neonote.engine.toPlainText
import com.neonote.model.CanvasPoint
import com.neonote.model.RichContentBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EditorHistoryTest {
    @Test
    fun `undo and redo restore rich content document snapshots`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(80f, 120f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id

        controller.updateRichContentText(boxId, "hello history")
        assertEquals(
            "hello history",
            (controller.currentCanvas.objects.single() as RichContentBox).toPlainText(),
        )
        assertTrue(controller.canUndo)

        controller.undo()
        assertEquals(
            "",
            (controller.currentCanvas.objects.single() as RichContentBox).toPlainText(),
        )
        assertTrue(controller.canRedo)

        controller.redo()
        assertEquals(
            "hello history",
            (controller.currentCanvas.objects.single() as RichContentBox).toPlainText(),
        )
    }

    @Test
    fun `focus-only changes do not create document history entries`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(40f, 60f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id

        controller.focusRichContentBox(boxId)
        controller.undo()

        assertTrue(controller.currentCanvas.objects.isEmpty())
        assertFalse(controller.canUndo)
    }
}

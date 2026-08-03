package com.neonote

import com.neonote.model.EditorTool
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ReleaseInvariantTest {
    @Test
    fun `repeating the same title does not create a duplicate undo entry`() {
        val controller = NeoNoteEditorController()
        val originalTitle = controller.state.document.title

        controller.renameDocument("Release note")
        controller.renameDocument("Release note")
        controller.undo()

        assertEquals(originalTitle, controller.state.document.title)
        assertFalse(controller.canUndo)
    }

    @Test
    fun `switching tools remains transient and does not dirty the document`() {
        val controller = NeoNoteEditorController()
        val revision = controller.state.document.revision

        controller.setTool(EditorTool.Pen)
        controller.setTool(EditorTool.Selection)
        controller.setTool(EditorTool.Eraser)
        controller.setTool(EditorTool.Text)

        assertEquals(revision, controller.state.document.revision)
    }
}

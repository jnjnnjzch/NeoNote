package com.neonote

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DocumentTitleTest {
    @Test
    fun `renaming a document updates its revision and can be undone`() {
        val controller = NeoNoteEditorController()
        val originalTitle = controller.state.document.title
        val originalRevision = controller.state.document.revision

        controller.renameDocument("Field notes")

        assertEquals("Field notes", controller.state.document.title)
        assertEquals(originalRevision + 1, controller.state.document.revision)
        assertTrue(controller.canUndo)

        controller.undo()

        assertEquals(originalTitle, controller.state.document.title)
    }

    @Test
    fun `document titles are bounded for a stable top bar and persistence format`() {
        val controller = NeoNoteEditorController()

        controller.renameDocument("x".repeat(500))

        assertEquals(120, controller.state.document.title.length)
    }
}

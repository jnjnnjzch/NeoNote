package com.neonote

import com.neonote.model.CanvasPoint
import com.neonote.model.EditorTool
import com.neonote.model.RichContentBox
import com.neonote.model.TableNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TableControlsTest {
    @Test
    fun `active table can add rows and columns with undo support`() {
        val controller = NeoNoteEditorController()
        controller.setTool(EditorTool.Text)
        controller.focusOrCreateRichContentBox(CanvasPoint(80f, 90f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.insertRichContentTablePlaceholder(boxId = boxId, rows = 2, columns = 2)

        fun activeTable(): TableNode = (controller.currentCanvas.objects.single() as RichContentBox)
            .content
            .blocks
            .filterIsInstance<TableNode>()
            .single()

        assertEquals(2, activeTable().rows.size)
        assertEquals(2, activeTable().rows.first().size)

        controller.addActiveRichContentTableRow(boxId)
        assertEquals(3, activeTable().rows.size)
        assertTrue(activeTable().rows.all { it.size == 2 })

        controller.addActiveRichContentTableColumn(boxId)
        assertEquals(3, activeTable().rows.size)
        assertTrue(activeTable().rows.all { it.size == 3 })
        assertTrue(controller.canUndo)

        controller.undo()
        assertEquals(3, activeTable().rows.size)
        assertTrue(activeTable().rows.all { it.size == 2 })
    }
}

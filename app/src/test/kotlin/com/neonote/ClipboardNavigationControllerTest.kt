package com.neonote

import com.neonote.engine.ActiveRichContentTarget
import com.neonote.engine.RichClipboardFragment
import com.neonote.model.CanvasPoint
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContentBox
import com.neonote.model.TableCellAddress
import com.neonote.model.TableNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ClipboardNavigationControllerTest {
    @Test
    fun `rich replacement preserves formatting and participates in undo`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(80f, 90f))
        val boxId = assertIs<RichContentBox>(controller.currentCanvas.objects.single()).id
        controller.updateRichContentText(boxId, "hello world")
        val fragment = RichClipboardFragment(listOf(
            ParagraphNode(listOf(InlineText("Neo", bold = true))),
        ))

        val result = controller.replaceRichContentSelection(boxId, 6, 11, fragment)

        assertEquals(9, result?.cursorOffset)
        val edited = assertIs<RichContentBox>(controller.currentCanvas.objects.single())
        val run = assertIs<InlineText>((edited.content.blocks.single() as ParagraphNode).inlines.last())
        assertEquals("Neo", run.text)
        assertTrue(run.bold)
        assertTrue(controller.canUndo)
    }

    @Test
    fun `tab navigation skips merged continuations and appends a row`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(80f, 90f))
        val boxId = assertIs<RichContentBox>(controller.currentCanvas.objects.single()).id
        controller.insertRichContentTablePlaceholder(boxId, rows = 1, columns = 2)
        val box = assertIs<RichContentBox>(controller.currentCanvas.objects.single())
        val tableIndex = box.content.blocks.indexOfFirst { it is TableNode }
        val first = TableCellAddress(tableIndex, 0, 0, 0)
        controller.focusRichContentTableCell(boxId, first)
        controller.mergeActiveTableCellRight(boxId)

        controller.navigateRichContentTableCell(boxId, first, backwards = false)

        val table = assertIs<TableNode>(
            assertIs<RichContentBox>(controller.currentCanvas.objects.single()).content.blocks[tableIndex],
        )
        assertEquals(2, table.rows.size)
        val active = assertIs<ActiveRichContentTarget.TableCell>(controller.activeRichContentTarget(boxId))
        assertEquals(1, active.address.rowIndex)
        assertEquals(0, active.address.columnIndex)
        assertTrue(controller.canUndo)
    }
}

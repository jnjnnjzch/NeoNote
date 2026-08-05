package com.neonote

import com.neonote.engine.RichContentTree
import com.neonote.model.BlockFormula
import com.neonote.model.CanvasPoint
import com.neonote.model.RichContentBox
import com.neonote.model.TableCellAddress
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProductHardeningNestedContentTest {
    @Test
    fun `nested object edit preserves viewport and participates in undo`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(120f, 180f))
        val boxId = assertIs<RichContentBox>(controller.currentCanvas.objects.single()).id
        controller.insertRichContentTablePlaceholder(boxId)
        val address = TableCellAddress(
            blockIndex = 1,
            rowIndex = 0,
            columnIndex = 0,
            contentBlockIndex = 0,
        )
        controller.panViewportBy(screenDx = 140f, screenDy = -55f)
        val viewportBefore = controller.state.viewport
        val pageBefore = controller.state.currentPageId

        controller.insertNestedFormula(boxId, address, expression = "x^2+y^2")

        val editedBox = assertIs<RichContentBox>(controller.currentCanvas.objects.single())
        assertIs<BlockFormula>(RichContentTree.block(editedBox.content, address))
        assertEquals(viewportBefore, controller.state.viewport)
        assertEquals(pageBefore, controller.state.currentPageId)
        assertTrue(controller.canUndo)

        controller.undo()

        val restoredBox = assertIs<RichContentBox>(controller.currentCanvas.objects.single())
        val restoredBlocks = RichContentTree.cellContent(restoredBox.content, address)?.blocks.orEmpty()
        assertFalse(restoredBlocks.any { it is BlockFormula })
        assertEquals(viewportBefore, controller.state.viewport)
        assertEquals(pageBefore, controller.state.currentPageId)
        assertTrue(controller.canRedo)
    }
}

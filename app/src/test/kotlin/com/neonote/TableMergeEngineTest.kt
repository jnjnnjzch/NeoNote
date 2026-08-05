package com.neonote

import com.neonote.engine.RichContentEngine
import com.neonote.engine.RichContentCommand
import com.neonote.engine.RichContentCommandResult
import com.neonote.engine.TableMergeEngine
import com.neonote.engine.normalizedGrid
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TableMergeEngineTest {
    @Test
    fun `merge preserves all selected content in row-major order`() {
        val table = tableWithLabels(2, 2)
        val merged = TableMergeEngine.merge(table, 0, 1, 0, 1)
        val anchor = merged.rows[0][0]

        assertEquals(2, anchor.rowSpan)
        assertEquals(2, anchor.columnSpan)
        assertEquals(listOf("0,0", "0,1", "1,0", "1,1"), anchor.content.blocks.map(::paragraphText))
        assertEquals(0, merged.rows[1][1].mergedInto?.rowIndex)
        assertEquals(0, merged.rows[1][1].mergedInto?.columnIndex)
    }

    @Test
    fun `repeated right and down merge expands one anchor`() {
        var table = tableWithLabels(3, 3)
        table = TableMergeEngine.mergeRight(table, 0, 0)
        table = TableMergeEngine.mergeRight(table, 0, 0)
        table = TableMergeEngine.mergeDown(table, 0, 0)

        assertEquals(2, table.rows[0][0].rowSpan)
        assertEquals(3, table.rows[0][0].columnSpan)
        assertEquals(6, table.rows[0][0].content.blocks.size)
    }

    @Test
    fun `split restores editable cells and keeps consolidated content at anchor`() {
        val merged = TableMergeEngine.merge(tableWithLabels(2, 2), 0, 1, 0, 1)
        val split = TableMergeEngine.splitAt(merged, 1, 1)

        assertEquals(1, split.rows[0][0].rowSpan)
        assertEquals(1, split.rows[0][0].columnSpan)
        assertNull(split.rows[1][1].mergedInto)
        assertEquals(4, split.rows[0][0].content.blocks.size)
    }

    @Test
    fun `partial overlap with an existing merge is rejected`() {
        val merged = TableMergeEngine.merge(tableWithLabels(2, 3), 0, 1, 0, 1)
        assertFailsWith<IllegalArgumentException> {
            TableMergeEngine.merge(merged, 0, 0, 1, 2)
        }
    }

    @Test
    fun `row insertion safely clears spans instead of leaving stale anchors`() {
        val engine = RichContentEngine()
        val merged = TableMergeEngine.merge(tableWithLabels(2, 2), 0, 1, 0, 1)
        val box = RichContentBox(id = "box", content = RichContent(listOf(merged)))
        val edited = engine.execute(
            box,
            RichContentCommand.AddTableRow(TableCellAddress(0, 0, 0)),
        ) as RichContentCommandResult.ContentEdited
        val result = edited.box.content.blocks.single() as TableNode

        assertEquals(3, result.rows.size)
        assertEquals(1, result.rows[0][0].rowSpan)
        assertNull(result.rows[1][1].mergedInto)
    }

    @Test
    fun `column insertion safely clears spans instead of leaving stale anchors`() {
        val engine = RichContentEngine()
        val merged = TableMergeEngine.merge(tableWithLabels(2, 2), 0, 1, 0, 1)
        val box = RichContentBox(id = "box", content = RichContent(listOf(merged)))
        val edited = engine.execute(
            box,
            RichContentCommand.AddTableColumn(TableCellAddress(0, 0, 0)),
        ) as RichContentCommandResult.ContentEdited
        val result = edited.box.content.blocks.single() as TableNode

        assertEquals(3, result.rows.first().size)
        assertEquals(1, result.rows[0][0].columnSpan)
        assertNull(result.rows[1][1].mergedInto)
    }

    @Test
    fun `invalid continuation anchor is ignored during normalization`() {
        val malformed = TableNode(
            rows = listOf(listOf(TableCell(mergedInto = com.neonote.model.TableCellMergeAnchor(9, 9)))),
        )

        val normalized = malformed.normalizedGrid()

        assertNull(normalized.rows[0][0].mergedInto)
    }

    private fun tableWithLabels(rows: Int, columns: Int): TableNode = TableNode(
        rows = List(rows) { row -> List(columns) { column ->
            TableCell(RichContent(listOf(ParagraphNode(listOf(InlineText("$row,$column"))))))
        } },
    )

    private fun paragraphText(block: com.neonote.model.BlockNode): String =
        ((block as ParagraphNode).inlines.single() as InlineText).text
}

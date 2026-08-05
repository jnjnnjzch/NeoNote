package com.neonote

import com.neonote.engine.TableMergeEngine
import com.neonote.engine.TableNavigationEngine
import com.neonote.model.TableCell
import com.neonote.model.TableNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TableNavigationEngineTest {
    @Test
    fun `tab skips continuation cells of a merge`() {
        val merged = TableMergeEngine.merge(
            TableNode(rows = List(2) { List(3) { TableCell() } }),
            firstRow = 0,
            lastRow = 0,
            firstColumn = 0,
            lastColumn = 1,
        )

        val next = TableNavigationEngine.navigate(merged, 0, 0, backwards = false)

        assertEquals(0, next.rowIndex)
        assertEquals(2, next.columnIndex)
        assertFalse(next.appendedRow)
    }

    @Test
    fun `tab from final anchor appends a row and focuses its first cell`() {
        val table = TableNode(rows = listOf(listOf(TableCell(), TableCell())))

        val next = TableNavigationEngine.navigate(table, 0, 1, backwards = false)

        assertTrue(next.appendedRow)
        assertEquals(2, next.table.rows.size)
        assertEquals(1, next.rowIndex)
        assertEquals(0, next.columnIndex)
    }

    @Test
    fun `shift tab from first cell stays at first cell`() {
        val table = TableNode(rows = listOf(listOf(TableCell(), TableCell())))

        val previous = TableNavigationEngine.navigate(table, 0, 0, backwards = true)

        assertEquals(0, previous.rowIndex)
        assertEquals(0, previous.columnIndex)
        assertFalse(previous.appendedRow)
    }
}

package com.neonote

import com.neonote.engine.SpanAwareTableLayout
import com.neonote.engine.TableGridCellKey
import com.neonote.engine.TableMergeEngine
import com.neonote.model.TableCell
import com.neonote.model.TableNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SpanAwareTableLayoutTest {
    @Test
    fun `column and row spans produce exact non-overlapping geometry`() {
        var table = TableNode(rows = List(3) { List(3) { TableCell() } })
        table = TableMergeEngine.merge(table, 0, 1, 0, 1)
        val plan = SpanAwareTableLayout.calculate(
            table = table,
            columnWidths = listOf(100, 120, 80),
            intrinsicHeights = mapOf(
                TableGridCellKey(0, 0) to 140,
                TableGridCellKey(0, 2) to 40,
                TableGridCellKey(1, 2) to 60,
                TableGridCellKey(2, 0) to 50,
                TableGridCellKey(2, 1) to 50,
                TableGridCellKey(2, 2) to 50,
            ),
            minimumRowHeight = 44,
        )
        val anchor = plan.placements.first { it.key == TableGridCellKey(0, 0) }

        assertEquals(220, anchor.width)
        assertEquals(plan.rowHeights[0] + plan.rowHeights[1], anchor.height)
        assertTrue(anchor.height >= 140)
        assertEquals(300, plan.width)
        assertEquals(plan.rowHeights.sum(), plan.height)
        assertEquals(6, plan.placements.size)
    }
}

package com.neonote

import com.neonote.engine.DefaultDocumentJson
import com.neonote.model.TableCell
import com.neonote.model.TableCellMergeAnchor
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TableSpanSchemaCompatibilityTest {
    @Test
    fun `legacy table cell without span fields decodes as independent cell`() {
        val cell = DefaultDocumentJson.decodeFromString(TableCell.serializer(), "{}")

        assertEquals(1, cell.rowSpan)
        assertEquals(1, cell.columnSpan)
        assertNull(cell.mergedInto)
    }

    @Test
    fun `merged cell metadata survives compact json round trip`() {
        val original = TableCell(
            rowSpan = 3,
            columnSpan = 2,
            mergedInto = TableCellMergeAnchor(rowIndex = 1, columnIndex = 4),
        )

        val encoded = DefaultDocumentJson.encodeToString(TableCell.serializer(), original)
        val decoded = DefaultDocumentJson.decodeFromString(TableCell.serializer(), encoded)

        assertEquals(original, decoded)
    }
}

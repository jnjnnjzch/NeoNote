package com.example.cahier.core.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentSerializerTest {

    @Test
    fun encodeDecode_roundTripsTableBlock() {
        val document = TicDocument(
            pages = listOf(
                CanvasPage(
                    blocks = listOf(
                        TableBlock(rows = 3, columns = 3)
                    )
                )
            )
        )

        val raw = DocumentSerializer.encode(document)
        val decoded = DocumentSerializer.decodeOrNull(raw)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.pages.size)
        assertEquals(1, decoded.pages.first().blocks.size)
        val table = decoded.pages.first().blocks.first() as TableBlock
        assertEquals(3, table.rows)
        assertEquals(3, table.columns)
    }

    @Test
    fun decodeOrNull_invalidPayload_returnsNull() {
        val decoded = DocumentSerializer.decodeOrNull("{bad json")
        assertNull(decoded)
    }

    @Test
    fun encodeDecode_preservesCellFormattingAndRowGrowth() {
        val row = listOf(
            TableCell(text = "A1", bold = true),
            TableCell(text = "B1"),
            TableCell(text = "C1")
        )
        val table = TableBlock(
            rows = 4,
            columns = 3,
            cells = listOf(row, row, row, row)
        )
        val document = TicDocument(pages = listOf(CanvasPage(blocks = listOf(table))))

        val decoded = DocumentSerializer.decodeOrNull(DocumentSerializer.encode(document))
        val decodedTable = decoded!!.pages.first().blocks.first() as TableBlock

        assertEquals(4, decodedTable.rows)
        assertEquals(true, decodedTable.cells.first().first().bold)
        assertEquals("A1", decodedTable.cells.first().first().text)
    }
}

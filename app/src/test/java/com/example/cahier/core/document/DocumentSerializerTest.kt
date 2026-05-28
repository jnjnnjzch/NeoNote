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
}

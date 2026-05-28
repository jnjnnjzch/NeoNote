package com.example.cahier.features.drawing

import com.example.cahier.core.document.ImageBlock
import com.example.cahier.core.document.TableBlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StressDocumentFactoryTest {
    @Test
    fun createBlocks_generates_expected_counts() {
        val blocks = StressDocumentFactory.createBlocks(
            tableCount = 10,
            rowsPerTable = 20,
            columnsPerTable = 6,
            imageCount = 50
        )
        assertEquals(60, blocks.size)
        assertEquals(10, blocks.count { it is TableBlock })
        assertEquals(50, blocks.count { it is ImageBlock })
    }

    @Test
    fun createBlocks_includes_formula_cells() {
        val tables = StressDocumentFactory.createBlocks(tableCount = 1, rowsPerTable = 10, columnsPerTable = 10, imageCount = 0)
            .filterIsInstance<TableBlock>()
        val hasLatex = tables.flatMap { it.cells }.flatten().any { !it.latex.isNullOrBlank() }
        assertTrue(hasLatex)
    }
}

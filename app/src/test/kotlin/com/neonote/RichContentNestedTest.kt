package com.neonote

import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.TableCell
import com.neonote.model.TableNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RichContentNestedTest {
    @Test
    fun `table cell can contain paragraphs block media formulas and another table`() {
        val nestedTable = TableNode(
            rows = listOf(
                listOf(
                    TableCell(
                        content = RichContent(
                            blocks = listOf(ParagraphNode(listOf(InlineText("nested")))),
                        ),
                    ),
                ),
            ),
        )
        val table = TableNode(
            rows = listOf(
                listOf(
                    TableCell(
                        content = RichContent(
                            blocks = listOf(
                                ParagraphNode(listOf(InlineText("cell paragraph"))),
                                BlockImage(assetId = "image-1", altText = "block image"),
                                BlockFormula(expression = "E = mc^2"),
                                nestedTable,
                            ),
                        ),
                    ),
                ),
            ),
        )

        val cellBlocks = table.rows.single().single().content.blocks
        assertIs<ParagraphNode>(cellBlocks[0])
        assertIs<BlockImage>(cellBlocks[1])
        assertIs<BlockFormula>(cellBlocks[2])
        val actualNestedTable = assertIs<TableNode>(cellBlocks[3])
        val actualNestedParagraph = assertIs<ParagraphNode>(
            actualNestedTable.rows.single().single().content.blocks.single(),
        )
        val actualNestedText = assertIs<InlineText>(actualNestedParagraph.inlines.single())
        assertEquals("nested", actualNestedText.text)
    }
}

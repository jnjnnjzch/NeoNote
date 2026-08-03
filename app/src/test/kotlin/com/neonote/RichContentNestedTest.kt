package com.neonote

import com.neonote.engine.RichContentCommand
import com.neonote.engine.RichContentCommandResult
import com.neonote.engine.RichContentEditorSession
import com.neonote.engine.RichContentEngine
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RichContentNestedTest {
    @Test
    fun `table cell can contain paragraphs block media formulas and another table`() {
        val table = TableNode(
            rows = listOf(
                listOf(
                    TableCell(
                        content = RichContent(
                            blocks = listOf(
                                paragraph("cell paragraph"),
                                BlockImage(assetId = "image-1", altText = "block image"),
                                BlockFormula(expression = "E = mc^2"),
                                nestedTable(),
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

    @Test
    fun `editing a later cell paragraph preserves all sibling rich blocks`() {
        val address = TableCellAddress(
            blockIndex = 0,
            rowIndex = 0,
            columnIndex = 0,
            contentBlockIndex = 3,
        )

        val result = RichContentEngine().execute(
            richCellBox(),
            RichContentCommand.ReplaceTableCellParagraphText(
                address = address,
                text = "edited second paragraph",
            ),
        ) as RichContentCommandResult.ContentEdited

        val blocks = cellBlocks(result.box)
        assertEquals(
            "first paragraph",
            assertIs<InlineText>(assertIs<ParagraphNode>(blocks[0]).inlines.single()).text,
        )
        assertIs<BlockImage>(blocks[1])
        assertIs<BlockFormula>(blocks[2])
        assertEquals(
            "edited second paragraph",
            assertIs<InlineText>(assertIs<ParagraphNode>(blocks[3]).inlines.single()).text,
        )
        assertIs<TableNode>(blocks[4])
    }

    @Test
    fun `session reads and edits the addressed paragraph inside a rich cell`() {
        val address = TableCellAddress(
            blockIndex = 0,
            rowIndex = 0,
            columnIndex = 0,
            contentBlockIndex = 3,
        )
        val session = RichContentEditorSession(richCellBox())

        assertEquals("second paragraph", session.tableCellPlainText(address))
        session.replaceTableCellParagraphFromPlatformInput(address, "session edit")

        val blocks = cellBlocks(session.box)
        assertEquals(
            "session edit",
            assertIs<InlineText>(assertIs<ParagraphNode>(blocks[3]).inlines.single()).text,
        )
        assertIs<BlockImage>(blocks[1])
        assertIs<BlockFormula>(blocks[2])
        assertIs<TableNode>(blocks[4])
    }

    private fun richCellBox(): RichContentBox = RichContentBox(
        id = "box-rich-cell",
        content = RichContent(
            blocks = listOf(
                TableNode(
                    rows = listOf(
                        listOf(
                            TableCell(
                                content = RichContent(
                                    blocks = listOf(
                                        paragraph("first paragraph"),
                                        BlockImage(assetId = "image-1", altText = "block image"),
                                        BlockFormula(expression = "E = mc^2"),
                                        paragraph("second paragraph"),
                                        nestedTable(),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    )

    private fun cellBlocks(box: RichContentBox) =
        assertIs<TableNode>(box.content.blocks.single()).rows.single().single().content.blocks

    private fun nestedTable(): TableNode = TableNode(
        rows = listOf(
            listOf(
                TableCell(
                    content = RichContent(blocks = listOf(paragraph("nested"))),
                ),
            ),
        ),
    )

    private fun paragraph(text: String): ParagraphNode =
        ParagraphNode(inlines = listOf(InlineText(text)))
}

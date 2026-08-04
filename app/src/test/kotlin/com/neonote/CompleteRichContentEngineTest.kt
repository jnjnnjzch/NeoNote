package com.neonote

import com.neonote.engine.RichContentCommand
import com.neonote.engine.RichContentCommandResult
import com.neonote.engine.RichContentEngine
import com.neonote.engine.RichContentTree
import com.neonote.engine.TypingStyle
import com.neonote.model.BlockImage
import com.neonote.model.ImageCrop
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableCellAddress
import com.neonote.model.TableNode
import com.neonote.model.TextCursorPosition
import com.neonote.model.TextRange
import com.neonote.model.TextSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CompleteRichContentEngineTest {
    private val engine = RichContentEngine()

    @Test
    fun `recursive table cell address edits nested content without touching siblings`() {
        val nested = TableNode(rows = listOf(listOf(TableCell())))
        val outer = TableNode(rows = listOf(listOf(
            TableCell(content = RichContent(listOf(nested))),
            TableCell(content = RichContent(listOf(ParagraphNode(listOf(InlineText("sibling"))))))),
        )))
        val box = RichContentBox(id = "box", content = RichContent(listOf(outer)))
        val address = TableCellAddress(0, 0, 0).child(0, 0, 0)

        val result = engine.execute(
            box,
            RichContentCommand.ReplaceTableCellParagraphText(address, "nested text"),
        ) as RichContentCommandResult.ContentEdited

        val paragraph = assertIs<ParagraphNode>(RichContentTree.block(result.box.content, address))
        assertEquals("nested text", assertIs<InlineText>(paragraph.inlines.single()).text)
        val sibling = (assertIs<TableNode>(result.box.content.blocks.single()).rows[0][1].content.blocks.single() as ParagraphNode)
        assertEquals("sibling", assertIs<InlineText>(sibling.inlines.single()).text)
    }

    @Test
    fun `nested table supports row column insertion deletion and manual width`() {
        val outerAddress = TableCellAddress(0, 0, 0)
        var box = RichContentBox(
            id = "box",
            content = RichContent(listOf(TableNode(rows = listOf(listOf(TableCell()))))),
        )
        box = (engine.execute(box, RichContentCommand.InsertNestedTable(outerAddress, 2, 2)) as RichContentCommandResult.ContentEdited).box
        val nestedAddress = outerAddress.child(0, 0, 0)
        box = (engine.execute(box, RichContentCommand.AddTableRow(nestedAddress)) as RichContentCommandResult.ContentEdited).box
        box = (engine.execute(box, RichContentCommand.AddTableColumn(nestedAddress)) as RichContentCommandResult.ContentEdited).box
        box = (engine.execute(box, RichContentCommand.SetTableColumnWidth(nestedAddress, 180f)) as RichContentCommandResult.ContentEdited).box

        var table = requireNotNull(RichContentTree.table(box.content, nestedAddress))
        assertEquals(3, table.rows.size)
        assertEquals(3, table.rows.first().size)
        assertEquals(180f, table.columnPolicies.first().manualWidth)

        box = (engine.execute(box, RichContentCommand.DeleteTableRow(nestedAddress)) as RichContentCommandResult.ContentEdited).box
        box = (engine.execute(box, RichContentCommand.DeleteTableColumn(nestedAddress)) as RichContentCommandResult.ContentEdited).box
        table = requireNotNull(RichContentTree.table(box.content, nestedAddress))
        assertEquals(2, table.rows.size)
        assertEquals(2, table.rows.first().size)
    }

    @Test
    fun `full character formatting survives later insertion and run merging`() {
        val paragraph = ParagraphNode(listOf(InlineText("hello")))
        var box = RichContentBox(id = "box", content = RichContent(listOf(paragraph)))
        val selection = TextSelection(TextRange(TextCursorPosition(0, 0), TextCursorPosition(0, 5)))
        box = (engine.execute(box, RichContentCommand.ToggleStrikethrough(selection)) as RichContentCommandResult.ContentEdited).box
        box = (engine.execute(box, RichContentCommand.SetTextColor(selection, 0xFF112233.toInt())) as RichContentCommandResult.ContentEdited).box
        box = (engine.execute(box, RichContentCommand.SetHighlightColor(selection, 0xFFFFFF00.toInt())) as RichContentCommandResult.ContentEdited).box
        box = (engine.execute(box, RichContentCommand.SetFontScale(selection, 1.5f)) as RichContentCommandResult.ContentEdited).box
        box = (engine.execute(box, RichContentCommand.SetLink(selection, "https://example.com")) as RichContentCommandResult.ContentEdited).box
        val inserted = engine.execute(
            box,
            RichContentCommand.InsertText("!", TextSelection.cursor(TextCursorPosition(0, 5)), TypingStyle()),
        ) as RichContentCommandResult.ContentEdited

        val runs = assertIs<ParagraphNode>(inserted.box.content.blocks.single()).inlines.filterIsInstance<InlineText>()
        assertTrue(runs.all { it.strikethrough })
        assertTrue(runs.all { it.textColorArgb == 0xFF112233.toInt() })
        assertTrue(runs.all { it.highlightColorArgb == 0xFFFFFF00.toInt() })
        assertTrue(runs.all { it.fontScale == 1.5f })
        assertTrue(runs.all { it.link == "https://example.com" })
    }

    @Test
    fun `block image geometry crop and ordering are editable`() {
        var box = RichContentBox(
            id = "box",
            content = RichContent(listOf(
                ParagraphNode(listOf(InlineText("before"))),
                BlockImage(assetId = "asset"),
                ParagraphNode(listOf(InlineText("after"))),
            )),
        )
        box = (engine.execute(box, RichContentCommand.UpdateBlockImage(
            blockIndex = 1,
            width = 500f,
            height = 300f,
            rotationDegrees = 90f,
            crop = ImageCrop(0.1f, 0.2f, 0.9f, 0.8f),
            caption = "figure",
        )) as RichContentCommandResult.ContentEdited).box
        box = (engine.execute(box, RichContentCommand.MoveBlock(1, 0)) as RichContentCommandResult.ContentEdited).box

        val image = assertIs<BlockImage>(box.content.blocks.first())
        assertEquals(500f, image.width)
        assertEquals(300f, image.height)
        assertEquals(90f, image.rotationDegrees)
        assertEquals("figure", image.caption)
        assertEquals(0.1f, image.crop.leftFraction)
    }
}

package com.neonote

import com.neonote.engine.RichContentCommand
import com.neonote.engine.RichContentCommandResult
import com.neonote.engine.RichContentEngine
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.engine.RichContentMeasurer
import com.neonote.engine.RichContentEditorSession
import com.neonote.engine.toPlainText
import com.neonote.engine.TypingStyle
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.CanvasSize
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ListItemMetadata
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableNode
import com.neonote.model.TextCursorPosition
import com.neonote.model.TextRange
import com.neonote.model.TextSelection
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RichContentEngineTest {

    @Test
    fun `paragraph text replacement preserves non active sibling blocks`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(
                blocks = listOf(
                    ParagraphNode(inlines = listOf(InlineText("title"))),
                    ParagraphNode(inlines = listOf(InlineText("ni"))),
                    BlockFormula(expression = "E = mc^2"),
                ),
            ),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.ReplaceParagraphText(blockIndex = 1, text = "你"),
        ) as RichContentCommandResult.ContentEdited

        assertEquals("title\n你\n", result.box.toPlainText())
        assertEquals(3, result.box.content.blocks.size)
        assertIs<BlockFormula>(result.box.content.blocks[2])
        assertEquals(TextCursorPosition(blockIndex = 1, inlineOffset = 1), result.selection.start)
    }
    @Test
    fun `plain text replacement splits newline characters into paragraphs`() {
        val engine = RichContentEngine()
        val box = RichContentBox(id = "box-1")

        val result = engine.execute(
            box = box,
            command = RichContentCommand.ReplacePlainText("first\nsecond\n"),
        ) as RichContentCommandResult.ContentReplaced

        assertEquals(3, result.box.content.blocks.size)
        val first = assertIs<ParagraphNode>(result.box.content.blocks[0])
        val second = assertIs<ParagraphNode>(result.box.content.blocks[1])
        val trailing = assertIs<ParagraphNode>(result.box.content.blocks[2])
        assertEquals("first", assertIs<InlineText>(first.inlines.single()).text)
        assertEquals("second", assertIs<InlineText>(second.inlines.single()).text)
        assertEquals("", assertIs<InlineText>(trailing.inlines.single()).text)
        assertEquals("first\nsecond\n", result.box.toPlainText())
    }

    @Test
    fun `insert text edits paragraph content without Android dependencies`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("helo"))))),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.InsertText(
                text = "l",
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 0, inlineOffset = 2)),
            ),
        ) as RichContentCommandResult.ContentEdited

        assertEquals("hello", result.box.toPlainText())
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 3), result.selection.start)
    }

    @Test
    fun `delete backward removes previous character at cursor`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("hello"))))),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.DeleteBackward(
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 0, inlineOffset = 3)),
            ),
        ) as RichContentCommandResult.ContentEdited

        assertEquals("helo", result.box.toPlainText())
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 2), result.selection.start)
    }

    @Test
    fun `insert paragraph splits the current paragraph at selection`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("hello"))))),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.InsertParagraph(
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 0, inlineOffset = 2)),
            ),
        ) as RichContentCommandResult.ContentEdited

        assertEquals("he\nllo", result.box.toPlainText())
        assertEquals(2, result.box.content.blocks.size)
        assertEquals(TextCursorPosition(blockIndex = 1, inlineOffset = 0), result.selection.start)
    }

    @Test
    fun `toggle inline marks stores bold italic and underline on inline text`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("hello"))))),
        )
        val selection = TextSelection(
            TextRange(
                start = TextCursorPosition(blockIndex = 0, inlineOffset = 1),
                end = TextCursorPosition(blockIndex = 0, inlineOffset = 4),
            ),
        )

        val bold = engine.execute(box, RichContentCommand.ToggleBold(selection)) as RichContentCommandResult.ContentEdited
        val italic = engine.execute(bold.box, RichContentCommand.ToggleItalic(selection)) as RichContentCommandResult.ContentEdited
        val underline = engine.execute(italic.box, RichContentCommand.ToggleUnderline(selection)) as RichContentCommandResult.ContentEdited

        val paragraph = assertIs<ParagraphNode>(underline.box.content.blocks.single())
        val leading = assertIs<InlineText>(paragraph.inlines[0])
        val styled = assertIs<InlineText>(paragraph.inlines[1])
        val trailing = assertIs<InlineText>(paragraph.inlines[2])
        assertEquals("h", leading.text)
        assertFalse(leading.bold)
        assertEquals("ell", styled.text)
        assertTrue(styled.bold)
        assertTrue(styled.italic)
        assertTrue(styled.underline)
        assertEquals("o", trailing.text)
        assertFalse(trailing.underline)
    }

    @Test
    fun `toggle style on selected word stores marks on only selected inline text`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("hello world"))))),
        )
        val worldSelection = TextSelection(
            TextRange(
                start = TextCursorPosition(blockIndex = 0, inlineOffset = 6),
                end = TextCursorPosition(blockIndex = 0, inlineOffset = 11),
            ),
        )

        val bold = engine.execute(box, RichContentCommand.ToggleBold(worldSelection)) as RichContentCommandResult.ContentEdited
        val italic = engine.execute(bold.box, RichContentCommand.ToggleItalic(worldSelection)) as RichContentCommandResult.ContentEdited
        val underline = engine.execute(italic.box, RichContentCommand.ToggleUnderline(worldSelection)) as RichContentCommandResult.ContentEdited

        val paragraph = assertIs<ParagraphNode>(underline.box.content.blocks.single())
        val plain = assertIs<InlineText>(paragraph.inlines[0])
        val styled = assertIs<InlineText>(paragraph.inlines[1])
        assertEquals("hello ", plain.text)
        assertFalse(plain.bold)
        assertFalse(plain.italic)
        assertFalse(plain.underline)
        assertEquals("world", styled.text)
        assertTrue(styled.bold)
        assertTrue(styled.italic)
        assertTrue(styled.underline)
    }

    @Test
    fun `insert text uses active typing style at collapsed cursor`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("he"))))),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.InsertText(
                text = "llo",
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 0, inlineOffset = 2)),
                typingStyle = TypingStyle(bold = true, italic = true, underline = true),
            ),
        ) as RichContentCommandResult.ContentEdited

        val paragraph = assertIs<ParagraphNode>(result.box.content.blocks.single())
        val plain = assertIs<InlineText>(paragraph.inlines[0])
        val styled = assertIs<InlineText>(paragraph.inlines[1])
        assertEquals("he", plain.text)
        assertFalse(plain.bold)
        assertEquals("llo", styled.text)
        assertTrue(styled.bold)
        assertTrue(styled.italic)
        assertTrue(styled.underline)
        assertEquals("hello", result.box.toPlainText())
    }

    @Test
    fun `editor session ctrl style toggle affects subsequent typing at collapsed cursor`() {
        val session = RichContentEditorSession(
            initialBox = RichContentBox(
                id = "box-1",
                content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("he"))))),
            ),
        )
        session.setSelectionFromPlainOffsets(2)

        val toggle = session.toggleBold()
        val edit = session.insertText("llo")

        assertTrue(session.typingStyle.bold)
        assertEquals(emptyList(), toggle.commands)
        val paragraph = assertIs<ParagraphNode>(edit.box.content.blocks.single())
        val styled = assertIs<InlineText>(paragraph.inlines[1])
        assertEquals("llo", styled.text)
        assertTrue(styled.bold)
        assertEquals("hello", edit.box.toPlainText())
    }

    @Test
    fun `range style toggle splits and merges inline text nodes`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(
                blocks = listOf(
                    ParagraphNode(
                        inlines = listOf(
                            InlineText("he"),
                            InlineText("ll", bold = true),
                            InlineText("o"),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.ToggleBold(
                TextSelection(
                    TextRange(
                        start = TextCursorPosition(blockIndex = 0, inlineOffset = 0),
                        end = TextCursorPosition(blockIndex = 0, inlineOffset = 5),
                    ),
                ),
            ),
        ) as RichContentCommandResult.ContentEdited

        val paragraph = assertIs<ParagraphNode>(result.box.content.blocks.single())
        val merged = assertIs<InlineText>(paragraph.inlines.single())
        assertEquals("hello", merged.text)
        assertTrue(merged.bold)
    }


    @Test
    fun `toggle list commands add and remove paragraph metadata`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(
                blocks = listOf(
                    ParagraphNode(inlines = listOf(InlineText("first"))),
                    ParagraphNode(inlines = listOf(InlineText("second"))),
                ),
            ),
        )
        val selection = TextSelection(
            TextRange(
                start = TextCursorPosition(blockIndex = 0, inlineOffset = 0),
                end = TextCursorPosition(blockIndex = 1, inlineOffset = 6),
            ),
        )

        val bulleted = engine.execute(box, RichContentCommand.ToggleBulletList(selection)) as RichContentCommandResult.ContentEdited

        assertEquals(ListKind.Bullet, assertIs<ParagraphNode>(bulleted.box.content.blocks[0]).listMetadata?.kind)
        assertEquals(ListKind.Bullet, assertIs<ParagraphNode>(bulleted.box.content.blocks[1]).listMetadata?.kind)

        val plain = engine.execute(bulleted.box, RichContentCommand.ToggleBulletList(selection)) as RichContentCommandResult.ContentEdited

        assertEquals(null, assertIs<ParagraphNode>(plain.box.content.blocks[0]).listMetadata)
        assertEquals(null, assertIs<ParagraphNode>(plain.box.content.blocks[1]).listMetadata)
    }

    @Test
    fun `todo checked toggle only changes requested block`() {
        val engine = RichContentEngine()
        val todo = ListItemMetadata(kind = ListKind.Todo)
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(
                blocks = listOf(
                    ParagraphNode(inlines = listOf(InlineText("first")), listMetadata = todo),
                    ParagraphNode(inlines = listOf(InlineText("second")), listMetadata = todo.copy(checked = true)),
                ),
            ),
        )

        val result = engine.execute(box, RichContentCommand.ToggleTodoCheckedState(blockIndex = 0)) as RichContentCommandResult.ContentEdited

        assertTrue(assertIs<ParagraphNode>(result.box.content.blocks[0]).listMetadata?.checked == true)
        assertTrue(assertIs<ParagraphNode>(result.box.content.blocks[1]).listMetadata?.checked == true)
    }

    @Test
    fun `insert paragraph continues list item metadata and resets todo checked state`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(
                blocks = listOf(
                    ParagraphNode(
                        inlines = listOf(InlineText("done item")),
                        listMetadata = ListItemMetadata(kind = ListKind.Todo, checked = true),
                    ),
                ),
            ),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.InsertParagraph(
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 0, inlineOffset = "done item".length)),
            ),
        ) as RichContentCommandResult.ContentEdited

        assertEquals("done item\n", result.box.toPlainText())
        assertEquals(ListKind.Todo, assertIs<ParagraphNode>(result.box.content.blocks[0]).listMetadata?.kind)
        val continued = assertIs<ParagraphNode>(result.box.content.blocks[1])
        assertEquals(ListKind.Todo, continued.listMetadata?.kind)
        assertFalse(continued.listMetadata?.checked == true)
        assertEquals(TextCursorPosition(blockIndex = 1, inlineOffset = 0), result.selection.start)
    }


    @Test
    fun `single line paste inserts plain text at cursor`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("helo"))))),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.PastePlainText(
                text = "l",
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 0, inlineOffset = 2)),
            ),
        ) as RichContentCommandResult.ContentEdited

        assertEquals("hello", result.box.toPlainText())
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 3), result.selection.start)
    }

    @Test
    fun `multi line paste creates paragraph nodes and keeps trailing text`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("hello"))))),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.PastePlainText(
                text = "one\r\ntwo\nthree",
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 0, inlineOffset = 2)),
            ),
        ) as RichContentCommandResult.ContentEdited

        assertEquals("heone\ntwo\nthreello", result.box.toPlainText())
        assertEquals(3, result.box.content.blocks.size)
        assertEquals("heone", assertIs<ParagraphNode>(result.box.content.blocks[0]).inlines.joinToString("") { assertIs<InlineText>(it).text })
        assertEquals("two", assertIs<InlineText>(assertIs<ParagraphNode>(result.box.content.blocks[1]).inlines.single()).text)
        assertEquals("threello", assertIs<ParagraphNode>(result.box.content.blocks[2]).inlines.joinToString("") { assertIs<InlineText>(it).text })
        assertEquals(TextCursorPosition(blockIndex = 2, inlineOffset = 5), result.selection.start)
    }

    @Test
    fun `backspace at paragraph start merges with previous paragraph and preserves inline styles`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(
                blocks = listOf(
                    ParagraphNode(inlines = listOf(InlineText("hello", bold = true))),
                    ParagraphNode(inlines = listOf(InlineText("world", italic = true))),
                ),
            ),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.DeleteBackward(
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 1, inlineOffset = 0)),
            ),
        ) as RichContentCommandResult.ContentEdited

        assertEquals("helloworld", result.box.toPlainText())
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 5), result.selection.start)
        val paragraph = assertIs<ParagraphNode>(result.box.content.blocks.single())
        val first = assertIs<InlineText>(paragraph.inlines[0])
        val second = assertIs<InlineText>(paragraph.inlines[1])
        assertEquals("hello", first.text)
        assertTrue(first.bold)
        assertEquals("world", second.text)
        assertTrue(second.italic)
    }

    @Test
    fun `delete selected range merges surrounding paragraph text`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(
                blocks = listOf(
                    ParagraphNode(inlines = listOf(InlineText("hello"))),
                    ParagraphNode(inlines = listOf(InlineText("wide"))),
                    ParagraphNode(inlines = listOf(InlineText("world"))),
                ),
            ),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.DeleteSelection(
                TextSelection(
                    TextRange(
                        start = TextCursorPosition(blockIndex = 0, inlineOffset = 2),
                        end = TextCursorPosition(blockIndex = 2, inlineOffset = 3),
                    ),
                ),
            ),
        ) as RichContentCommandResult.ContentEdited

        assertEquals("held", result.box.toPlainText())
        assertEquals(1, result.box.content.blocks.size)
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 2), result.selection.start)
    }

    @Test
    fun `plain text paste inherits surrounding inline style when no typing style is active`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(
                blocks = listOf(
                    ParagraphNode(
                        inlines = listOf(
                            InlineText("he", bold = true),
                            InlineText("lo", italic = true),
                        ),
                    ),
                ),
            ),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.PastePlainText(
                text = "l",
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 0, inlineOffset = 2)),
            ),
        ) as RichContentCommandResult.ContentEdited

        val paragraph = assertIs<ParagraphNode>(result.box.content.blocks.single())
        val boldRun = assertIs<InlineText>(paragraph.inlines[0])
        val italicRun = assertIs<InlineText>(paragraph.inlines[1])
        assertEquals("hel", boldRun.text)
        assertTrue(boldRun.bold)
        assertEquals("lo", italicRun.text)
        assertTrue(italicRun.italic)
        assertEquals("hello", result.box.toPlainText())
    }

    @Test
    fun `measurer wraps lines from available box width and reports content height`() {
        val wideBox = RichContentBox(
            id = "box-1",
            size = CanvasSize(width = 320f, height = 1f),
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("abcdefghijklmnopqrstuvwxyz"))))),
        )
        val narrowBox = wideBox.copy(size = wideBox.size.copy(width = 80f))
        val measurer = RichContentMeasurer()

        val wideLayout = measurer.measure(wideBox)
        val narrowLayout = measurer.measure(narrowBox)

        assertEquals(1, wideLayout.lineRects.size)
        assertTrue(narrowLayout.lineRects.size > wideLayout.lineRects.size)
        assertTrue(narrowLayout.measuredSize.height > wideLayout.measuredSize.height)
        assertEquals(80f, narrowLayout.measuredSize.width)
        assertEquals(1, narrowLayout.blockRects.size)
    }

    @Test
    fun `measurer keeps explicit line breaks as separate line metadata`() {
        val box = RichContentBox(
            id = "box-1",
            size = CanvasSize(width = 320f, height = 1f),
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("first"), InlineLineBreak, InlineText("second"))))),
        )

        val layout = RichContentMeasurer().measure(box)

        assertEquals(2, layout.lineRects.size)
        assertEquals(0, layout.lineRects[0].inlineStart)
        assertEquals(5, layout.lineRects[0].inlineEnd)
        assertEquals(6, layout.lineRects[1].inlineStart)
        assertEquals(12, layout.lineRects[1].inlineEnd)
    }


    @Test
    fun `insert inline formula splits paragraph and advances placeholder cursor`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("ab"))))),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.InsertInlineFormula(
                expression = "x^2 + y^2",
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 0, inlineOffset = 1)),
            ),
        ) as RichContentCommandResult.ContentEdited

        val paragraph = assertIs<ParagraphNode>(result.box.content.blocks.single())
        assertEquals("a", assertIs<InlineText>(paragraph.inlines[0]).text)
        assertEquals("x^2 + y^2", assertIs<InlineFormula>(paragraph.inlines[1]).expression)
        assertEquals("b", assertIs<InlineText>(paragraph.inlines[2]).text)
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 2), result.selection.start)
    }

    @Test
    fun `insert inline image keeps image inside rich content paragraph`() {
        val engine = RichContentEngine()
        val box = RichContentBox(
            id = "box-1",
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("caption"))))),
        )

        val result = engine.execute(
            box = box,
            command = RichContentCommand.InsertInlineImage(
                assetId = "asset-inline-1",
                altText = "inline alt",
                selection = TextSelection.cursor(TextCursorPosition(blockIndex = 0, inlineOffset = 7)),
            ),
        ) as RichContentCommandResult.ContentEdited

        val paragraph = assertIs<ParagraphNode>(result.box.content.blocks.single())
        val image = assertIs<InlineImage>(paragraph.inlines.last())
        assertEquals("asset-inline-1", image.assetId)
        assertEquals("inline alt", image.altText)
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 8), result.selection.start)
    }

    @Test
    fun `insert block placeholders creates rich content block variants`() {
        val engine = RichContentEngine()
        val box = RichContentBox(id = "box-1")

        val withFormula = engine.execute(
            box = box,
            command = RichContentCommand.InsertBlockFormula(expression = "E = mc^2"),
        ) as RichContentCommandResult.ContentInserted
        val withImage = engine.execute(
            box = withFormula.box,
            command = RichContentCommand.InsertBlockImage(assetId = "asset-block-1", altText = "block alt"),
        ) as RichContentCommandResult.ContentInserted
        val withTable = engine.execute(
            box = withImage.box,
            command = RichContentCommand.InsertTable(rows = 2, columns = 3),
        ) as RichContentCommandResult.ContentInserted

        assertEquals("E = mc^2", assertIs<BlockFormula>(withTable.box.content.blocks[0]).expression)
        assertEquals("asset-block-1", assertIs<BlockImage>(withTable.box.content.blocks[1]).assetId)
        val table = assertIs<TableNode>(withTable.box.content.blocks[2])
        assertEquals(2, table.rows.size)
        assertEquals(3, table.rows.first().size)
        assertTrue(table.rows.flatten().all { it.content.blocks.isEmpty() })
    }

    @Test
    fun `table cells still contain rich content`() {
        val cell = TableCell(
            content = RichContent(
                blocks = listOf(ParagraphNode(inlines = listOf(InlineText("cell", bold = true)))),
            ),
        )

        val paragraph = assertIs<ParagraphNode>(cell.content.blocks.single())
        val inline = assertIs<InlineText>(paragraph.inlines.single())
        assertEquals("cell", inline.text)
        assertTrue(inline.bold)
    }

    @Test
    fun `measurer includes conservative placeholder heights for formula image and table blocks`() {
        val content = RichContent(
            blocks = listOf(
                BlockFormula(expression = "E = mc^2"),
                BlockImage(assetId = "asset-1", altText = "diagram"),
                TableNode(rows = List(3) { listOf(TableCell()) }),
            ),
        )
        val layout = RichContentMeasurer().measure(content, availableWidth = 240f)

        val expectedTableHeight = RichContentLayoutDefaults.TableHeaderPreviewHeight +
            3 * maxOf(RichContentLayoutDefaults.TableRowPreviewHeight, RichContentLayoutDefaults.TableCellPreviewHeight)
        val expectedHeight = RichContentLayoutDefaults.VerticalPadding * 2 +
            RichContentLayoutDefaults.FormulaCardHeight +
            RichContentLayoutDefaults.ImageCardHeight +
            expectedTableHeight +
            RichContentLayoutDefaults.BlockSpacing * 2

        assertEquals(3, layout.blockRects.size)
        assertEquals(expectedHeight, layout.measuredSize.height)
        assertEquals(RichContentLayoutDefaults.FormulaCardHeight, layout.lineRects[0].rect.bottom - layout.lineRects[0].rect.top)
        assertEquals(RichContentLayoutDefaults.ImageCardHeight, layout.lineRects[1].rect.bottom - layout.lineRects[1].rect.top)
        assertEquals(expectedTableHeight, layout.lineRects[2].rect.bottom - layout.lineRects[2].rect.top)
    }

    @Test
    fun `resizeBoxToMeasuredContent keeps wrapped multiline text above minimum box height`() {
        val box = RichContentBox(
            id = "box-1",
            size = CanvasSize(width = 88f, height = 1f),
            content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText("one two three four five six"))))),
        )

        val resized = RichContentMeasurer().resizeBoxToMeasuredContent(box)

        assertEquals(box.size.width, resized.size.width)
        assertTrue(resized.size.height >= RichContentLayoutDefaults.MinimumBoxHeight)
        assertTrue(resized.size.height > RichContentLayoutDefaults.VerticalPadding * 2 + RichContentLayoutDefaults.LineHeight)
    }

}

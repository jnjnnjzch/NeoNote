package com.neonote

import com.neonote.engine.RichContentCommand
import com.neonote.engine.RichContentCommandResult
import com.neonote.engine.RichContentEngine
import com.neonote.engine.toPlainText
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
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
}

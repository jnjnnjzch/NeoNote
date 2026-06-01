package com.neonote

import com.neonote.engine.RichContentCommand
import com.neonote.engine.RichContentCommandResult
import com.neonote.engine.RichContentEngine
import com.neonote.engine.RichContentMeasurer
import com.neonote.engine.RichContentEditorSession
import com.neonote.engine.toPlainText
import com.neonote.engine.TypingStyle
import com.neonote.model.CanvasSize
import com.neonote.model.InlineLineBreak
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

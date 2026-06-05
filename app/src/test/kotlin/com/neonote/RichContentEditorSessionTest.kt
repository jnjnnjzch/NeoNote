package com.neonote

import com.neonote.engine.InlineStyle
import com.neonote.engine.RichContentCommand
import com.neonote.engine.RichContentEditorSession
import com.neonote.engine.toPlainText
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TextCursorPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RichContentEditorSessionTest {
    @Test
    fun `basic typing is translated to insert text command`() {
        val session = RichContentEditorSession(emptyBox())

        val edit = session.replaceFromPlatformInput(
            previousText = "",
            nextText = "hello",
            selectionStartPlainOffset = 5,
        )

        assertEquals("hello", edit.box.toPlainText())
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 5), edit.selection.start)
        val command = assertIs<RichContentCommand.InsertText>(edit.commands.single())
        assertEquals("hello", command.text)
        assertEquals(listOf(command), session.pendingCommands)
    }

    @Test
    fun `enter is translated to insert paragraph command`() {
        val session = RichContentEditorSession(boxWithText("hello"))
        session.setSelectionFromPlainOffsets(2)

        val edit = session.replaceFromPlatformInput(
            previousText = "hello",
            nextText = "he\nllo",
            selectionStartPlainOffset = 3,
        )

        assertEquals("he\nllo", edit.box.toPlainText())
        assertEquals(2, edit.box.content.blocks.size)
        assertIs<RichContentCommand.InsertParagraph>(edit.commands.single())
        assertEquals(TextCursorPosition(blockIndex = 1, inlineOffset = 0), edit.selection.start)
    }

    @Test
    fun `backspace is translated to delete backward command`() {
        val session = RichContentEditorSession(boxWithText("hello"))
        session.setSelectionFromPlainOffsets(3)

        val edit = session.replaceFromPlatformInput(
            previousText = "hello",
            nextText = "helo",
            selectionStartPlainOffset = 2,
        )

        assertEquals("helo", edit.box.toPlainText())
        assertIs<RichContentCommand.DeleteBackward>(edit.commands.single())
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 2), edit.selection.start)
    }

    @Test
    fun `active ime composition uses documented plain text fallback`() {
        val session = RichContentEditorSession(boxWithText("ni"))

        val edit = session.replaceFromPlatformCompositionFallback(
            nextText = "你",
            selectionStartPlainOffset = 1,
        )

        assertEquals("你", edit.box.toPlainText())
        assertIs<RichContentCommand.ReplacePlainText>(edit.commands.single())
        assertTrue(session.localEditableBuffer == "你")
    }

    @Test
    fun `platform input bridge keeps selection range on ordinary snapshots`() {
        val session = RichContentEditorSession(boxWithText("hello world"))

        val edit = session.replaceFromPlatformInput(
            previousText = "hello world",
            nextText = "hello brave world",
            selectionStartPlainOffset = 6,
            selectionEndPlainOffset = 11,
        )

        assertEquals("hello brave world", edit.box.toPlainText())
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 6), edit.selection.start)
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 11), edit.selection.end)
        assertIs<RichContentCommand.InsertText>(edit.commands.single())
    }

    @Test
    fun `platform input bridge uses composition fallback while preserving selection range`() {
        val session = RichContentEditorSession(boxWithText("ni hao"))

        val edit = session.replaceFromPlatformCompositionFallback(
            nextText = "你好",
            selectionStartPlainOffset = 0,
            selectionEndPlainOffset = 2,
        )

        assertEquals("你好", edit.box.toPlainText())
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 0), edit.selection.start)
        assertEquals(TextCursorPosition(blockIndex = 0, inlineOffset = 2), edit.selection.end)
        assertIs<RichContentCommand.ReplacePlainText>(edit.commands.single())
        assertTrue(session.localEditableBuffer == "你好")
    }

    @Test
    fun `collapsed style shortcut updates typing style and applies to following input`() {
        val session = RichContentEditorSession(boxWithText("hello "))
        session.setSelectionFromPlainOffsets(6)

        val styleEdit = session.toggleStyle(InlineStyle.Bold)

        assertTrue(styleEdit.commands.isEmpty())
        assertTrue(session.typingStyle.bold)
        assertTrue(session.pendingCommands.isEmpty())

        val edit = session.replaceFromPlatformInput(
            previousText = "hello ",
            nextText = "hello world",
            selectionStartPlainOffset = 11,
        )

        val paragraph = assertIs<ParagraphNode>(edit.box.content.blocks.single())
        val plain = assertIs<InlineText>(paragraph.inlines[0])
        val typed = assertIs<InlineText>(paragraph.inlines[1])
        assertEquals("hello ", plain.text)
        assertFalse(plain.bold)
        assertEquals("world", typed.text)
        assertTrue(typed.bold)
        assertIs<RichContentCommand.InsertText>(edit.commands.single())
    }

    @Test
    fun `collapsed italic and underline shortcuts apply to following input`() {
        val session = RichContentEditorSession(boxWithText("hello "))
        session.setSelectionFromPlainOffsets(6)

        assertTrue(session.toggleStyle(InlineStyle.Italic).commands.isEmpty())
        assertTrue(session.toggleStyle(InlineStyle.Underline).commands.isEmpty())

        val edit = session.replaceFromPlatformInput(
            previousText = "hello ",
            nextText = "hello world",
            selectionStartPlainOffset = 11,
        )

        val paragraph = assertIs<ParagraphNode>(edit.box.content.blocks.single())
        val typed = assertIs<InlineText>(paragraph.inlines[1])
        assertEquals("world", typed.text)
        assertFalse(typed.bold)
        assertTrue(typed.italic)
        assertTrue(typed.underline)
    }

    @Test
    fun `blur commit drains pending commands without losing local buffer`() {
        val session = RichContentEditorSession(emptyBox())
        session.replaceFromPlatformInput(previousText = "", nextText = "a", selectionStartPlainOffset = 1)

        val commit = session.blurCommit()

        assertEquals("a", commit.box.toPlainText())
        assertEquals("a", session.localEditableBuffer)
        assertEquals(1, commit.committedCommands.size)
        assertTrue(session.pendingCommands.isEmpty())
    }


    @Test
    fun `paragraph focus tracks active paragraph selection and local buffer`() {
        val session = RichContentEditorSession(boxWithParagraphs("first", "second"))

        session.focusParagraph(blockIndex = 1, selectionStart = 2, selectionEnd = 5)

        assertEquals(1, session.activeBlockIndex)
        assertEquals(2, session.activeParagraphSelection.start)
        assertEquals(5, session.activeParagraphSelection.end)
        assertEquals(null, session.activeParagraphCaret)
        assertEquals("second", session.localParagraphEditableBuffer)
        assertEquals(TextCursorPosition(blockIndex = 1, inlineOffset = 2), session.selection.start)
        assertEquals(TextCursorPosition(blockIndex = 1, inlineOffset = 5), session.selection.end)
    }

    @Test
    fun `paragraph local enter splits only the active paragraph`() {
        val session = RichContentEditorSession(boxWithParagraphs("alpha", "bravo"))
        session.focusParagraph(blockIndex = 1, selectionStart = 2)

        val edit = session.replaceFromPlatformParagraphInput(
            blockIndex = 1,
            previousText = "bravo",
            nextText = "br\navo",
            selectionStartOffset = 0,
        )

        assertEquals("alpha\nbr\navo", edit.box.toPlainText())
        assertEquals(3, edit.box.content.blocks.size)
        assertEquals(2, session.activeBlockIndex)
        assertEquals(TextCursorPosition(blockIndex = 2, inlineOffset = 0), edit.selection.start)
        assertIs<RichContentCommand.InsertParagraph>(edit.commands.single())
    }

    @Test
    fun `paragraph local backspace deletes within paragraph before merging blocks`() {
        val session = RichContentEditorSession(boxWithParagraphs("alpha", "bravo"))
        session.focusParagraph(blockIndex = 1, selectionStart = 3)

        val edit = session.replaceFromPlatformParagraphInput(
            blockIndex = 1,
            previousText = "bravo",
            nextText = "bavo",
            selectionStartOffset = 1,
        )

        assertEquals("alpha\nbavo", edit.box.toPlainText())
        assertEquals(2, edit.box.content.blocks.size)
        assertEquals(1, session.activeBlockIndex)
        assertEquals(TextCursorPosition(blockIndex = 1, inlineOffset = 1), edit.selection.start)
        assertIs<RichContentCommand.DeleteBackward>(edit.commands.single())
    }

    @Test
    fun `paragraph local composition fallback preserves sibling blocks`() {
        val session = RichContentEditorSession(boxWithParagraphs("title", "ni"))
        session.focusParagraph(blockIndex = 1, selectionStart = 2)

        val edit = session.replaceParagraphFromPlatformCompositionFallback(
            blockIndex = 1,
            nextText = "你",
            selectionStartOffset = 1,
        )

        assertEquals("title\n你", edit.box.toPlainText())
        assertEquals(2, edit.box.content.blocks.size)
        assertEquals(1, session.activeBlockIndex)
        assertEquals(1, session.activeParagraphCaret)
        assertIs<RichContentCommand.ReplaceParagraphText>(edit.commands.single())
    }

    private fun emptyBox(): RichContentBox = RichContentBox(id = "box-1", content = RichContent())

    private fun boxWithText(text: String): RichContentBox = RichContentBox(
        id = "box-1",
        content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText(text))))),
    )

    private fun boxWithParagraphs(vararg texts: String): RichContentBox = RichContentBox(
        id = "box-1",
        content = RichContent(blocks = texts.map { text -> ParagraphNode(inlines = listOf(InlineText(text))) }),
    )
}

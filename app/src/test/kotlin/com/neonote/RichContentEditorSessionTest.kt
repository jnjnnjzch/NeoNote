package com.neonote

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
    fun `blur commit drains pending commands without losing local buffer`() {
        val session = RichContentEditorSession(emptyBox())
        session.replaceFromPlatformInput(previousText = "", nextText = "a", selectionStartPlainOffset = 1)

        val commit = session.blurCommit()

        assertEquals("a", commit.box.toPlainText())
        assertEquals("a", session.localEditableBuffer)
        assertEquals(1, commit.committedCommands.size)
        assertTrue(session.pendingCommands.isEmpty())
    }

    private fun emptyBox(): RichContentBox = RichContentBox(id = "box-1", content = RichContent())

    private fun boxWithText(text: String): RichContentBox = RichContentBox(
        id = "box-1",
        content = RichContent(blocks = listOf(ParagraphNode(inlines = listOf(InlineText(text))))),
    )
}

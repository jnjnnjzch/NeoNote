package com.neonote

import com.neonote.engine.RichContentCommand
import com.neonote.engine.RichContentCommandResult
import com.neonote.engine.RichContentEngine
import com.neonote.engine.toPlainText
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContentBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
}

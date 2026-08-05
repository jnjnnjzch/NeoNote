package com.neonote

import com.neonote.engine.RichClipboardFragment
import com.neonote.engine.RichContentClipboardEngine
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineText
import com.neonote.model.ListItemMetadata
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class RichContentClipboardEngineTest {
    @Test
    fun `copy across paragraphs preserves styles lists and inline atoms`() {
        val content = RichContent(listOf(
            ParagraphNode(
                inlines = listOf(InlineText("ab", bold = true), InlineFormula("x^2")),
                listMetadata = ListItemMetadata(ListKind.Bullet),
            ),
            ParagraphNode(inlines = listOf(InlineImage("asset", altText = "chart"), InlineText("cd", italic = true))),
        ))

        val fragment = requireNotNull(RichContentClipboardEngine.copy(content, 1, 6))

        assertEquals(2, fragment.paragraphs.size)
        assertEquals(ListKind.Bullet, fragment.paragraphs.first().listMetadata?.kind)
        assertEquals("b", assertIs<InlineText>(fragment.paragraphs.first().inlines[0]).text)
        assertIs<InlineFormula>(fragment.paragraphs.first().inlines[1])
        assertIs<InlineImage>(fragment.paragraphs.last().inlines[0])
        assertEquals("c", assertIs<InlineText>(fragment.paragraphs.last().inlines[1]).text)
        assertEquals(true, assertIs<InlineText>(fragment.paragraphs.last().inlines[1]).italic)
    }

    @Test
    fun `replace merges destination prefix and suffix around rich fragment`() {
        val source = RichContent(listOf(ParagraphNode(listOf(InlineText("hello world")))))
        val fragment = RichClipboardFragment(listOf(
            ParagraphNode(listOf(InlineText("RED", textColorArgb = 0xFFFF0000.toInt()))),
        ))

        val result = RichContentClipboardEngine.replace(source, 6, 11, fragment)
        val paragraph = result.content.blocks.single() as ParagraphNode

        assertEquals("hello RED", paragraph.inlines.filterIsInstance<InlineText>().joinToString("") { it.text })
        assertEquals(9, result.cursorOffset)
        assertEquals(0xFFFF0000.toInt(), paragraph.inlines.filterIsInstance<InlineText>().last().textColorArgb)
    }

    @Test
    fun `cut across paragraph boundary joins remaining text`() {
        val source = RichContent(listOf(
            ParagraphNode(listOf(InlineText("abc"))),
            ParagraphNode(listOf(InlineText("def"))),
        ))

        val result = RichContentClipboardEngine.replace(source, 2, 5, RichClipboardFragment())
        val paragraph = result.content.blocks.single() as ParagraphNode

        assertEquals("abef", paragraph.inlines.filterIsInstance<InlineText>().joinToString("") { it.text })
        assertEquals(2, result.cursorOffset)
    }

    @Test
    fun `multi paragraph paste retains inserted paragraph metadata`() {
        val source = RichContent(listOf(ParagraphNode(listOf(InlineText("abcd")))))
        val fragment = RichClipboardFragment(listOf(
            ParagraphNode(listOf(InlineText("one", bold = true))),
            ParagraphNode(listOf(InlineText("two")), listMetadata = ListItemMetadata(ListKind.Numbered)),
        ))

        val result = RichContentClipboardEngine.replace(source, 2, 2, fragment)
        val paragraphs = result.content.blocks.filterIsInstance<ParagraphNode>()

        assertEquals(2, paragraphs.size)
        assertEquals("abone", paragraphs[0].inlines.filterIsInstance<InlineText>().joinToString("") { it.text })
        assertEquals("twocd", paragraphs[1].inlines.filterIsInstance<InlineText>().joinToString("") { it.text })
        assertEquals(ListKind.Numbered, paragraphs[1].listMetadata?.kind)
        assertEquals(9, result.cursorOffset)
    }

    @Test
    fun `cursor length treats inline atoms as one platform character`() {
        val source = RichContent(listOf(ParagraphNode(listOf(InlineText("ab")))))
        val fragment = RichClipboardFragment(listOf(
            ParagraphNode(listOf(InlineFormula("x^2"), InlineImage("asset"))),
        ))

        val result = RichContentClipboardEngine.replace(source, 1, 1, fragment)

        assertEquals(3, result.cursorOffset)
    }

    @Test
    fun `plain text fallback normalizes line endings`() {
        val fragment = RichClipboardFragment.fromPlainText("a\r\nb\rc")

        assertEquals(3, fragment.paragraphs.size)
        assertEquals("a\nb\nc", fragment.plainText())
    }
}

package com.neonote

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.ParagraphNode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class RichContentRendererTest {
    @Test
    fun `paragraph renderer preserves bold italic and underline spans`() {
        val paragraph = ParagraphNode(
            inlines = listOf(
                InlineText("bold", bold = true),
                InlineText(" italic", italic = true),
                InlineText(" under", underline = true),
            ),
        )

        val annotated = paragraph.toDisplayAnnotatedString()

        assertEquals("bold italic under", annotated.text)
        assertNotNull(annotated.spanStyles.singleOrNull { it.start == 0 && it.end == 4 })
            .also { assertEquals(FontWeight.Bold, it.item.fontWeight) }
        assertNotNull(annotated.spanStyles.singleOrNull { it.start == 4 && it.end == 11 })
            .also { assertEquals(FontStyle.Italic, it.item.fontStyle) }
        assertNotNull(annotated.spanStyles.singleOrNull { it.start == 11 && it.end == 17 })
            .also { assertEquals(TextDecoration.Underline, it.item.textDecoration) }
    }

    @Test
    fun `paragraph renderer emits visible chips for inline formula and image`() {
        val paragraph = ParagraphNode(
            inlines = listOf(
                InlineText("before"),
                InlineFormula("x^2"),
                InlineLineBreak,
                InlineImage(assetId = "asset-42", altText = "diagram"),
            ),
        )

        val annotated = paragraph.toDisplayAnnotatedString()

        assertEquals("before ƒ x^2 \n Image: diagram ", annotated.text)
        val chipSpans = annotated.spanStyles.filter { it.item.background != Color.Unspecified }
        assertEquals(2, chipSpans.size)
        assertTrue(annotated.text.substring(chipSpans[0].start, chipSpans[0].end).contains("ƒ x^2"))
        assertTrue(annotated.text.substring(chipSpans[1].start, chipSpans[1].end).contains("Image: diagram"))
    }

    @Test
    fun `inline placeholder helpers fall back to stable labels`() {
        assertEquals(" ƒ empty ", InlineFormula("").formulaChipText())
        assertEquals(" Image: asset:asset-42 ", InlineImage(assetId = "asset-42").imageChipText())
    }
}

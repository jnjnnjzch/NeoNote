package com.example.cahier.features.drawing.export

import com.example.cahier.core.document.CanvasPage
import com.example.cahier.core.document.ImageBlock
import com.example.cahier.core.document.TableBlock
import com.example.cahier.core.document.TableCell
import com.example.cahier.core.document.TicDocument
import org.junit.Assert.assertTrue
import org.junit.Test

class ExportComposerTest {
    @Test
    fun markdown_contains_table_image_formula_and_ink_count() {
        val doc = sampleDoc()
        val md = ExportComposer.toMarkdown("Neo", doc, 12)
        assertTrue(md.contains("| alpha `${'$'}x^2${'$'}` |"))
        assertTrue(md.contains("![image-0](assets/sample.png)"))
        assertTrue(md.contains("Finalized stroke count: 12"))
    }

    @Test
    fun html_contains_latex_span_and_escaped_text() {
        val doc = sampleDoc()
        val html = ExportComposer.toHtml("Neo<Note>", doc, 3)
        assertTrue(html.contains("<h1>Neo&lt;Note&gt;</h1>"))
        assertTrue(html.contains("class=\"latex\""))
        assertTrue(html.contains("data-latex=\"x^2\""))
        assertTrue(html.contains("Finalized stroke count: 3"))
    }

    private fun sampleDoc(): TicDocument {
        val table = TableBlock(
            rows = 1,
            columns = 1,
            cells = listOf(
                listOf(TableCell(text = "alpha", latex = "x^2"))
            )
        )
        val image = ImageBlock(assetPath = "/tmp/sample.png")
        return TicDocument(pages = listOf(CanvasPage(blocks = listOf(table, image))))
    }
}

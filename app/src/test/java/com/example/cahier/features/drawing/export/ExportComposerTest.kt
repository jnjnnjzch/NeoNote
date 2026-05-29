package com.example.cahier.features.drawing.export

import com.example.cahier.core.document.AssetManifestEntry
import com.example.cahier.core.document.CanvasPage
import com.example.cahier.core.document.FormulaBlock
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
        assertTrue(md.contains("## Layout"))
        assertTrue(md.contains("| alpha `plain formula preview: x^2` |"))
        assertTrue(md.contains("![image-0](assets/asset-1.png)"))
        assertTrue(md.contains("formula-0 (plain formula preview)"))
        assertTrue(md.contains("Finalized stroke count: 12"))
    }

    @Test
    fun html_contains_latex_span_and_escaped_text() {
        val doc = sampleDoc()
        val html = ExportComposer.toHtml("Neo<Note>", doc, 3)
        assertTrue(html.contains("<h1>Neo&lt;Note&gt;</h1>"))
        assertTrue(html.contains("class=\"canvas\""))
        assertTrue(html.contains("class=\"block formula\""))
        assertTrue(html.contains("x^2"))
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
        val image = ImageBlock(assetId = "asset-1", assetPath = "files/notes/7/assets/asset-1.png")
        val formula = FormulaBlock(source = "x^2 + y^2 = z^2", rendered = "plain formula preview: x^2 + y^2 = z^2")
        val manifest = AssetManifestEntry(
            assetId = "asset-1",
            mimeType = "image/png",
            originalName = "sample.png",
            relativePath = "files/notes/7/assets/asset-1.png",
            width = 640,
            height = 480,
            createdAt = 1_800_000_000_000L
        )
        return TicDocument(
            pages = listOf(CanvasPage(blocks = listOf(table, image, formula))),
            assetManifest = listOf(manifest)
        )
    }
}

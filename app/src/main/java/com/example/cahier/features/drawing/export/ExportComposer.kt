package com.example.cahier.features.drawing.export

import com.example.cahier.core.document.FormulaBlock
import com.example.cahier.core.document.ImageBlock
import com.example.cahier.core.document.TableBlock
import com.example.cahier.core.document.TextContainerBlock
import com.example.cahier.core.document.TicDocument
import java.io.File

object ExportComposer {
    fun toMarkdown(title: String, document: TicDocument, strokeCount: Int): String = buildString {
        val page = document.pages.firstOrNull()
        val tables = page?.blocks?.filterIsInstance<TableBlock>().orEmpty()
        val images = page?.blocks?.filterIsInstance<ImageBlock>().orEmpty()
        val formulas = page?.blocks?.filterIsInstance<FormulaBlock>().orEmpty()
        val textContainers = page?.blocks?.filterIsInstance<TextContainerBlock>().orEmpty()
        append("# ").append(title).append("\n\n")
        append("## Layout\n\n")
        append("| block | x | y | width | height |\n")
        append("| --- | ---: | ---: | ---: | ---: |\n")
        textContainers.forEachIndexed { idx, b ->
            append("| text-container-$idx | ${b.x} | ${b.y} | ${b.width} | ${b.height} |\n")
        }
        tables.forEachIndexed { idx, b ->
            append("| table-$idx | ${b.x} | ${b.y} | ${b.width} | ${b.height} |\n")
        }
        images.forEachIndexed { idx, b ->
            append("| image-$idx | ${b.x} | ${b.y} | ${b.width} | ${b.height} |\n")
        }
        formulas.forEachIndexed { idx, b ->
            append("| formula-$idx | ${b.x} | ${b.y} | ${b.width} | ${b.height} |\n")
        }
        append('\n')
        append("## Tables\n\n")
        tables.forEach { table ->
            table.cells.forEach { row ->
                append("| ")
                append(row.joinToString(" | ") { cell ->
                    val text = cell.text.ifBlank { " " }
                    val formula = cell.latex?.let { " `plain formula preview: ${it}`" } ?: ""
                    "$text$formula"
                })
                append(" |\n")
            }
            append("\n")
        }
        images.forEachIndexed { idx, img ->
            append("![image-$idx](${exportAssetReference(document, img)})\n")
        }
        if (formulas.isNotEmpty()) {
            append("\n## Formulas\n\n")
            formulas.forEachIndexed { idx, formula ->
                append("- formula-$idx (plain formula preview): `${formula.source}`\n")
            }
        }
        append("\n## Ink\n\n")
        append("Finalized stroke count: ").append(strokeCount).append("\n")
    }

    fun toHtml(title: String, document: TicDocument, strokeCount: Int): String {
        val page = document.pages.firstOrNull()
        val tables = page?.blocks?.filterIsInstance<TableBlock>().orEmpty()
        val images = page?.blocks?.filterIsInstance<ImageBlock>().orEmpty()
        val formulas = page?.blocks?.filterIsInstance<FormulaBlock>().orEmpty()
        val tablesHtml = tables.joinToString("\n") { table ->
            val rows = table.cells.joinToString("\n") { row ->
                val cols = row.joinToString("") { cell ->
                    val formula = cell.latex?.let { "<span class=\"latex plain-formula-preview\" data-latex=\"${escape(it)}\">plain formula preview: ${escape(it)}</span>" } ?: ""
                    "<td>${escape(cell.text)}$formula</td>"
                }
                "<tr>$cols</tr>"
            }
            "<div class=\"block table\" style=\"left:${table.x}px;top:${table.y}px;width:${table.width}px;height:${table.height}px;\"><table border=\"1\" cellspacing=\"0\" cellpadding=\"4\">$rows</table></div>"
        }
        val imagesHtml = images.joinToString("\n") { img ->
            "<img class=\"block image\" src=\"${escape(exportAssetReference(document, img))}\" style=\"left:${img.x}px;top:${img.y}px;width:${img.width}px;height:${img.height}px;\" />"
        }
        val formulasHtml = formulas.joinToString("\n") { formula ->
            "<div class=\"block formula\" style=\"left:${formula.x}px;top:${formula.y}px;width:${formula.width}px;height:${formula.height}px;\"><div class=\"formula-rendered plain-formula-preview\">plain formula preview</div><div class=\"formula-source\">${escape(formula.source)}</div></div>"
        }
        return """
            <html><body>
            <h1>${escape(title)}</h1>
            <style>
            .canvas { position: relative; min-height: 1200px; border: 1px solid #ddd; }
            .block { position: absolute; box-sizing: border-box; }
            .formula { border: 1px solid #aaa; padding: 6px; background: #fafafa; }
            .formula-source { color: #666; font-size: 12px; margin-top: 4px; }
            .plain-formula-preview { color: #555; font-style: italic; }
            </style>
            <h2>Canvas</h2>
            <div class="canvas">
            $tablesHtml
            $imagesHtml
            $formulasHtml
            </div>
            <h2>Ink</h2>
            <p>Finalized stroke count: $strokeCount</p>
            </body></html>
        """.trimIndent()
    }

    private fun exportAssetReference(document: TicDocument, image: ImageBlock): String {
        val manifestEntry = image.assetId?.let { assetId -> document.assetManifest.firstOrNull { it.assetId == assetId } }
        val relativePath = manifestEntry?.relativePath ?: image.assetPath.orEmpty()
        return "assets/${File(relativePath).name}"
    }

    private fun escape(raw: String): String {
        return raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
    }
}

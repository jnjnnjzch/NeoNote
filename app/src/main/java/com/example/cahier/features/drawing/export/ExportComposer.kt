package com.example.cahier.features.drawing.export

import com.example.cahier.core.document.ImageBlock
import com.example.cahier.core.document.TableBlock
import com.example.cahier.core.document.TicDocument
import java.io.File

object ExportComposer {
    fun toMarkdown(title: String, document: TicDocument, strokeCount: Int): String = buildString {
        append("# ").append(title).append("\n\n")
        append("## Tables\n\n")
        document.pages.firstOrNull()?.blocks?.filterIsInstance<TableBlock>()?.forEach { table ->
            table.cells.forEach { row ->
                append("| ")
                append(row.joinToString(" | ") { cell ->
                    val text = cell.text.ifBlank { " " }
                    val formula = cell.latex?.let { " `$${it}$`" } ?: ""
                    "$text$formula"
                })
                append(" |\n")
            }
            append("\n")
        }
        document.pages.firstOrNull()?.blocks?.filterIsInstance<ImageBlock>()?.forEachIndexed { idx, img ->
            append("![image-$idx](assets/${File(img.assetPath).name})\n")
        }
        append("\n## Ink\n\n")
        append("Finalized stroke count: ").append(strokeCount).append("\n")
    }

    fun toHtml(title: String, document: TicDocument, strokeCount: Int): String {
        val tablesHtml = document.pages.firstOrNull()?.blocks?.filterIsInstance<TableBlock>()
            ?.joinToString("\n") { table ->
                val rows = table.cells.joinToString("\n") { row ->
                    val cols = row.joinToString("") { cell ->
                        val formula = cell.latex?.let { "<span class=\"latex\" data-latex=\"${escape(it)}\">${escape(it)}</span>" } ?: ""
                        "<td>${escape(cell.text)}$formula</td>"
                    }
                    "<tr>$cols</tr>"
                }
                "<table border=\"1\" cellspacing=\"0\" cellpadding=\"4\">$rows</table>"
            } ?: ""
        val imagesHtml = document.pages.firstOrNull()?.blocks?.filterIsInstance<ImageBlock>()
            ?.joinToString("\n") { img ->
                "<img src=\"assets/${File(img.assetPath).name}\" width=\"${img.width}\" height=\"${img.height}\" />"
            } ?: ""
        return """
            <html><body>
            <h1>${escape(title)}</h1>
            <h2>Tables</h2>
            $tablesHtml
            $imagesHtml
            <h2>Ink</h2>
            <p>Finalized stroke count: $strokeCount</p>
            </body></html>
        """.trimIndent()
    }

    private fun escape(raw: String): String {
        return raw
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
    }
}

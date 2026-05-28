package com.example.cahier.features.drawing

import com.example.cahier.core.document.ImageBlock
import com.example.cahier.core.document.TableBlock
import com.example.cahier.core.document.TableCell

object StressDocumentFactory {
    fun createBlocks(
        tableCount: Int = 10,
        rowsPerTable: Int = 20,
        columnsPerTable: Int = 6,
        imageCount: Int = 50
    ): List<com.example.cahier.core.document.Block> {
        val tables = (0 until tableCount).map { i ->
            TableBlock(
                x = 64f + (i % 3) * 720f,
                y = 64f + (i / 3) * 540f,
                width = 680f,
                height = 480f,
                rows = rowsPerTable,
                columns = columnsPerTable,
                cells = List(rowsPerTable) { r ->
                    List(columnsPerTable) { c ->
                        val latex = if ((r + c) % 7 == 0) "x_${r}_${c}^2" else null
                        TableCell(text = "R${r + 1}C${c + 1}", latex = latex)
                    }
                }
            )
        }
        val images = (0 until imageCount).map { i ->
            ImageBlock(
                x = 96f + (i % 5) * 360f,
                y = 96f + (i / 5) * 280f,
                width = 280f,
                height = 180f,
                assetPath = "stress/image_$i.png"
            )
        }
        return tables + images
    }
}

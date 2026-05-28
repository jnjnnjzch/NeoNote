package com.example.cahier.core.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TicDocument(
    val version: Int = CURRENT_VERSION,
    val pages: List<CanvasPage> = listOf(CanvasPage())
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class CanvasPage(
    val id: String = UUID.randomUUID().toString(),
    val blocks: List<Block> = emptyList(),
    val inkLayer: InkLayerRef = InkLayerRef()
)

@Serializable
data class InkLayerRef(
    val source: String = "note_strokes_data_v1"
)

@Serializable
sealed interface Block {
    val id: String
    val x: Float
    val y: Float
    val width: Float
    val height: Float
}

@Serializable
@SerialName("table")
data class TableBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val x: Float = 64f,
    override val y: Float = 64f,
    override val width: Float = 640f,
    override val height: Float = 320f,
    val rows: Int = 3,
    val columns: Int = 3,
    val cells: List<List<TableCell>> = List(3) { List(3) { TableCell() } },
) : Block

@Serializable
data class TableCell(
    val text: String = "",
    val bold: Boolean = false
)

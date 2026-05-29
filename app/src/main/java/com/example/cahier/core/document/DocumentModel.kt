package com.example.cahier.core.document

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class TicDocument(
    val version: Int = CURRENT_VERSION,
    val pages: List<CanvasPage> = listOf(CanvasPage()),
    val settings: DocumentSettings = DocumentSettings(),
    val revision: Long = 0L
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class DocumentSettings(
    val pressureCurve: Float = 1.0f,
    val stylusWritesByDefault: Boolean = true,
    val fingerPansByDefault: Boolean = true
)

@Serializable
data class CanvasPage(
    val id: String = UUID.randomUUID().toString(),
    val blocks: List<Block> = emptyList(),
    val inkLayer: InkLayerRef = InkLayerRef(),
    val strokeIds: List<String> = emptyList(),
    val strokeAnchors: List<StrokeAnchor> = emptyList(),
    val strokeTransforms: List<StrokeTransform> = emptyList(),
)

@Serializable
data class InkLayerRef(
    val source: String = "note_strokes_data_v1",
    val documentRevision: Long = 0L,
    val strokeCount: Int = 0,
)

@Serializable
data class StrokeAnchor(
    val blockId: String,
    val strokeIds: List<String> = emptyList(),
    val startStrokeIndex: Int? = null,
    val endStrokeIndexInclusive: Int? = null,
    val anchorOriginX: Float,
    val anchorOriginY: Float,
)


@Serializable
data class StrokeTransform(
    val strokeId: String,
    val translateX: Float = 0f,
    val translateY: Float = 0f,
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
sealed interface ContentNode

@Serializable
@SerialName("paragraph")
data class ParagraphNode(
    val text: String = ""
) : ContentNode

@Serializable
@SerialName("table_node")
data class TableNode(
    val rows: Int = 3,
    val columns: Int = 3,
    val cells: List<List<TableCell>> = List(3) { List(3) { TableCell() } }
) : ContentNode

@Serializable
@SerialName("formula_node")
data class FormulaNode(
    val source: String = ""
) : ContentNode

@Serializable
@SerialName("image_node")
data class ImageNode(
    val assetPath: String
) : ContentNode

@Serializable
data class TextContainerContent(
    val nodes: List<ContentNode> = listOf(ParagraphNode())
)

@Serializable
@SerialName("text_container")
data class TextContainerBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val x: Float = 96f,
    override val y: Float = 96f,
    override val width: Float = 720f,
    override val height: Float = 420f,
    val content: TextContainerContent = TextContainerContent(),
) : Block

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
@SerialName("image")
data class ImageBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val x: Float = 64f,
    override val y: Float = 64f,
    override val width: Float = 320f,
    override val height: Float = 240f,
    val assetPath: String
) : Block

@Serializable
@SerialName("formula")
data class FormulaBlock(
    override val id: String = UUID.randomUUID().toString(),
    override val x: Float = 96f,
    override val y: Float = 96f,
    override val width: Float = 360f,
    override val height: Float = 96f,
    val source: String,
    val rendered: String = source
) : Block

@Serializable
data class TableCell(
    val text: String = "",
    val bold: Boolean = false,
    val italic: Boolean = false,
    val underline: Boolean = false,
    val imageUri: String? = null,
    val latex: String? = null,
)

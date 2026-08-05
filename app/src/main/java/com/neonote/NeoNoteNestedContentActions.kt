package com.neonote

import com.neonote.engine.RichContentTree
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.FormulaDisplayMode
import com.neonote.model.ImageCrop
import com.neonote.model.RichContent
import com.neonote.model.TableCellAddress

internal fun NeoNoteEditorController.insertNestedFormula(
    boxId: String,
    cellAddress: TableCellAddress,
    expression: String = "",
) {
    mutateNestedContent(boxId, cellAddress) { content ->
        RichContentTree.insertBlock(
            content,
            cellAddress,
            BlockFormula(expression = expression),
            cellAddress.contentBlockIndex,
        )
    }
}

internal fun NeoNoteEditorController.updateNestedFormula(
    boxId: String,
    address: TableCellAddress,
    expression: String? = null,
    displayMode: FormulaDisplayMode? = null,
    numbered: Boolean? = null,
) {
    mutateNestedContent(boxId, address) { content ->
        val formula = RichContentTree.block(content, address) as? BlockFormula ?: return@mutateNestedContent content
        RichContentTree.replaceBlock(
            content,
            address,
            formula.copy(
                expression = expression ?: formula.expression,
                displayMode = displayMode ?: formula.displayMode,
                numbered = numbered ?: formula.numbered,
            ),
        )
    }
}

internal fun NeoNoteEditorController.insertNestedImage(
    boxId: String,
    cellAddress: TableCellAddress,
    assetId: String,
    altText: String? = null,
) {
    mutateNestedContent(boxId, cellAddress) { content ->
        RichContentTree.insertBlock(
            content,
            cellAddress,
            BlockImage(assetId = assetId, altText = altText),
            cellAddress.contentBlockIndex,
        )
    }
}

internal fun NeoNoteEditorController.updateNestedImage(
    boxId: String,
    address: TableCellAddress,
    width: Float? = null,
    height: Float? = null,
    rotationDegrees: Float? = null,
    crop: ImageCrop? = null,
    caption: String? = null,
    replacementAssetId: String? = null,
) {
    mutateNestedContent(boxId, address) { content ->
        val image = RichContentTree.block(content, address) as? BlockImage ?: return@mutateNestedContent content
        RichContentTree.replaceBlock(
            content,
            address,
            image.copy(
                assetId = replacementAssetId ?: image.assetId,
                width = width ?: image.width,
                height = height ?: image.height,
                rotationDegrees = rotationDegrees ?: image.rotationDegrees,
                crop = (crop ?: image.crop).normalized(),
                caption = caption ?: image.caption,
            ),
        )
    }
}

internal fun NeoNoteEditorController.deleteNestedBlock(
    boxId: String,
    address: TableCellAddress,
) {
    mutateNestedContent(boxId, address) { content -> RichContentTree.deleteBlock(content, address) }
}

private inline fun NeoNoteEditorController.mutateNestedContent(
    boxId: String,
    address: TableCellAddress,
    crossinline transform: (RichContent) -> RichContent,
) {
    setTool(com.neonote.model.EditorTool.Text)
    if (!mutateRichContentBoxContent(boxId) { content -> transform(content) }) return
    focusRichContentTableCell(boxId, address)
}

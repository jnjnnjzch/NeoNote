package com.neonote

import com.neonote.engine.RichContentMeasurer
import com.neonote.engine.RichContentTree
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.FormulaDisplayMode
import com.neonote.model.ImageCrop
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
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
    transform: (RichContent) -> RichContent,
) {
    val originalState = state
    val pageId = originalState.currentPageId ?: return
    val pageIndex = originalState.document.pages.indexOfFirst { it.id == pageId }
    if (pageIndex < 0) return
    val page = originalState.document.pages[pageIndex]
    var changed = false
    val objects = page.canvas.objects.map { objectValue ->
        if (objectValue !is RichContentBox || objectValue.id != boxId || objectValue.isLocked) return@map objectValue
        val nextContent = transform(objectValue.content)
        if (nextContent == objectValue.content) return@map objectValue
        changed = true
        val updated = objectValue.copy(content = nextContent)
        if (updated.autoSizeHeight) RichContentMeasurer().resizeBoxToMeasuredContent(updated) else updated
    }
    if (!changed) return
    val now = System.currentTimeMillis()
    val nextPage = page.copy(
        canvas = page.canvas.copy(objects = objects),
        updatedAtEpochMillis = now,
    )
    val nextDocument = originalState.document.copy(
        pages = originalState.document.pages.mapIndexed { index, current -> if (index == pageIndex) nextPage else current },
        revision = originalState.document.revision + 1,
        updatedAtEpochMillis = now,
    )
    replaceDocument(nextDocument, recordHistory = true)
    if (state.currentPageId != pageId) switchPage(pageId)
    setTool(com.neonote.model.EditorTool.Text)
    focusRichContentTableCell(boxId, address)
}

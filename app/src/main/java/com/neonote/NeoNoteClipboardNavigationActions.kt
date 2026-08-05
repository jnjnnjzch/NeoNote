package com.neonote

import com.neonote.engine.RichClipboardFragment
import com.neonote.engine.RichContentClipboardEngine
import com.neonote.engine.RichContentReplacement
import com.neonote.engine.RichContentTree
import com.neonote.engine.TableNavigationEngine
import com.neonote.model.RichContentBox
import com.neonote.model.TableCellAddress

internal object NeoNoteRichClipboardCache {
    private var plainText: String? = null
    private var fragment: RichClipboardFragment? = null

    fun store(text: String, value: RichClipboardFragment) {
        plainText = text
        fragment = value
    }

    fun resolve(text: String): RichClipboardFragment? =
        fragment?.takeIf { plainText == text }
}

internal fun NeoNoteEditorController.replaceRichContentSelection(
    boxId: String,
    start: Int,
    end: Int,
    fragment: RichClipboardFragment,
): RichContentReplacement? {
    var replacement: RichContentReplacement? = null
    val changed = mutateRichContentBoxContent(boxId, keepFocused = true) { content ->
        RichContentClipboardEngine.replace(content, start, end, fragment)
            .also { replacement = it }
            .content
    }
    return replacement?.takeIf { changed }
}

internal fun NeoNoteEditorController.navigateRichContentTableCell(
    boxId: String,
    address: TableCellAddress,
    backwards: Boolean,
) {
    val box = currentCanvas.objects.filterIsInstance<RichContentBox>().firstOrNull { it.id == boxId } ?: return
    val table = RichContentTree.table(box.content, address) ?: return
    val step = address.path.last()
    val navigation = TableNavigationEngine.navigate(table, step.rowIndex, step.columnIndex, backwards)
    val target = address.withNavigatedCell(navigation.rowIndex, navigation.columnIndex)
    if (navigation.table != table) {
        if (!mutateRichContentBoxContent(boxId, keepFocused = true) { content ->
                RichContentTree.updateTable(content, address) { navigation.table }
            }) return
    }
    focusRichContentTableCell(boxId, target)
}

private fun TableCellAddress.withNavigatedCell(row: Int, column: Int): TableCellAddress =
    if (nestedPath.isEmpty()) copy(rowIndex = row, columnIndex = column)
    else copy(nestedPath = nestedPath.dropLast(1) + nestedPath.last().copy(rowIndex = row, columnIndex = column))

from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Missing exact anchor in {path}: {old[:120]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    "import com.neonote.model.TableCellAddress\n",
    "import com.neonote.model.TableCell\nimport com.neonote.model.TableCellAddress\nimport com.neonote.model.TableNode\n",
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    "private const val MaximumDocumentTitleLength = 120\n",
    "private const val MaximumDocumentTitleLength = 120\nprivate const val MaximumTableRows = 50\nprivate const val MaximumTableColumns = 20\n",
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    '''    public fun insertRichContentFormulaPlaceholder(boxId: String, expression: String = "") {''',
    '''    public fun addActiveRichContentTableRow(boxId: String) {
        val target = activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell ?: return
        val blockIndex = target.address.blockIndex
        var nextBox: RichContentBox? = null
        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val table = box.content.blocks.getOrNull(blockIndex) as? TableNode ?: return@updateRichContentBox box
            if (table.rows.size >= MaximumTableRows) return@updateRichContentBox box
            val columnCount = (table.rows.maxOfOrNull { it.size } ?: 0).coerceAtLeast(1)
            val normalizedRows = table.rows.map { row ->
                row + List((columnCount - row.size).coerceAtLeast(0)) { TableCell() }
            }
            val updatedTable = table.copy(
                rows = normalizedRows + listOf(List(columnCount) { TableCell() }),
            )
            val updatedBox = box.copy(
                content = RichContent(
                    blocks = box.content.blocks.mapIndexed { index, block ->
                        if (index == blockIndex) updatedTable else block
                    },
                ),
            )
            richContentMeasurer.resizeBoxToMeasuredContent(updatedBox).also { nextBox = it }
        }
        nextBox?.let { editorSessionFor(boxId, it).focus(it) }
        state = state.copy(
            document = state.document.withCanvas(updatedCanvas.setFocusedRichContentBox(boxId)),
            focusedRichContentBoxId = boxId,
            currentTool = EditorTool.Text,
        )
    }

    public fun addActiveRichContentTableColumn(boxId: String) {
        val target = activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell ?: return
        val blockIndex = target.address.blockIndex
        var nextBox: RichContentBox? = null
        val updatedCanvas = currentCanvas.updateRichContentBox(boxId) { box ->
            val table = box.content.blocks.getOrNull(blockIndex) as? TableNode ?: return@updateRichContentBox box
            val currentColumnCount = table.rows.maxOfOrNull { it.size } ?: 0
            if (currentColumnCount >= MaximumTableColumns) return@updateRichContentBox box
            val sourceRows = table.rows.ifEmpty { listOf(emptyList()) }
            val updatedTable = table.copy(rows = sourceRows.map { row -> row + TableCell() })
            val updatedBox = box.copy(
                content = RichContent(
                    blocks = box.content.blocks.mapIndexed { index, block ->
                        if (index == blockIndex) updatedTable else block
                    },
                ),
            )
            richContentMeasurer.resizeBoxToMeasuredContent(updatedBox).also { nextBox = it }
        }
        nextBox?.let { editorSessionFor(boxId, it).focus(it) }
        state = state.copy(
            document = state.document.withCanvas(updatedCanvas.setFocusedRichContentBox(boxId)),
            focusedRichContentBoxId = boxId,
            currentTool = EditorTool.Text,
        )
    }

    public fun insertRichContentFormulaPlaceholder(boxId: String, expression: String = "") {''',
)

replace_once(
    "app/src/main/java/com/neonote/RichContentToolbar.kt",
    "import com.neonote.engine.AssetDraft\n",
    "import com.neonote.engine.ActiveRichContentTarget\nimport com.neonote.engine.AssetDraft\n",
)
replace_once(
    "app/src/main/java/com/neonote/RichContentToolbar.kt",
    '''    val scrollState = rememberScrollState()
    val assetStore = remember(context) {''',
    '''    val scrollState = rememberScrollState()
    val activeTableCell = controller.activeRichContentTarget(boxId) as? ActiveRichContentTarget.TableCell
    val assetStore = remember(context) {''',
)
replace_once(
    "app/src/main/java/com/neonote/RichContentToolbar.kt",
    '''            ToolbarAction(label = "Image", contentDescription = "Import image", compact = false) {
                imagePicker.launch("image/*")
            }''',
    '''            ToolbarAction(label = "Image", contentDescription = "Import image", compact = false) {
                imagePicker.launch("image/*")
            }
            if (activeTableCell != null) {
                ToolbarDivider()
                ToolbarAction(label = "+ Row", contentDescription = "Add table row", compact = false) {
                    controller.addActiveRichContentTableRow(boxId)
                }
                ToolbarAction(label = "+ Col", contentDescription = "Add table column", compact = false) {
                    controller.addActiveRichContentTableColumn(boxId)
                }
            }''',
)

print("Active table row and column controls applied")

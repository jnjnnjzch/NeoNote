from pathlib import Path


def replace_between(path: str, start_marker: str, end_marker: str, replacement: str) -> None:
    file = Path(path)
    text = file.read_text()
    start = text.find(start_marker)
    if start < 0:
        raise SystemExit(f"Start marker not found in {path}: {start_marker!r}")
    end = text.find(end_marker, start)
    if end < 0:
        raise SystemExit(f"End marker not found in {path}: {end_marker!r}")
    file.write_text(text[:start] + replacement + text[end:])


replace_between(
    "app/src/main/java/com/neonote/engine/RichContentEngine.kt",
    "    public fun replaceTableCellParagraphText(",
    "    public fun insertInlineFormula(",
    '''    public fun replaceTableCellParagraphText(
        box: RichContentBox,
        address: TableCellAddress,
        text: String,
    ): RichContentCommandResult.ContentEdited {
        require(address.blockIndex in box.content.blocks.indices) {
            "blockIndex must address an existing block"
        }
        val table = box.content.blocks[address.blockIndex] as? TableNode
            ?: return RichContentCommandResult.ContentEdited(
                box = box,
                selection = TextSelection.cursor(
                    TextCursorPosition(blockIndex = address.blockIndex, inlineOffset = 0),
                ),
            )
        require(address.rowIndex in table.rows.indices) {
            "rowIndex must address an existing row"
        }
        val row = table.rows[address.rowIndex]
        require(address.columnIndex in row.indices) {
            "columnIndex must address an existing cell"
        }

        val normalizedText = text.normalizePlainTextNewlines()
        val cell = row[address.columnIndex]
        val cellBlocks = cell.content.blocks
        require(address.contentBlockIndex in 0..cellBlocks.size) {
            "contentBlockIndex must address an existing block or the append position"
        }

        val existingBlock = cellBlocks.getOrNull(address.contentBlockIndex)
        if (existingBlock != null && existingBlock !is ParagraphNode) {
            return RichContentCommandResult.ContentEdited(
                box = box,
                selection = TextSelection.cursor(
                    TextCursorPosition(blockIndex = address.blockIndex, inlineOffset = 0),
                ),
            )
        }

        val paragraph = (existingBlock as? ParagraphNode)
            ?.copy(inlines = listOf(InlineText(normalizedText)))
            ?: ParagraphNode(inlines = listOf(InlineText(normalizedText)))
        val updatedCellBlocks = if (address.contentBlockIndex == cellBlocks.size) {
            cellBlocks + paragraph
        } else {
            cellBlocks.replaceAt(address.contentBlockIndex, paragraph)
        }
        val updatedCell = cell.copy(content = RichContent(blocks = updatedCellBlocks))
        val updatedRow = row.replaceAt(address.columnIndex, updatedCell)
        val updatedTable = table.copy(rows = table.rows.replaceAt(address.rowIndex, updatedRow))
        val updatedBlocks = box.content.blocks.replaceAt(address.blockIndex, updatedTable)

        return RichContentCommandResult.ContentEdited(
            box = box.copy(content = RichContent(blocks = updatedBlocks)),
            selection = TextSelection.cursor(
                TextCursorPosition(blockIndex = address.blockIndex, inlineOffset = 0),
            ),
        )
    }

''',
)

replace_between(
    "app/src/main/java/com/neonote/engine/RichContentEditorSession.kt",
    "    public fun tableCellPlainText(",
    "    public fun drainPendingCommands(",
    '''    public fun tableCellPlainText(address: TableCellAddress): String =
        ((box.content.blocks.getOrNull(address.blockIndex) as? TableNode)
            ?.rows
            ?.getOrNull(address.rowIndex)
            ?.getOrNull(address.columnIndex)
            ?.content
            ?.blocks
            ?.getOrNull(address.contentBlockIndex) as? ParagraphNode)
            ?.plainText()
            .orEmpty()

''',
)

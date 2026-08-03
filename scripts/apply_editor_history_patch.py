from pathlib import Path


path = Path("app/src/main/java/com/neonote/NeoNoteEditorController.kt")
text = path.read_text()


def replace_once(old: str, new: str) -> None:
    global text
    if old not in text:
        raise SystemExit(f"Expected controller fragment not found: {old[:80]!r}")
    text = text.replace(old, new, 1)


replace_once(
    "import com.neonote.model.TableCellAddress\n",
    "import com.neonote.model.TableCellAddress\nimport java.util.ArrayDeque\n",
)
replace_once(
    "private const val DefaultInputDiagnosticsThrottleMillis = 32L\n",
    "private const val DefaultInputDiagnosticsThrottleMillis = 32L\nprivate const val HistoryLimit = 100\n",
)
replace_once(
    '''    public var state: EditorState by mutableStateOf(initialState)
        private set
''',
    '''    private val undoDocuments: ArrayDeque<NeoNoteDocument> = ArrayDeque()
    private val redoDocuments: ArrayDeque<NeoNoteDocument> = ArrayDeque()
    private var historyMutationInProgress: Boolean = false
    private var stateBacking: EditorState by mutableStateOf(initialState)

    public var state: EditorState
        get() = stateBacking
        private set(value) {
            val previousDocument = stateBacking.document.historySnapshot()
            val nextDocument = value.document.historySnapshot()
            if (
                !historyMutationInProgress &&
                !previousDocument.hasSameHistoryContentAs(nextDocument)
            ) {
                undoDocuments.addLast(previousDocument)
                undoDocuments.trimToHistoryLimit()
                redoDocuments.clear()
            }
            stateBacking = value
        }
''',
)
replace_once(
    '''    public val canSwitchToNextPage: Boolean
        get() = currentPageIndex < pageCount - 1

    private var activeSelectionGesture: ActiveSelectionGesture? by mutableStateOf(null)
''',
    '''    public val canSwitchToNextPage: Boolean
        get() = currentPageIndex < pageCount - 1

    public val canUndo: Boolean
        get() = undoDocuments.isNotEmpty()

    public val canRedo: Boolean
        get() = redoDocuments.isNotEmpty()

    private var activeSelectionGesture: ActiveSelectionGesture? by mutableStateOf(null)
''',
)
replace_once(
    '''    public suspend fun saveDocument(store: PersistenceStore): PersistenceResult.Saved {
''',
    '''    public fun undo() {
        if (undoDocuments.isEmpty()) return
        val previousDocument = undoDocuments.removeLast()
        redoDocuments.addLast(state.document.historySnapshot())
        redoDocuments.trimToHistoryLimit()
        restoreDocumentFromHistory(previousDocument)
    }

    public fun redo() {
        if (redoDocuments.isEmpty()) return
        val nextDocument = redoDocuments.removeLast()
        undoDocuments.addLast(state.document.historySnapshot())
        undoDocuments.trimToHistoryLimit()
        restoreDocumentFromHistory(nextDocument)
    }

    public suspend fun saveDocument(store: PersistenceStore): PersistenceResult.Saved {
''',
)
replace_once(
    '''        val nextPageId = document.pages.firstOrNull()?.id
        state = state.copy(
            document = document.withMeasuredRichContentBoxHeights(),
            currentPageId = nextPageId,
            focusedRichContentBoxId = null,
            selection = SelectionState(),
        )
''',
    '''        val nextPageId = document.pages.firstOrNull()?.id
        replaceStateWithoutRecordingHistory {
            state = state.copy(
                document = document.withMeasuredRichContentBoxHeights(),
                currentPageId = nextPageId,
                focusedRichContentBoxId = null,
                selection = SelectionState(),
            )
        }
        undoDocuments.clear()
        redoDocuments.clear()
        richContentSessions.clear()
        selectedRichContentObjectBlocks.clear()
''',
)
replace_once(
    '''    private fun nextRichContentBoxId(): String {
''',
    '''    private fun restoreDocumentFromHistory(document: NeoNoteDocument) {
        val measuredDocument = document.withMeasuredRichContentBoxHeights()
        val nextPageId = state.currentPageId
            .takeIf { currentId -> measuredDocument.pages.any { it.id == currentId } }
            ?: measuredDocument.pages.firstOrNull()?.id

        replaceStateWithoutRecordingHistory {
            state = state.copy(
                document = measuredDocument,
                currentPageId = nextPageId,
                focusedRichContentBoxId = null,
                selection = SelectionState(),
                currentTool = EditorTool.Text,
            )
        }
        richContentSessions.clear()
        selectedRichContentObjectBlocks.clear()
        activeSelectionGesture = null
        richContentInteractionRevision++
        inkSession = measuredDocument.pages
            .firstOrNull { it.id == nextPageId }
            ?.canvas
            ?.let(InkSession::fromCanvas)
            ?: InkSession()
    }

    private inline fun replaceStateWithoutRecordingHistory(block: () -> Unit) {
        historyMutationInProgress = true
        try {
            block()
        } finally {
            historyMutationInProgress = false
        }
    }

    private fun NeoNoteDocument.historySnapshot(): NeoNoteDocument = copy(
        pages = pages.map { page ->
            page.copy(
                canvas = page.canvas.copy(
                    objects = page.canvas.objects.map { canvasObject ->
                        if (canvasObject is RichContentBox) {
                            canvasObject.copy(isFocused = false)
                        } else {
                            canvasObject
                        }
                    },
                ),
            )
        },
    )

    private fun NeoNoteDocument.hasSameHistoryContentAs(other: NeoNoteDocument): Boolean =
        copy(revision = 0L) == other.copy(revision = 0L)

    private fun ArrayDeque<NeoNoteDocument>.trimToHistoryLimit() {
        while (size > HistoryLimit) removeFirst()
    }

    private fun nextRichContentBoxId(): String {
''',
)

path.write_text(text)

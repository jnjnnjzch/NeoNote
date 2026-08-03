from __future__ import annotations

from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def replace_once(path: str, old: str, new: str) -> None:
    target = ROOT / path
    text = target.read_text(encoding="utf-8")
    if new in text:
        return
    if old not in text:
        raise RuntimeError(f"Missing exact anchor in {path}: {old[:100]!r}")
    target.write_text(text.replace(old, new, 1), encoding="utf-8")


replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    '''    public fun resetViewport() {
        state = state.copy(viewport = ViewportState())
    }

    public fun deleteSelection() {''',
    '''    public fun resetViewport() {
        state = state.copy(viewport = ViewportState())
    }

    public fun renameDocument(title: String) {
        val nextTitle = title.take(MaximumDocumentTitleLength)
        if (state.document.title == nextTitle) return
        state = state.copy(
            document = state.document.copy(
                title = nextTitle,
                revision = state.document.revision + 1,
            ),
        )
    }

    public fun deleteSelection() {''',
)

replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    "private const val HistoryLimit = 100\n",
    "private const val HistoryLimit = 100\nprivate const val MaximumDocumentTitleLength = 120\n",
)

replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorScreen.kt",
    "import androidx.compose.foundation.shape.RoundedCornerShape\n",
    "import androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.foundation.text.BasicTextField\n",
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorScreen.kt",
    "import androidx.compose.ui.graphics.Color\n",
    "import androidx.compose.ui.graphics.Color\nimport androidx.compose.ui.graphics.SolidColor\n",
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorScreen.kt",
    '''                title = state.document.title,
                saveLabel = controller.persistenceStatus.toFriendlySaveLabel(),''',
    '''                title = state.document.title,
                onTitleChange = controller::renameDocument,
                saveLabel = controller.persistenceStatus.toFriendlySaveLabel(),''',
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorScreen.kt",
    '''private fun WorkspaceTopBar(
    title: String,
    saveLabel: String,''',
    '''private fun WorkspaceTopBar(
    title: String,
    onTitleChange: (String) -> Unit,
    saveLabel: String,''',
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorScreen.kt",
    '''                Text(
                    text = title.ifBlank { "Untitled note" },
                    color = InkColor,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )''',
    '''                BasicTextField(
                    value = title,
                    onValueChange = onTitleChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.titleMedium.copy(
                        color = InkColor,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    cursorBrush = SolidColor(NeoPurple),
                    modifier = Modifier.fillMaxWidth(),
                    decorationBox = { innerTextField ->
                        Box {
                            if (title.isBlank()) {
                                Text(
                                    text = "Untitled note",
                                    color = Color(0xFF9A94A5),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            innerTextField()
                        }
                    },
                )''',
)

replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorViewModel.kt",
    "import androidx.lifecycle.ViewModel\n",
    "import androidx.lifecycle.ViewModel\nimport com.neonote.model.EditorTool\n",
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorViewModel.kt",
    '''        initialState = createTestEditorState().let { state ->
            state.copy(document = state.document.copy(title = "Untitled Note"))
        },''',
    '''        initialState = createTestEditorState().let { state ->
            state.copy(
                document = state.document.copy(title = "Untitled Note"),
                currentTool = EditorTool.Pen,
            )
        },''',
)

replace_once(
    "app/src/main/java/com/neonote/engine/RichContentLayoutEngine.kt",
    "    public const val ImageCardHeight: Float = 96f\n",
    "    public const val ImageCardHeight: Float = 220f\n",
)

print("Final product interaction patches applied")

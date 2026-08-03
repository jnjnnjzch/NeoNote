from __future__ import annotations

from pathlib import Path

path = Path(__file__).resolve().parents[1] / "app/src/main/java/com/neonote/NeoNoteEditorController.kt"
text = path.read_text(encoding="utf-8")
old = '''    private fun restoreDocumentFromHistory(document: NeoNoteDocument) {
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
'''
new = '''    private fun restoreDocumentFromHistory(document: NeoNoteDocument) {
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
            )
        }
'''
if old in text:
    path.write_text(text.replace(old, new, 1), encoding="utf-8")
elif new not in text:
    raise RuntimeError("The targeted restoreDocumentFromHistory block was not found")
print("Targeted history-state correction applied")

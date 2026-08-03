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
    '''                currentPageId = nextPageId,
                focusedRichContentBoxId = null,
                selection = SelectionState(),
                currentTool = EditorTool.Text,
            )''',
    '''                currentPageId = nextPageId,
                focusedRichContentBoxId = null,
                selection = SelectionState(),
            )''',
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    '''    private fun NeoNoteDocument.withCanvasForPage(pageId: String?, canvas: InfiniteCanvas): NeoNoteDocument = copy(
        pages = pages.map { page -> if (page.id == pageId) page.copy(canvas = canvas) else page },
        revision = revision + 1,
    )''',
    '''    private fun NeoNoteDocument.withCanvasForPage(pageId: String?, canvas: InfiniteCanvas): NeoNoteDocument {
        val current = pages.firstOrNull { it.id == pageId } ?: return this
        if (current.canvas == canvas) return this
        return copy(
            pages = pages.map { page -> if (page.id == pageId) page.copy(canvas = canvas) else page },
            revision = revision + 1,
        )
    }''',
)

print("History and transient-state separation applied")

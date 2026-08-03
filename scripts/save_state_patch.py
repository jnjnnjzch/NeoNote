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
    '''    public var persistenceDiagnostics: PersistenceDiagnostics? by mutableStateOf(null)
        private set

    public val activeInkStroke: InkStroke?''',
    '''    public var persistenceDiagnostics: PersistenceDiagnostics? by mutableStateOf(null)
        private set

    private var lastPersistedRevision: Long? by mutableStateOf(null)

    public val saveStateLabel: String
        get() = when {
            lastPersistedRevision == state.document.revision -> "Saved"
            lastPersistedRevision == null -> "Saving locally"
            else -> "Saving…"
        }

    public val activeInkStroke: InkStroke?''',
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    '''        val saved = store.save(state.document)
        persistenceDiagnostics = saved.diagnostics''',
    '''        val saved = store.save(state.document)
        lastPersistedRevision = saved.revision
        persistenceDiagnostics = saved.diagnostics''',
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    '''        if (document == null) {
            persistenceDiagnostics = null
            persistenceStatus = "No local document found for $documentId"''',
    '''        if (document == null) {
            lastPersistedRevision = null
            persistenceDiagnostics = null
            persistenceStatus = "No local document found for $documentId"''',
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorController.kt",
    '''        persistenceDiagnostics = diagnostics
        persistenceStatus = "Loaded ${document.id} at revision ${document.revision}"''',
    '''        lastPersistedRevision = document.revision
        persistenceDiagnostics = diagnostics
        persistenceStatus = "Loaded ${document.id} at revision ${document.revision}"''',
)
replace_once(
    "app/src/main/java/com/neonote/NeoNoteEditorScreen.kt",
    "                saveLabel = controller.persistenceStatus.toFriendlySaveLabel(),\n",
    "                saveLabel = controller.saveStateLabel,\n",
)

print("Revision-backed save indicator applied")

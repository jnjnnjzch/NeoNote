package com.neonote.model

/**
 * Minimal editor session state for opening a v2 document model.
 */
data class EditorState(
    val document: NeoNoteDocument,
    val currentPageId: String? = document.pages.firstOrNull()?.id,
    val selectedObjectIds: Set<String> = emptySet(),
)

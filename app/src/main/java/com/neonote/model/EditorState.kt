package com.neonote.model

/**
 * Minimal viewport state for panning and zooming around the editor canvas.
 */
data class ViewportState(
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val zoomScale: Float = 1f,
)

/**
 * Tools available in the editor.
 */
enum class EditorTool {
    Pen,
    Selection,
    Text,
    Eraser,
}

/**
 * Minimal editor session state for opening a v2 document model.
 */
data class EditorState(
    val document: NeoNoteDocument,
    val currentPageId: String? = document.pages.firstOrNull()?.id,
    val viewport: ViewportState = ViewportState(),
    val currentTool: EditorTool = EditorTool.Pen,
    val focusedRichContentBoxId: String? = null,
    val selection: SelectionState = SelectionState(),
)

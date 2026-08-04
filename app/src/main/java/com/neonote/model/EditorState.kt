package com.neonote.model

/** Viewport state for panning and zooming around the infinite canvas. */
data class ViewportState(
    val panOffsetX: Float = 0f,
    val panOffsetY: Float = 0f,
    val zoomScale: Float = 1f,
)

enum class EditorTool {
    Pen,
    Selection,
    Text,
    Eraser,
}

enum class EraserMode {
    Stroke,
    Segment,
}

/** Active pen controls. A committed stroke copies these values into its style. */
data class InkToolSettings(
    val colorArgb: Int = DefaultInkColorArgb,
    val width: Float = 3f,
    val opacity: Float = 1f,
    val pressureEnabled: Boolean = true,
    val brush: InkBrush = InkBrush.Pen,
) {
    fun normalized(): InkToolSettings = copy(
        width = width.coerceIn(0.5f, 40f),
        opacity = opacity.coerceIn(0.05f, 1f),
    )

    fun toStrokeStyle(): InkStrokeStyle = InkStrokeStyle(
        colorArgb = colorArgb,
        baseWidth = width,
        opacity = opacity,
        pressureEnabled = pressureEnabled,
        brush = brush,
    ).normalized()
}

/** Complete transient editor session state. Document content remains serializable separately. */
data class EditorState(
    val document: NeoNoteDocument,
    val currentPageId: String? = document.pages.firstOrNull()?.id,
    val viewport: ViewportState = ViewportState(),
    val currentTool: EditorTool = EditorTool.Pen,
    val focusedRichContentBoxId: String? = null,
    val selection: SelectionState = SelectionState(),
    val inkSettings: InkToolSettings = InkToolSettings(),
    val eraserMode: EraserMode = EraserMode.Segment,
)

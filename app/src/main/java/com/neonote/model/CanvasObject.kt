package com.neonote.model

/**
 * Top-level floating items that can be placed on an infinite canvas.
 */
sealed interface CanvasObject {
    val id: String
}

/**
 * Rich text/content container placed on the canvas.
 */
data class RichContentBox(
    override val id: String,
    val content: RichContent = RichContent(),
    val position: CanvasPosition = CanvasPosition(0f, 0f),
) : CanvasObject

/**
 * Floating image object placed directly on the canvas.
 */
data class FloatingImage(
    override val id: String,
    val assetId: String,
    val altText: String? = null,
    val position: CanvasPosition = CanvasPosition(0f, 0f),
) : CanvasObject

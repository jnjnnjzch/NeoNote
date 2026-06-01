package com.neonote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * A point in the shared infinite canvas coordinate system.
 */
@Serializable
data class CanvasPoint(
    val x: Float,
    val y: Float,
) {
    companion object {
        val Zero = CanvasPoint(x = 0f, y = 0f)
    }
}

/**
 * A two-dimensional size in infinite canvas units.
 */
@Serializable
data class CanvasSize(
    val width: Float,
    val height: Float,
) {
    companion object {
        val Zero = CanvasSize(width = 0f, height = 0f)
    }
}

/**
 * Rectangle bounds in the shared infinite canvas coordinate system.
 */
data class CanvasRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    fun contains(point: CanvasPoint): Boolean =
        point.x in left..right && point.y in top..bottom

    companion object {
        fun from(position: CanvasPoint, size: CanvasSize): CanvasRect = CanvasRect(
            left = position.x,
            top = position.y,
            right = position.x + size.width,
            bottom = position.y + size.height,
        )
    }
}

/**
 * Top-level floating items that can be placed on an infinite canvas.
 *
 * Geometry values are expressed only as [CanvasPoint] plus [CanvasSize] in a
 * shared infinite-canvas coordinate system. Canvas objects deliberately do not
 * expose any parallel coordinate state.
 */
@Serializable
sealed interface CanvasObject {
    val id: String
    val position: CanvasPoint
    val size: CanvasSize
    val zIndex: Int
    val bounds: CanvasRect
        get() = CanvasRect.from(position = position, size = size)
}

/**
 * Rich text/content container placed on the canvas.
 */
@Serializable
@SerialName("richContentBox")
data class RichContentBox(
    override val id: String,
    override val position: CanvasPoint = CanvasPoint.Zero,
    override val size: CanvasSize = CanvasSize.Zero,
    override val zIndex: Int = 0,
    val content: RichContent = RichContent(),
    val isFocused: Boolean = false,
) : CanvasObject

/**
 * Floating image object placed directly on the canvas.
 *
 * This is a top-level, movable/resizable canvas object. It is deliberately
 * separate from [InlineImage] and [BlockImage], which are placeholders inside a
 * RichContent document flow and do not carry independent canvas geometry.
 */
@Serializable
@SerialName("floatingImage")
data class FloatingImage(
    override val id: String,
    override val position: CanvasPoint = CanvasPoint.Zero,
    override val size: CanvasSize = CanvasSize.Zero,
    override val zIndex: Int = 0,
    val assetId: String,
    val altText: String? = null,
) : CanvasObject

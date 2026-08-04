package com.neonote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CanvasPoint(
    val x: Float,
    val y: Float,
) {
    companion object {
        val Zero = CanvasPoint(x = 0f, y = 0f)
    }
}

@Serializable
data class CanvasSize(
    val width: Float,
    val height: Float,
) {
    companion object {
        val Zero = CanvasSize(width = 0f, height = 0f)
    }
}

data class CanvasRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val center: CanvasPoint get() = CanvasPoint((left + right) / 2f, (top + bottom) / 2f)

    fun contains(point: CanvasPoint): Boolean =
        point.x in left..right && point.y in top..bottom

    fun expanded(amount: Float): CanvasRect = CanvasRect(
        left = left - amount,
        top = top - amount,
        right = right + amount,
        bottom = bottom + amount,
    )

    companion object {
        fun from(position: CanvasPoint, size: CanvasSize): CanvasRect = CanvasRect(
            left = position.x,
            top = position.y,
            right = position.x + size.width,
            bottom = position.y + size.height,
        )
    }
}

@Serializable
sealed interface CanvasObject {
    val id: String
    val position: CanvasPoint
    val size: CanvasSize
    val zIndex: Int
    val bounds: CanvasRect
        get() = CanvasRect.from(position = position, size = size)
}

@Serializable
@SerialName("richContentBox")
data class RichContentBox(
    override val id: String,
    override val position: CanvasPoint = CanvasPoint.Zero,
    override val size: CanvasSize = CanvasSize.Zero,
    override val zIndex: Int = 0,
    val content: RichContent = RichContent(),
    val isFocused: Boolean = false,
    /** Height follows content unless the user explicitly resizes the box. */
    val autoSizeHeight: Boolean = true,
    val isLocked: Boolean = false,
) : CanvasObject

@Serializable
@SerialName("floatingImage")
data class FloatingImage(
    override val id: String,
    override val position: CanvasPoint = CanvasPoint.Zero,
    override val size: CanvasSize = CanvasSize.Zero,
    override val zIndex: Int = 0,
    val assetId: String,
    val altText: String? = null,
    val rotationDegrees: Float = 0f,
    val crop: ImageCrop = ImageCrop(),
    val lockAspectRatio: Boolean = true,
    val isLocked: Boolean = false,
) : CanvasObject

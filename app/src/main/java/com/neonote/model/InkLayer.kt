package com.neonote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Dedicated vector handwriting layer, separate from rich content objects. */
@Serializable
data class InkLayer(
    val strokes: List<InkStroke> = emptyList(),
)

@Serializable
data class InkStroke(
    val id: String,
    val points: List<InkPoint> = emptyList(),
    val style: InkStrokeStyle = InkStrokeStyle(),
)

@Serializable
data class InkStrokeStyle(
    val colorArgb: Int = DefaultInkColorArgb,
    val baseWidth: Float = 3f,
    val opacity: Float = 1f,
    val pressureEnabled: Boolean = true,
    val brush: InkBrush = InkBrush.Pen,
) {
    fun normalized(): InkStrokeStyle = copy(
        baseWidth = baseWidth.coerceIn(0.5f, 40f),
        opacity = opacity.coerceIn(0.05f, 1f),
    )
}

@Serializable
enum class InkBrush {
    @SerialName("pen") Pen,
    @SerialName("highlighter") Highlighter,
}

@Serializable
data class InkPoint(
    val x: Float,
    val y: Float,
    /** Normalized pressure used by renderers, in the 0..1 range. */
    val pressure: Float = 1f,
    /** Original hardware pressure before normalization, when available. */
    val rawPressure: Float? = null,
)

public const val DefaultInkColorArgb: Int = -15264471

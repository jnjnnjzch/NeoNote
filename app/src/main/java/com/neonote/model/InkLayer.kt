package com.neonote.model

import kotlinx.serialization.Serializable

/**
 * Dedicated handwriting layer that is separate from rich content objects.
 *
 * These committed vector strokes are the source of truth for persistence,
 * selection/lasso hit testing, and rebuilding any page-level render cache.
 */
@Serializable
data class InkLayer(
    val strokes: List<InkStroke> = emptyList(),
)

@Serializable
data class InkStroke(
    val id: String,
    val points: List<InkPoint> = emptyList(),
)

@Serializable
data class InkPoint(
    val x: Float,
    val y: Float,
    /** Normalized pressure used by renderers, in the 0..1 range. */
    val pressure: Float = 1f,
    /** Original hardware pressure before normalization, when available. */
    val rawPressure: Float? = null,
)

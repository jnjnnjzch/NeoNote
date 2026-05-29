package com.neonote.model

import kotlinx.serialization.Serializable

/**
 * Infinite canvas content: positioned objects plus an ink layer.
 */
@Serializable
data class InfiniteCanvas(
    val objects: List<CanvasObject> = emptyList(),
    val inkLayer: InkLayer = InkLayer(),
)

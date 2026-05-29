package com.neonote.model

/**
 * Infinite canvas content: positioned objects plus an ink layer.
 */
data class InfiniteCanvas(
    val objects: List<CanvasObject> = emptyList(),
    val inkLayer: InkLayer = InkLayer(),
)

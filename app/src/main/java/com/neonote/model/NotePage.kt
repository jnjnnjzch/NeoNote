package com.neonote.model

/**
 * A single page backed by an infinite canvas.
 */
data class NotePage(
    val id: String,
    val canvas: InfiniteCanvas = InfiniteCanvas(),
)

package com.neonote.model

import kotlinx.serialization.Serializable

/**
 * A single page backed by an infinite canvas.
 */
@Serializable
data class NotePage(
    val id: String,
    val canvas: InfiniteCanvas = InfiniteCanvas(),
)

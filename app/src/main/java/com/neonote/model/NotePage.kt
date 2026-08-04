package com.neonote.model

import kotlinx.serialization.Serializable

/** A named page backed by an infinite canvas. */
@Serializable
data class NotePage(
    val id: String,
    val canvas: InfiniteCanvas = InfiniteCanvas(),
    val title: String = "",
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
)

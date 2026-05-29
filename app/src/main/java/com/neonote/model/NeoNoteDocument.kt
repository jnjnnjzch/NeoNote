package com.neonote.model

import kotlinx.serialization.Serializable

/**
 * Root document aggregate for the v2 NeoNote model.
 */
@Serializable
data class NeoNoteDocument(
    val id: String,
    val title: String,
    val pages: List<NotePage> = emptyList(),
    val assetStoreId: String,
    val revision: Long = 0L,
)

package com.neonote.model

/**
 * Root document aggregate for the v2 NeoNote model.
 */
data class NeoNoteDocument(
    val id: String,
    val title: String,
    val pages: List<NotePage> = emptyList(),
    val assetStoreId: String,
    val revision: Long = 0L,
)

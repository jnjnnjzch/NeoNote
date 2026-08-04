package com.neonote.model

import kotlinx.serialization.Serializable

/** Root document aggregate persisted by NeoNote. */
@Serializable
data class NeoNoteDocument(
    val id: String,
    val title: String,
    val pages: List<NotePage> = emptyList(),
    val assetStoreId: String,
    val sections: List<NoteSection> = emptyList(),
    val revision: Long = 0L,
    val schemaVersion: Int = CurrentDocumentSchemaVersion,
    val createdAtEpochMillis: Long = 0L,
    val updatedAtEpochMillis: Long = 0L,
    val isFavorite: Boolean = false,
)

public const val CurrentDocumentSchemaVersion: Int = 4

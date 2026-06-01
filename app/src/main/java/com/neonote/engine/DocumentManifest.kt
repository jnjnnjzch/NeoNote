package com.neonote.engine

import com.neonote.model.NeoNoteDocument
import kotlinx.serialization.Serializable

/**
 * Page-level document index that can be read before any page payload is loaded.
 *
 * The manifest intentionally carries only document-wide routing metadata: the document id, current
 * revision, ordered page ids, and the asset-store namespace shared by page content. Whole-document
 * JSON remains the default persistence format; this type is a forward-compatible boundary for a
 * future layout where a manifest points at separate page files.
 */
@Serializable
public data class DocumentManifest(
    val documentId: String,
    val revision: Long,
    val pageIds: List<String>,
    val assetStoreId: String,
) {
    init {
        require(documentId.isNotBlank()) { "documentId must not be blank" }
        require(pageIds.all { it.isNotBlank() }) { "pageIds must not contain blank ids" }
        require(pageIds.toSet().size == pageIds.size) { "pageIds must be unique" }
        require(assetStoreId.isNotBlank()) { "assetStoreId must not be blank" }
    }

    public companion object {
        public fun fromDocument(document: NeoNoteDocument): DocumentManifest = document.toDocumentManifest()
    }
}

public fun NeoNoteDocument.toDocumentManifest(): DocumentManifest = DocumentManifest(
    documentId = id,
    revision = revision,
    pageIds = pages.map { it.id },
    assetStoreId = assetStoreId,
)

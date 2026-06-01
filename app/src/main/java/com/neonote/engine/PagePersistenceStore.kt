package com.neonote.engine

import com.neonote.model.NotePage

/** File name reserved for future per-page binary ink payloads. */
public const val PAGE_INK_BIN_FILE_NAME: String = "page.ink.bin"

/** Format id reserved for the first binary ink payload stored outside page JSON. */
public const val PAGE_INK_BIN_FORMAT_ID: String = "neonote.page-ink.bin.v1"

/**
 * Persistence boundary for page-level storage.
 *
 * This interface is intentionally separate from [PersistenceStore] so the app can keep using the
 * existing whole-document JSON store while storage backends gradually learn to read a lightweight
 * [DocumentManifest], lazily load individual pages, and write only dirty pages.
 */
public interface PagePersistenceStore {
    /** Loads document-wide routing metadata without loading page payloads. */
    public suspend fun loadDocumentManifest(documentId: String): DocumentManifest?

    /** Loads a single page payload by id after callers have selected it from the manifest. */
    public suspend fun loadPage(documentId: String, pageId: String): PersistedPage?

    /** Saves one dirty page payload without requiring unrelated pages to be read or rewritten. */
    public suspend fun saveDirtyPage(documentId: String, page: PersistedPage): PagePersistenceResult.PageSaved

    /** Saves document-wide metadata such as revision, ordered page ids, and asset-store id. */
    public suspend fun saveManifest(manifest: DocumentManifest): PagePersistenceResult.ManifestSaved
}

/**
 * Page payload loaded or saved through [PagePersistenceStore].
 *
 * Today the ink layer is still embedded in [page] because the app's only production store remains
 * whole-document JSON. [inkStorage] reserves the boundary where a future page-level implementation
 * can point at an external [PAGE_INK_BIN_FILE_NAME] payload without changing the manifest contract.
 */
public data class PersistedPage(
    val page: NotePage,
    val inkStorage: PageInkStorage = PageInkStorage.EmbeddedInPageJson,
)

/** Describes where a page's ink data lives for page-level storage implementations. */
public sealed interface PageInkStorage {
    public val formatId: String

    /** Current model: ink is serialized inside the page JSON/model payload. */
    public data object EmbeddedInPageJson : PageInkStorage {
        override val formatId: String = "neonote.page-json.embedded-ink"
    }

    /** Future model: ink can live next to page metadata in a binary page.ink.bin payload. */
    public data class ExternalInkBinary(
        val relativePath: String = PAGE_INK_BIN_FILE_NAME,
        override val formatId: String = PAGE_INK_BIN_FORMAT_ID,
        val revision: Long? = null,
    ) : PageInkStorage
}

public sealed interface PagePersistenceResult {
    public data class PageSaved(
        val documentId: String,
        val pageId: String,
    ) : PagePersistenceResult

    public data class ManifestSaved(
        val documentId: String,
        val revision: Long,
        val pageCount: Int,
    ) : PagePersistenceResult
}

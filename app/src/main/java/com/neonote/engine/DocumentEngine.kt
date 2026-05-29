package com.neonote.engine

import com.neonote.model.EditorState
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage

/**
 * Creates NeoNote documents and manages the active page in an editor session.
 */
public class DocumentEngine(
    private val idGenerator: IdGenerator,
) {
    public fun execute(command: DocumentCommand): DocumentCommandResult = when (command) {
        is DocumentCommand.CreateDocument -> DocumentCommandResult.DocumentCreated(
            document = createDocument(
                title = command.title,
                assetStoreId = command.assetStoreId,
                documentId = command.documentId ?: idGenerator.nextId("document"),
                firstPageId = command.firstPageId ?: idGenerator.nextId("page"),
            ),
        )
        is DocumentCommand.AddPage -> addPage(command.document, command.page ?: NotePage(id = idGenerator.nextId("page")))
        is DocumentCommand.SwitchPage -> switchPage(command.state, command.pageId)
    }

    public fun createDocument(
        title: String,
        assetStoreId: String,
        documentId: String = idGenerator.nextId("document"),
        firstPageId: String = idGenerator.nextId("page"),
    ): NeoNoteDocument = NeoNoteDocument(
        id = documentId,
        title = title,
        pages = listOf(NotePage(id = firstPageId)),
        assetStoreId = assetStoreId,
    )

    public fun addPage(
        document: NeoNoteDocument,
        page: NotePage = NotePage(id = idGenerator.nextId("page")),
    ): DocumentCommandResult.PageAdded = DocumentCommandResult.PageAdded(
        document = document.copy(pages = document.pages + page, revision = document.revision + 1),
        page = page,
    )

    public fun switchPage(state: EditorState, pageId: String): DocumentCommandResult.PageSwitched {
        require(state.document.pages.any { it.id == pageId }) { "Unknown page id: $pageId" }
        return DocumentCommandResult.PageSwitched(state = state.copy(currentPageId = pageId))
    }
}

public sealed interface DocumentCommand {
    public data class CreateDocument(
        val title: String,
        val assetStoreId: String,
        val documentId: String? = null,
        val firstPageId: String? = null,
    ) : DocumentCommand

    public data class AddPage(
        val document: NeoNoteDocument,
        val page: NotePage? = null,
    ) : DocumentCommand

    public data class SwitchPage(
        val state: EditorState,
        val pageId: String,
    ) : DocumentCommand
}

public sealed interface DocumentCommandResult {
    public data class DocumentCreated(
        val document: NeoNoteDocument,
    ) : DocumentCommandResult

    public data class PageAdded(
        val document: NeoNoteDocument,
        val page: NotePage,
    ) : DocumentCommandResult

    public data class PageSwitched(
        val state: EditorState,
    ) : DocumentCommandResult
}

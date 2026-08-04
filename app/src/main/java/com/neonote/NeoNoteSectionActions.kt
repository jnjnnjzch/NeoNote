package com.neonote

import com.neonote.engine.DocumentCommand
import com.neonote.engine.DocumentCommandResult
import com.neonote.engine.DocumentEngine
import com.neonote.engine.IdGenerator
import com.neonote.model.NoteSection
import com.neonote.model.effectiveSectionId
import com.neonote.model.effectiveSections
import java.util.UUID

private val sectionDocumentEngine: DocumentEngine = DocumentEngine(object : IdGenerator {
    override fun nextId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
})

internal val NeoNoteEditorController.currentSectionId: String
    get() {
        val page = state.document.pages.firstOrNull { it.id == state.currentPageId }
            ?: state.document.pages.first()
        return state.document.effectiveSectionId(page)
    }

internal val NeoNoteEditorController.sections: List<NoteSection>
    get() = state.document.effectiveSections()

internal fun NeoNoteEditorController.createSection(title: String = "New section") {
    val added = sectionDocumentEngine.execute(
        DocumentCommand.CreateSection(state.document, title),
    ) as DocumentCommandResult.SectionAdded
    val pageAdded = sectionDocumentEngine.execute(
        DocumentCommand.AddPage(added.document, sectionId = added.section.id),
    ) as DocumentCommandResult.PageAdded
    replaceDocument(pageAdded.document, recordHistory = true)
    switchPage(pageAdded.page.id)
}

internal fun NeoNoteEditorController.renameSection(sectionId: String, title: String) {
    val result = sectionDocumentEngine.execute(
        DocumentCommand.RenameSection(state.document, sectionId, title),
    ) as DocumentCommandResult.SectionUpdated
    replaceDocument(result.document, recordHistory = true)
}

internal fun NeoNoteEditorController.deleteSection(sectionId: String) {
    val currentPageId = state.currentPageId
    val result = sectionDocumentEngine.execute(
        DocumentCommand.DeleteSection(state.document, sectionId),
    ) as DocumentCommandResult.SectionDeleted
    if (result.document == state.document) return
    replaceDocument(result.document, recordHistory = true)
    val retainedCurrent = currentPageId?.takeIf { id -> result.document.pages.any { it.id == id } }
    val next = retainedCurrent ?: result.document.pages.firstOrNull { page ->
        result.document.effectiveSectionId(page) == result.destinationSectionId
    }?.id
    if (next != null) switchPage(next)
}

internal fun NeoNoteEditorController.moveSection(fromIndex: Int, toIndex: Int) {
    val result = sectionDocumentEngine.execute(
        DocumentCommand.MoveSection(state.document, fromIndex, toIndex),
    ) as DocumentCommandResult.SectionsReordered
    val current = state.currentPageId
    replaceDocument(result.document, recordHistory = true)
    if (current != null) switchPage(current)
}

internal fun NeoNoteEditorController.movePageToSection(pageId: String, sectionId: String) {
    val result = sectionDocumentEngine.execute(
        DocumentCommand.MovePageToSection(state.document, pageId, sectionId),
    ) as DocumentCommandResult.PageMoved
    replaceDocument(result.document, recordHistory = true)
    switchPage(result.page.id)
}

internal fun NeoNoteEditorController.switchSection(sectionId: String) {
    val page = state.document.pages.firstOrNull { state.document.effectiveSectionId(it) == sectionId }
    if (page != null) switchPage(page.id)
}

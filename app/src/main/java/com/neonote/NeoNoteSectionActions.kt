package com.neonote

import com.neonote.engine.DocumentCommand
import com.neonote.engine.DocumentCommandResult
import com.neonote.engine.DocumentEngine
import com.neonote.engine.IdGenerator
import com.neonote.model.NotePage
import com.neonote.model.NoteSection
import com.neonote.model.effectiveSectionId
import com.neonote.model.effectiveSections
import java.util.UUID

private val sectionDocumentEngine: DocumentEngine = DocumentEngine(object : IdGenerator {
    override fun nextId(prefix: String): String = "$prefix-${UUID.randomUUID()}"
})


internal val NeoNoteEditorController.currentPageOrNull: NotePage?
    get() = state.document.pages.firstOrNull { it.id == state.currentPageId }

internal val NeoNoteEditorController.pagesInCurrentSection: List<NotePage>
    get() = state.document.pages.filter { state.document.effectiveSectionId(it) == currentSectionId }

internal val NeoNoteEditorController.currentSectionTitle: String
    get() = sections.firstOrNull { it.id == currentSectionId }?.title.orEmpty()

internal val NeoNoteEditorController.currentSectionPageNumber: Int
    get() = pagesInCurrentSection.indexOfFirst { it.id == state.currentPageId }.coerceAtLeast(0) + 1

internal val NeoNoteEditorController.canSwitchToPreviousPageInSection: Boolean
    get() = pagesInCurrentSection.indexOfFirst { it.id == state.currentPageId } > 0

internal val NeoNoteEditorController.canSwitchToNextPageInSection: Boolean
    get() {
        val index = pagesInCurrentSection.indexOfFirst { it.id == state.currentPageId }
        return index >= 0 && index < pagesInCurrentSection.lastIndex
    }

internal fun NeoNoteEditorController.switchToPreviousPageInSection() {
    val pages = pagesInCurrentSection
    val index = pages.indexOfFirst { it.id == state.currentPageId }
    if (index > 0) switchPage(pages[index - 1].id)
}

internal fun NeoNoteEditorController.switchToNextPageInSection() {
    val pages = pagesInCurrentSection
    val index = pages.indexOfFirst { it.id == state.currentPageId }
    if (index in 0 until pages.lastIndex) switchPage(pages[index + 1].id)
}

internal fun NeoNoteEditorController.movePageWithinSection(pageId: String, delta: Int) {
    val page = state.document.pages.firstOrNull { it.id == pageId } ?: return
    val sectionId = state.document.effectiveSectionId(page)
    val sectionPages = state.document.pages.filter { state.document.effectiveSectionId(it) == sectionId }
    val localIndex = sectionPages.indexOfFirst { it.id == pageId }
    val target = (localIndex + delta).coerceIn(0, sectionPages.lastIndex)
    if (localIndex < 0 || target == localIndex) return
    val fromIndex = state.document.pages.indexOfFirst { it.id == pageId }
    val targetIndex = state.document.pages.indexOfFirst { it.id == sectionPages[target].id }
    movePage(fromIndex, targetIndex)
}

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
    applyDocumentStructureChange(pageAdded.document, pageAdded.page.id)
}

internal fun NeoNoteEditorController.renameSection(sectionId: String, title: String) {
    val result = sectionDocumentEngine.execute(
        DocumentCommand.RenameSection(state.document, sectionId, title),
    ) as DocumentCommandResult.SectionUpdated
    applyDocumentStructureChange(result.document)
}

internal fun NeoNoteEditorController.deleteSection(sectionId: String) {
    val currentPageId = state.currentPageId
    val result = sectionDocumentEngine.execute(
        DocumentCommand.DeleteSection(state.document, sectionId),
    ) as DocumentCommandResult.SectionDeleted
    if (result.document == state.document) return
    val retainedCurrent = currentPageId?.takeIf { id -> result.document.pages.any { it.id == id } }
    val next = retainedCurrent ?: result.document.pages.firstOrNull { page ->
        result.document.effectiveSectionId(page) == result.destinationSectionId
    }?.id
    applyDocumentStructureChange(result.document, next)
}

internal fun NeoNoteEditorController.moveSection(fromIndex: Int, toIndex: Int) {
    val result = sectionDocumentEngine.execute(
        DocumentCommand.MoveSection(state.document, fromIndex, toIndex),
    ) as DocumentCommandResult.SectionsReordered
    applyDocumentStructureChange(result.document, state.currentPageId)
}

internal fun NeoNoteEditorController.movePageToSection(pageId: String, sectionId: String) {
    val result = sectionDocumentEngine.execute(
        DocumentCommand.MovePageToSection(state.document, pageId, sectionId),
    ) as DocumentCommandResult.PageMoved
    applyDocumentStructureChange(result.document, result.page.id)
}

internal fun NeoNoteEditorController.switchSection(sectionId: String) {
    val page = state.document.pages.firstOrNull { state.document.effectiveSectionId(it) == sectionId }
    if (page != null) switchPage(page.id)
}

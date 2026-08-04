package com.neonote

import com.neonote.engine.DocumentCommand
import com.neonote.engine.DocumentCommandResult
import com.neonote.engine.DocumentEngine
import com.neonote.engine.FileDocumentLibrary
import com.neonote.engine.IdGenerator
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CompleteDocumentLifecycleTest {
    private class TestIds : IdGenerator {
        private var next = 0
        override fun nextId(prefix: String): String = "$prefix-${next++}"
    }

    @Test
    fun `page lifecycle supports rename duplicate reorder and recoverable delete`() {
        val engine = DocumentEngine(TestIds(), clockMillis = { 100L })
        var document = engine.createDocument("Notebook", "assets", "doc", "first")
        val added = engine.execute(DocumentCommand.AddPage(document)) as DocumentCommandResult.PageAdded
        document = added.document
        val secondId = added.page.id
        document = (engine.execute(DocumentCommand.RenamePage(document, secondId, "Research"))
            as DocumentCommandResult.PageUpdated).document
        val duplicated = engine.execute(DocumentCommand.DuplicatePage(document, secondId)) as DocumentCommandResult.PageAdded
        document = duplicated.document
        document = (engine.execute(DocumentCommand.MovePage(document, 2, 0))
            as DocumentCommandResult.PagesReordered).document
        val deleted = engine.execute(DocumentCommand.DeletePage(document, secondId)) as DocumentCommandResult.PageDeleted

        assertEquals(2, deleted.document.pages.size)
        assertEquals("Research copy", deleted.document.pages.first().title)
        assertTrue(deleted.nextPageId in deleted.document.pages.map { it.id })
        assertTrue(deleted.document.revision > 0)
    }

    @Test
    fun `last page is never removed`() {
        val engine = DocumentEngine(TestIds())
        val document = engine.createDocument("Notebook", "assets", "doc", "first")
        val deleted = engine.deletePage(document, "first")
        assertEquals(document, deleted.document)
        assertEquals("first", deleted.nextPageId)
    }

    @Test
    fun `file library lists exports trashes restores and imports documents`() = runBlocking {
        val root = Files.createTempDirectory("neonote-library-test").toFile()
        try {
            val engine = DocumentEngine(TestIds(), clockMillis = { 1234L })
            val library = FileDocumentLibrary(root)
            val document = engine.createDocument("Library note", "assets", "doc", "page")
            library.save(document)

            val listed = library.list()
            assertEquals(listOf("doc"), listed.map { it.id })
            assertEquals("Library note", listed.single().title)
            val exported = assertNotNull(library.exportJson("doc"))

            assertTrue(library.moveToTrash("doc"))
            assertTrue(library.list().isEmpty())
            assertTrue(library.list(includeTrash = true).single().isTrashed)
            assertNull(library.load("doc").document)

            assertTrue(library.restore("doc"))
            assertEquals("doc", assertNotNull(library.load("doc").document).id)

            val imported = library.importJson(exported, replaceId = "imported")
            assertEquals("imported", imported.id)
            assertEquals(setOf("doc", "imported"), library.list().map { it.id }.toSet())
            assertTrue(library.permanentlyDelete("imported"))
            assertFalse(library.list().any { it.id == "imported" })
        } finally {
            root.deleteRecursively()
        }
    }
}

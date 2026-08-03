package com.neonote

import com.neonote.engine.JsonFilePersistenceStore
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class AtomicPersistenceTest {
    @Test
    fun `repeated saves atomically replace the document and leave no temp file`() = runBlocking {
        val directory = Files.createTempDirectory("neonote-atomic-save").toFile()
        val store = JsonFilePersistenceStore(directory)
        val first = document(revision = 1L, title = "first")
        val second = document(revision = 2L, title = "second")

        store.save(first)
        store.save(second)

        val loaded = assertNotNull(store.load(second.id).document)
        assertEquals(2L, loaded.revision)
        assertEquals("second", loaded.title)
        assertFalse(directory.listFiles().orEmpty().any { it.name.endsWith(".tmp") })
    }

    private fun document(revision: Long, title: String): NeoNoteDocument = NeoNoteDocument(
        id = "autosave-document",
        title = title,
        pages = listOf(NotePage(id = "page-1")),
        assetStoreId = "autosave-assets",
        revision = revision,
    )
}

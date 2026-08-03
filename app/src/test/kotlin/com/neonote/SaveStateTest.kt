package com.neonote

import com.neonote.engine.JsonFilePersistenceStore
import java.nio.file.Files
import kotlin.io.path.deleteRecursively
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.runBlocking

class SaveStateTest {
    @Test
    fun `save label follows the persisted document revision`() = runBlocking {
        val directory = Files.createTempDirectory("neonote-save-state")
        try {
            val controller = NeoNoteEditorController()
            val store = JsonFilePersistenceStore(directory.toFile())

            assertEquals("Saving locally", controller.saveStateLabel)

            controller.saveDocument(store)
            assertEquals("Saved", controller.saveStateLabel)

            controller.renameDocument("Changed after save")
            assertEquals("Saving…", controller.saveStateLabel)

            controller.saveDocument(store)
            assertEquals("Saved", controller.saveStateLabel)
        } finally {
            directory.deleteRecursively()
        }
    }
}

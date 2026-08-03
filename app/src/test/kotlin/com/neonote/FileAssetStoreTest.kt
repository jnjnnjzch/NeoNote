package com.neonote

import com.neonote.engine.AssetDraft
import com.neonote.engine.AssetStoreResult
import com.neonote.engine.FileAssetStore
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class FileAssetStoreTest {
    @Test
    fun `put get and delete preserve local image bytes and metadata`() = runBlocking {
        val directory = Files.createTempDirectory("neonote-assets").toFile()
        val store = FileAssetStore(directory)
        val expectedBytes = byteArrayOf(1, 3, 5, 7, 9)

        val stored = store.put(
            AssetDraft(
                mediaType = "image/png",
                bytes = expectedBytes,
                fileName = "diagram.png",
            ),
        )
        val loaded = requireNotNull(store.get(stored.id))

        assertEquals("image/png", loaded.mediaType)
        assertEquals("diagram.png", loaded.fileName)
        assertTrue(stored.id.endsWith(".png"))
        assertContentEquals(expectedBytes, File(requireNotNull(loaded.uri)).readBytes())
        assertTrue(directory.listFiles().orEmpty().none { it.name.endsWith(".tmp") })

        assertIs<AssetStoreResult.Deleted>(store.delete(stored.id))
        assertNull(store.get(stored.id))
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun `path traversal ids cannot escape the asset directory`() = runBlocking {
        val directory = Files.createTempDirectory("neonote-assets-safe").toFile()
        val store = FileAssetStore(directory)

        assertNull(store.get("../outside.png"))
        assertIs<AssetStoreResult.Missing>(store.delete("../outside.png"))
    }
}

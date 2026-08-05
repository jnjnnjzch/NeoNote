package com.neonote

import com.neonote.engine.FileAssetGarbageCollector
import com.neonote.engine.VersionedPersistenceStore
import com.neonote.model.BlockImage
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasSize
import com.neonote.model.InfiniteCanvas
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ProductHardeningPersistenceTest {
    @Test
    fun `corrupt active document recovers newest decodable backup`() = runBlocking {
        val root = Files.createTempDirectory("neonote-versioned").toFile()
        val store = VersionedPersistenceStore(root, maximumBackups = 3)
        val first = document("doc", 1L, "first")
        val second = document("doc", 2L, "second")
        store.save(first)
        store.save(second)
        root.resolve("doc.json").writeText("{broken")

        val recovered = store.load("doc")

        assertNotNull(recovered.document)
        assertEquals(1L, recovered.document?.revision)
        assertEquals("first", recovered.document?.title)
        assertTrue(root.resolve("doc.json").readText().contains("first"))
    }

    @Test
    fun `asset collection retains referenced bytes and metadata but removes orphan pairs`() {
        val root = Files.createTempDirectory("neonote-assets").toFile()
        val documents = root.resolve("documents").also { it.mkdirs() }
        val assets = root.resolve("assets").also { it.mkdirs() }
        val document = document("doc", 1L, "assets").copy(
            pages = listOf(NotePage(
                id = "page",
                canvas = InfiniteCanvas(objects = listOf(RichContentBox(
                    id = "box",
                    position = CanvasPoint.Zero,
                    size = CanvasSize(320f, 96f),
                    content = RichContent(listOf(BlockImage("asset-keep.png"))),
                ))),
            )),
        )
        documents.resolve("doc.json").writeText(com.neonote.engine.DefaultDocumentJson.encodeToString(NeoNoteDocument.serializer(), document))
        assets.resolve("asset-keep.png").writeBytes(byteArrayOf(1))
        assets.resolve("asset-keep.png.properties").writeText("mediaType=image/png")
        assets.resolve("asset-orphan.png").writeBytes(byteArrayOf(2))
        assets.resolve("asset-orphan.png.properties").writeText("mediaType=image/png")
        assets.resolve("unfinished.tmp").apply {
            writeText("pending")
            setLastModified(System.currentTimeMillis())
        }

        val result = FileAssetGarbageCollector().collect(listOf(documents), assets)

        assertTrue(assets.resolve("asset-keep.png").exists())
        assertTrue(assets.resolve("asset-keep.png.properties").exists())
        assertFalse(assets.resolve("asset-orphan.png").exists())
        assertFalse(assets.resolve("asset-orphan.png.properties").exists())
        assertTrue(assets.resolve("unfinished.tmp").exists())
        assertEquals(2, result.deletedFileCount)
        assertEquals(1, result.retainedTemporaryFileCount)
    }

    private fun document(id: String, revision: Long, title: String) = NeoNoteDocument(
        id = id,
        title = title,
        revision = revision,
        assetStoreId = "assets",
        pages = listOf(NotePage(id = "page")),
    )
}

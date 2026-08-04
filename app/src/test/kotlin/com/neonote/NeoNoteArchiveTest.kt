package com.neonote

import com.neonote.engine.AssetDraft
import com.neonote.engine.FileAssetStore
import com.neonote.engine.NeoNoteArchiveCodec
import com.neonote.engine.referencedAssetIds
import com.neonote.model.BlockImage
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasSize
import com.neonote.model.FloatingImage
import com.neonote.model.InfiniteCanvas
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableNode
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class NeoNoteArchiveTest {
    @Test
    fun `archive exports imports and remaps recursively referenced assets`() = runBlocking {
        val sourceRoot = Files.createTempDirectory("neonote-archive-source").toFile()
        val targetRoot = Files.createTempDirectory("neonote-archive-target").toFile()
        try {
            val sourceStore = FileAssetStore(sourceRoot)
            val firstBytes = byteArrayOf(1, 2, 3, 4)
            val secondBytes = byteArrayOf(5, 6, 7)
            val first = sourceStore.put(AssetDraft("image/png", firstBytes, "nested.png"))
            val second = sourceStore.put(AssetDraft("image/jpeg", secondBytes, "floating.jpg"))
            val document = NeoNoteDocument(
                id = "source-document",
                title = "Archive test",
                assetStoreId = "assets",
                pages = listOf(NotePage(
                    id = "page",
                    canvas = InfiniteCanvas(objects = listOf(
                        RichContentBox(
                            id = "box",
                            content = RichContent(listOf(TableNode(rows = listOf(listOf(
                                TableCell(content = RichContent(listOf(BlockImage(first.id)))),
                            ))))),
                        ),
                        FloatingImage(
                            id = "floating",
                            position = CanvasPoint(10f, 20f),
                            size = CanvasSize(100f, 80f),
                            assetId = second.id,
                        ),
                    )),
                )),
            )

            val archive = NeoNoteArchiveCodec(sourceStore).export(document)
            val targetStore = FileAssetStore(targetRoot)
            val imported = NeoNoteArchiveCodec(targetStore).import(archive, "imported-document")

            assertEquals("imported-document", imported.document.id)
            assertEquals(2, imported.importedAssetCount)
            val importedIds = imported.document.referencedAssetIds()
            assertEquals(2, importedIds.size)
            assertNotEquals(document.referencedAssetIds(), importedIds)
            val importedReferences = importedIds.map { assertNotNull(targetStore.get(it)) }
            assertContentEquals(
                setOf(firstBytes.toList(), secondBytes.toList()),
                importedReferences.map { java.io.File(requireNotNull(it.uri)).readBytes().toList() }.toSet(),
            )
        } finally {
            sourceRoot.deleteRecursively()
            targetRoot.deleteRecursively()
        }
    }
}

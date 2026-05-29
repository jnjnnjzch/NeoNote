package com.neonote

import com.neonote.engine.JsonFilePersistenceStore
import com.neonote.engine.PersistenceResult
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasSize
import com.neonote.model.FloatingImage
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkLayer
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import com.neonote.model.InlineFormula
import com.neonote.model.InlineImage
import com.neonote.model.InlineLineBreak
import com.neonote.model.InlineText
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCell
import com.neonote.model.TableNode
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlinx.coroutines.runBlocking

class PersistenceStoreTest {
    @Test
    fun roundTripsTextBoxesAndRichContentNodeVariants() = runBlocking {
        val document = NeoNoteDocument(
            id = "rich-document",
            title = "Rich document",
            assetStoreId = "local-assets",
            revision = 7L,
            pages = listOf(
                NotePage(
                    id = "page-1",
                    canvas = InfiniteCanvas(
                        objects = listOf(
                            RichContentBox(
                                id = "box-1",
                                position = CanvasPoint(x = -123.5f, y = 456.25f),
                                size = CanvasSize(width = 320.75f, height = 160.5f),
                                zIndex = 4,
                                isFocused = true,
                                content = RichContent(
                                    blocks = listOf(
                                        ParagraphNode(
                                            id = "paragraph-1",
                                            inlines = listOf(
                                                InlineText("Exact coordinates stay on the document"),
                                                InlineLineBreak,
                                                InlineFormula("x^2 + y^2"),
                                                InlineImage(assetId = "inline-image-1", altText = "inline alt"),
                                            ),
                                        ),
                                        TableNode(
                                            id = "table-1",
                                            rows = listOf(
                                                listOf(
                                                    TableCell(
                                                        RichContent(
                                                            blocks = listOf(
                                                                ParagraphNode(
                                                                    id = "cell-paragraph",
                                                                    inlines = listOf(InlineText("nested cell")),
                                                                ),
                                                            ),
                                                        ),
                                                    ),
                                                ),
                                            ),
                                        ),
                                        BlockFormula(expression = "\\int_0^1 x dx", id = "formula-1"),
                                        BlockImage(assetId = "block-image-1", altText = "block alt", id = "image-1"),
                                    ),
                                ),
                            ),
                            FloatingImage(
                                id = "floating-image-1",
                                position = CanvasPoint(x = 10.125f, y = -20.875f),
                                size = CanvasSize(width = 40.5f, height = 50.25f),
                                zIndex = 2,
                                assetId = "image-asset",
                                altText = "floating alt",
                            ),
                        ),
                    ),
                ),
            ),
        )

        val loaded = document.saveThenLoad()

        assertEquals(document, loaded)
        val loadedBox = loaded.pages.single().canvas.objects.first() as RichContentBox
        assertEquals(CanvasPoint(x = -123.5f, y = 456.25f), loadedBox.position)
        assertEquals(CanvasSize(width = 320.75f, height = 160.5f), loadedBox.size)
    }

    @Test
    fun roundTripsInkStrokesWithExactDocumentCoordinates() = runBlocking {
        val stroke = InkStroke(
            id = "stroke-1",
            points = listOf(
                InkPoint(x = -0.125f, y = 1_024.5f, pressure = 0.25f, rawPressure = 0.125f),
                InkPoint(x = 2_048.75f, y = -512.375f, pressure = 0.75f, rawPressure = 1.25f),
            ),
        )
        val document = testDocument(
            revision = 3L,
            pages = listOf(
                NotePage(
                    id = "ink-page",
                    canvas = InfiniteCanvas(inkLayer = InkLayer(strokes = listOf(stroke))),
                ),
            ),
        )

        val loaded = document.saveThenLoad()

        assertEquals(stroke, loaded.pages.single().canvas.inkLayer.strokes.single())
        assertEquals(-0.125f, loaded.pages.single().canvas.inkLayer.strokes.single().points.first().x)
        assertEquals(1_024.5f, loaded.pages.single().canvas.inkLayer.strokes.single().points.first().y)
    }

    @Test
    fun roundTripsMultiPageDocumentShapeAndRevision() = runBlocking {
        val document = testDocument(
            revision = 42L,
            pages = listOf(
                NotePage(id = "page-1", canvas = InfiniteCanvas()),
                NotePage(
                    id = "page-2",
                    canvas = InfiniteCanvas(
                        objects = listOf(
                            RichContentBox(
                                id = "page-2-box",
                                position = CanvasPoint(x = 2f, y = 3f),
                                size = CanvasSize(width = 4f, height = 5f),
                            ),
                        ),
                    ),
                ),
                NotePage(
                    id = "page-3",
                    canvas = InfiniteCanvas(
                        inkLayer = InkLayer(strokes = listOf(InkStroke(id = "page-3-stroke"))),
                    ),
                ),
            ),
        )

        val loaded = document.saveThenLoad()

        assertEquals(listOf("page-1", "page-2", "page-3"), loaded.pages.map { it.id })
        assertEquals(42L, loaded.revision)
        assertEquals(document, loaded)
    }

    @Test
    fun controllerSaveAndLoadUpdateStateAndStatusWithoutPersistingViewportAsPosition() = runBlocking {
        val directory = Files.createTempDirectory("neonote-controller-persistence").toFile()
        val store = JsonFilePersistenceStore(directory)
        val initialState = createTestEditorState()
        val controller = NeoNoteEditorController(initialState = initialState)
        controller.panViewportBy(screenDx = 500f, screenDy = -250f)
        controller.focusOrCreateRichContentBox(CanvasPoint(x = 12.5f, y = -98.25f))
        controller.updateRichContentText("rich-content-1", "persist me")
        val savedDocument = controller.state.document

        val saved = controller.saveDocument(store)
        val reloadedController = NeoNoteEditorController(initialState = createTestEditorState())
        val loadedResult = reloadedController.loadDocument(store, saved.documentId)

        assertEquals(PersistenceResult.Saved(documentId = savedDocument.id, revision = savedDocument.revision), saved)
        val loaded = assertNotNull(loadedResult.document)
        assertEquals(savedDocument, loaded)
        assertEquals(savedDocument, reloadedController.state.document)
        assertEquals("Loaded ${savedDocument.id} at revision ${savedDocument.revision}", reloadedController.persistenceStatus)
        val loadedBox = assertIs<RichContentBox>(loaded.pages.single().canvas.objects.single())
        assertEquals(CanvasPoint(x = 12.5f, y = -98.25f), loadedBox.position)
    }

    private suspend fun NeoNoteDocument.saveThenLoad(): NeoNoteDocument {
        val directory = Files.createTempDirectory("neonote-persistence").toFile()
        val store = JsonFilePersistenceStore(directory)
        store.save(this)
        return assertNotNull(store.load(id).document)
    }

    private fun testDocument(revision: Long, pages: List<NotePage>): NeoNoteDocument = NeoNoteDocument(
        id = "test-document",
        title = "Test document",
        assetStoreId = "test-assets",
        revision = revision,
        pages = pages,
    )
}

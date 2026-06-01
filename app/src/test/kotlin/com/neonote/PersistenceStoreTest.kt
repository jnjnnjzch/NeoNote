package com.neonote

import com.neonote.engine.DefaultDocumentJson
import com.neonote.engine.DocumentManifest
import com.neonote.engine.JsonFilePersistenceStore
import com.neonote.engine.PAGE_INK_BIN_FILE_NAME
import com.neonote.engine.PageInkStorage
import com.neonote.engine.PagePersistenceResult
import com.neonote.engine.PagePersistenceStore
import com.neonote.engine.PersistedPage
import com.neonote.engine.toDocumentManifest
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
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json

class PersistenceStoreTest {

    @Test
    fun documentManifestCapturesPageLevelRoutingMetadata() {
        val document = testDocument(
            id = "manifest-document",
            revision = 17L,
            pages = listOf(
                NotePage(id = "manifest-page-1"),
                NotePage(id = "manifest-page-2"),
            ),
        )

        val manifest = document.toDocumentManifest()

        assertEquals("manifest-document", manifest.documentId)
        assertEquals(17L, manifest.revision)
        assertEquals(listOf("manifest-page-1", "manifest-page-2"), manifest.pageIds)
        assertEquals("test-assets", manifest.assetStoreId)
    }

    @Test
    fun pagePersistenceStoreCanLoadManifestBeforeLoadingPages() = runBlocking {
        val manifest = DocumentManifest(
            documentId = "lazy-document",
            revision = 5L,
            pageIds = listOf("page-1", "page-2", "page-3"),
            assetStoreId = "lazy-assets",
        )
        val store = FakePagePersistenceStore(
            initialManifest = manifest,
            initialPages = listOf(
                NotePage(id = "page-1"),
                NotePage(
                    id = "page-2",
                    canvas = InfiniteCanvas(
                        inkLayer = InkLayer(strokes = listOf(InkStroke(id = "lazy-stroke"))),
                    ),
                ),
                NotePage(id = "page-3"),
            ),
        )

        val loadedManifest = store.loadDocumentManifest("lazy-document")
        val loadedPage = store.loadPage("lazy-document", "page-2")

        assertEquals(manifest, loadedManifest)
        assertEquals("page-2", loadedPage?.page?.id)
        assertEquals(listOf("page-2"), store.loadedPageIds)
        assertEquals(emptyList(), store.savedPageIds)
    }

    @Test
    fun pagePersistenceStoreCanSaveOnlyDirtyPageAndReserveBinaryInkBoundary() = runBlocking {
        val manifest = DocumentManifest(
            documentId = "dirty-document",
            revision = 8L,
            pageIds = listOf("clean-page", "dirty-page"),
            assetStoreId = "dirty-assets",
        )
        val cleanPage = NotePage(id = "clean-page")
        val store = FakePagePersistenceStore(
            initialManifest = manifest,
            initialPages = listOf(cleanPage, NotePage(id = "dirty-page")),
        )
        val dirtyPage = PersistedPage(
            page = NotePage(
                id = "dirty-page",
                canvas = InfiniteCanvas(
                    inkLayer = InkLayer(strokes = listOf(InkStroke(id = "dirty-stroke"))),
                ),
            ),
            inkStorage = PageInkStorage.ExternalInkBinary(revision = 9L),
        )

        val pageSaved = store.saveDirtyPage("dirty-document", dirtyPage)
        val manifestSaved = store.saveManifest(manifest.copy(revision = 9L))

        assertEquals(
            PagePersistenceResult.PageSaved(documentId = "dirty-document", pageId = "dirty-page"),
            pageSaved,
        )
        assertEquals(
            PagePersistenceResult.ManifestSaved(documentId = "dirty-document", revision = 9L, pageCount = 2),
            manifestSaved,
        )
        assertEquals(listOf("dirty-page"), store.savedPageIds)
        assertEquals(cleanPage, store.peekPage("clean-page")?.page)
        assertEquals(
            PageInkStorage.ExternalInkBinary(relativePath = PAGE_INK_BIN_FILE_NAME, revision = 9L),
            store.peekPage("dirty-page")?.inkStorage,
        )
    }

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
    fun savesCompactJsonAndLoadsItWithoutDroppingInkPressure() = runBlocking {
        val document = testDocument(
            id = "compact-document",
            revision = 4L,
            pages = listOf(
                NotePage(
                    id = "compact-page",
                    canvas = InfiniteCanvas(
                        inkLayer = InkLayer(
                            strokes = listOf(
                                InkStroke(
                                    id = "compact-stroke",
                                    points = listOf(
                                        InkPoint(x = 10f, y = 20f),
                                        InkPoint(x = 30.5f, y = -40.25f, pressure = 0.375f, rawPressure = 0.875f),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val directory = Files.createTempDirectory("neonote-compact-persistence").toFile()
        val store = JsonFilePersistenceStore(directory)

        val saved = store.save(document)
        val jsonText = directory.resolve("compact-document.json").readText()
        val loaded = assertNotNull(store.load(document.id).document)
        val loadedPoints = loaded.pages.single().canvas.inkLayer.strokes.single().points

        assertEquals(document, loaded)
        assertEquals(saved.diagnostics?.fileSizeBytes, jsonText.length.toLong())
        assertFalse(jsonText.contains('\n'), "Compact JSON should be emitted without pretty-print newlines")
        assertFalse(jsonText.contains("\"rawPressure\":null"), "Null rawPressure defaults should be omitted")
        assertEquals(1f, loadedPoints.first().pressure)
        assertEquals(null, loadedPoints.first().rawPressure)
        assertEquals(0.375f, loadedPoints.last().pressure)
        assertEquals(0.875f, loadedPoints.last().rawPressure)
    }

    @Test
    fun loadsLegacyPrettyJsonWithEncodedDefaults() = runBlocking {
        val directory = Files.createTempDirectory("neonote-legacy-pretty-persistence").toFile()
        val legacyJson = requireNotNull(javaClass.classLoader?.getResource("persistence/legacy-pretty-document.json"))
            .readText()
        directory.resolve("legacy-pretty-document.json").writeText(legacyJson)
        val store = JsonFilePersistenceStore(directory)

        val loaded = assertNotNull(store.load("legacy-pretty-document").document)
        val points = loaded.pages.single().canvas.inkLayer.strokes.single().points

        assertEquals("Legacy Pretty Document", loaded.title)
        assertEquals("legacy-assets", loaded.assetStoreId)
        assertEquals(12L, loaded.revision)
        assertEquals(InkPoint(x = 1.5f, y = -2.25f, pressure = 0.5f, rawPressure = 0.75f), points.first())
        assertEquals(InkPoint(x = 3f, y = 4f, pressure = 1f, rawPressure = null), points.last())
    }

    @Test
    fun missingDefaultFieldsLoadWithCurrentDefaultSemantics() = runBlocking {
        val directory = Files.createTempDirectory("neonote-default-field-persistence").toFile()
        directory.resolve("default-field-document.json").writeText(
            """
            {"id":"default-field-document","title":"Default Field Document","assetStoreId":"default-assets","pages":[{"id":"default-page","canvas":{"inkLayer":{"strokes":[{"id":"default-stroke","points":[{"x":7.0,"y":8.0}]}]}}},{"id":"blank-default-page"}]}
            """.trimIndent(),
        )
        val store = JsonFilePersistenceStore(directory)

        val loaded = assertNotNull(store.load("default-field-document").document)
        val page = loaded.pages.first()
        val blankPage = loaded.pages.last()
        val stroke = page.canvas.inkLayer.strokes.single()
        val point = stroke.points.single()

        assertEquals(0L, loaded.revision)
        assertEquals(emptyList(), page.canvas.objects)
        assertEquals(InkPoint(x = 7f, y = 8f, pressure = 1f, rawPressure = null), point)
        assertEquals(InfiniteCanvas(), blankPage.canvas)
    }

    @Test
    fun largeInkDocumentIsSmallerAsCompactJsonThanLegacyPrettyJson() = runBlocking {
        val document = largeInkDocument()
        val prettyDirectory = Files.createTempDirectory("neonote-large-pretty-persistence").toFile()
        val compactDirectory = Files.createTempDirectory("neonote-large-compact-persistence").toFile()
        val legacyPrettyJson = Json(DefaultDocumentJson) {
            encodeDefaults = true
            prettyPrint = true
        }
        val prettyStore = JsonFilePersistenceStore(prettyDirectory, legacyPrettyJson)
        val compactStore = JsonFilePersistenceStore(compactDirectory)

        val prettySize = assertNotNull(prettyStore.save(document).diagnostics).fileSizeBytes
        val compactSize = assertNotNull(compactStore.save(document).diagnostics).fileSizeBytes
        val loaded = assertNotNull(compactStore.load(document.id).document)

        assertEquals(document, loaded)
        assertTrue(compactSize < prettySize, "Compact JSON ($compactSize bytes) should be smaller than legacy pretty JSON ($prettySize bytes)")
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
    fun controllerSaveAndLoadPreserveMeasuredRichContentBoxHeight() = runBlocking {
        val directory = Files.createTempDirectory("neonote-rich-content-height").toFile()
        val store = JsonFilePersistenceStore(directory)
        val controller = NeoNoteEditorController(initialState = createTestEditorState())
        controller.focusOrCreateRichContentBox(CanvasPoint(x = 40f, y = 50f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, (1..12).joinToString("\n") { "persisted line $it" })
        val savedBox = controller.currentCanvas.objects.single() as RichContentBox

        controller.saveDocument(store)
        val reloadedController = NeoNoteEditorController(initialState = createTestEditorState())
        reloadedController.loadDocument(store, controller.state.document.id)

        val loadedBox = reloadedController.currentCanvas.objects.single() as RichContentBox
        assertEquals(savedBox.size, loadedBox.size)
        assertTrue(loadedBox.size.height > 160f)
        assertEquals(savedBox.toPlainText(), loadedBox.toPlainText())
    }

    @Test
    fun saveAndLoadDiagnosticsCountInkAcrossPages() = runBlocking {
        val document = testDocument(
            revision = 9L,
            pages = listOf(
                NotePage(
                    id = "page-1",
                    canvas = InfiniteCanvas(
                        inkLayer = InkLayer(
                            strokes = listOf(
                                InkStroke(
                                    id = "stroke-1",
                                    points = listOf(
                                        InkPoint(x = 1f, y = 1f),
                                        InkPoint(x = 2f, y = 2f),
                                    ),
                                ),
                                InkStroke(
                                    id = "stroke-2",
                                    points = listOf(InkPoint(x = 3f, y = 3f)),
                                ),
                            ),
                        ),
                    ),
                ),
                NotePage(id = "page-2", canvas = InfiniteCanvas()),
                NotePage(
                    id = "page-3",
                    canvas = InfiniteCanvas(
                        inkLayer = InkLayer(
                            strokes = listOf(
                                InkStroke(
                                    id = "stroke-3",
                                    points = listOf(
                                        InkPoint(x = 4f, y = 4f),
                                        InkPoint(x = 5f, y = 5f),
                                        InkPoint(x = 6f, y = 6f),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            ),
        )
        val directory = Files.createTempDirectory("neonote-persistence-diagnostics").toFile()
        val store = JsonFilePersistenceStore(directory)

        val saved = store.save(document)
        val saveDiagnostics = assertNotNull(saved.diagnostics)
        val loaded = store.load(document.id)
        val loadDiagnostics = assertNotNull(loaded.diagnostics)

        assertEquals(document.id, saveDiagnostics.documentId)
        assertEquals(3, saveDiagnostics.pageCount)
        assertEquals(3, saveDiagnostics.strokeCount)
        assertEquals(6, saveDiagnostics.pointCount)
        assertTrue(saveDiagnostics.fileSizeBytes > 0L)
        assertNotNull(saveDiagnostics.encodeTimeMillis)
        assertNotNull(saveDiagnostics.writeTimeMillis)

        assertEquals(document, loaded.document)
        assertEquals(document.id, loadDiagnostics.documentId)
        assertEquals(3, loadDiagnostics.pageCount)
        assertEquals(3, loadDiagnostics.strokeCount)
        assertEquals(6, loadDiagnostics.pointCount)
        assertEquals(saveDiagnostics.fileSizeBytes, loadDiagnostics.fileSizeBytes)
        assertNotNull(loadDiagnostics.readTimeMillis)
        assertNotNull(loadDiagnostics.decodeTimeMillis)
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

        assertEquals(savedDocument.id, saved.documentId)
        assertEquals(savedDocument.revision, saved.revision)
        val loaded = assertNotNull(loadedResult.document)
        assertEquals(savedDocument, loaded)
        assertEquals(savedDocument, reloadedController.state.document)
        assertTrue(reloadedController.persistenceStatus.startsWith("Loaded ${savedDocument.id} at revision ${savedDocument.revision}"))
        assertEquals(loadedResult.diagnostics, reloadedController.persistenceDiagnostics)
        assertNotNull(loadedResult.diagnostics?.cacheRebuildTimeMillis)
        val loadedBox = assertIs<RichContentBox>(loaded.pages.single().canvas.objects.single())
        assertEquals(CanvasPoint(x = 12.5f, y = -98.25f), loadedBox.position)
    }

    private suspend fun NeoNoteDocument.saveThenLoad(): NeoNoteDocument {
        val directory = Files.createTempDirectory("neonote-persistence").toFile()
        val store = JsonFilePersistenceStore(directory)
        store.save(this)
        return assertNotNull(store.load(id).document)
    }

    private fun testDocument(
        revision: Long,
        pages: List<NotePage>,
        id: String = "test-document",
    ): NeoNoteDocument = NeoNoteDocument(
        id = id,
        title = "Test document",
        assetStoreId = "test-assets",
        revision = revision,
        pages = pages,
    )

    private fun largeInkDocument(): NeoNoteDocument = testDocument(
        id = "large-ink-document",
        revision = 100L,
        pages = listOf(
            NotePage(
                id = "large-ink-page",
                canvas = InfiniteCanvas(
                    inkLayer = InkLayer(
                        strokes = List(48) { strokeIndex ->
                            InkStroke(
                                id = "large-stroke-$strokeIndex",
                                points = List(160) { pointIndex ->
                                    InkPoint(
                                        x = strokeIndex * 12.5f + pointIndex * 0.5f,
                                        y = pointIndex * 1.25f - strokeIndex,
                                        pressure = if (pointIndex % 4 == 0) 1f else 0.2f + (pointIndex % 8) * 0.1f,
                                        rawPressure = if (pointIndex % 5 == 0) null else 0.15f + (pointIndex % 10) * 0.05f,
                                    )
                                },
                            )
                        },
                    ),
                ),
            ),
        ),
    )
}

private class FakePagePersistenceStore(
    initialManifest: DocumentManifest,
    initialPages: List<NotePage>,
) : PagePersistenceStore {
    private var manifest: DocumentManifest = initialManifest
    private val pages: MutableMap<String, PersistedPage> = initialPages
        .associate { page -> page.id to PersistedPage(page) }
        .toMutableMap()

    val loadedPageIds: MutableList<String> = mutableListOf()
    val savedPageIds: MutableList<String> = mutableListOf()

    override suspend fun loadDocumentManifest(documentId: String): DocumentManifest? =
        manifest.takeIf { it.documentId == documentId }

    override suspend fun loadPage(documentId: String, pageId: String): PersistedPage? {
        if (manifest.documentId != documentId) return null
        loadedPageIds += pageId
        return pages[pageId]
    }

    override suspend fun saveDirtyPage(documentId: String, page: PersistedPage): PagePersistenceResult.PageSaved {
        require(manifest.documentId == documentId) { "Unknown document id: $documentId" }
        require(page.page.id in manifest.pageIds) { "Page is not listed in the manifest: ${page.page.id}" }
        pages[page.page.id] = page
        savedPageIds += page.page.id
        return PagePersistenceResult.PageSaved(documentId = documentId, pageId = page.page.id)
    }

    override suspend fun saveManifest(manifest: DocumentManifest): PagePersistenceResult.ManifestSaved {
        this.manifest = manifest
        return PagePersistenceResult.ManifestSaved(
            documentId = manifest.documentId,
            revision = manifest.revision,
            pageCount = manifest.pageIds.size,
        )
    }

    fun peekPage(pageId: String): PersistedPage? = pages[pageId]
}

package com.example.cahier.core.document

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class DocumentSerializerTest {

    @Test
    fun encodeDecode_roundTripsTableBlock() {
        val document = TicDocument(
            pages = listOf(
                CanvasPage(
                    blocks = listOf(
                        TableBlock(rows = 3, columns = 3)
                    )
                )
            )
        )

        val raw = DocumentSerializer.encode(document)
        val decoded = DocumentSerializer.decodeOrNull(raw)

        assertNotNull(decoded)
        assertEquals(1, decoded!!.pages.size)
        assertEquals(1, decoded.pages.first().blocks.size)
        val table = decoded.pages.first().blocks.first() as TableBlock
        assertEquals(3, table.rows)
        assertEquals(3, table.columns)
    }

    @Test
    fun encodeDecode_preservesDocumentRevisionAndInkLayerMetadata() {
        val document = TicDocument(
            revision = 42L,
            pages = listOf(
                CanvasPage(
                    inkLayer = InkLayerRef(
                        documentRevision = 42L,
                        strokeCount = 7
                    )
                )
            )
        )

        val decoded = DocumentSerializer.decodeOrNull(DocumentSerializer.encode(document))

        assertNotNull(decoded)
        assertEquals(42L, decoded!!.revision)
        assertEquals(42L, decoded.pages.first().inkLayer.documentRevision)
        assertEquals(7, decoded.pages.first().inkLayer.strokeCount)
    }

    @Test
    fun decodeOrNull_invalidPayload_returnsNull() {
        val decoded = DocumentSerializer.decodeOrNull("{bad json")
        assertNull(decoded)
    }

    @Test
    fun encodeDecode_preservesCellFormattingAndRowGrowth() {
        val row = listOf(
            TableCell(text = "A1", bold = true, italic = true, underline = true, imageUri = "content://image/1", latex = "x^2"),
            TableCell(text = "B1"),
            TableCell(text = "C1")
        )
        val table = TableBlock(
            rows = 4,
            columns = 3,
            cells = listOf(row, row, row, row)
        )
        val document = TicDocument(pages = listOf(CanvasPage(blocks = listOf(table))))

        val decoded = DocumentSerializer.decodeOrNull(DocumentSerializer.encode(document))
        val decodedTable = decoded!!.pages.first().blocks.first() as TableBlock

        assertEquals(4, decodedTable.rows)
        assertEquals(true, decodedTable.cells.first().first().bold)
        assertEquals(true, decodedTable.cells.first().first().italic)
        assertEquals(true, decodedTable.cells.first().first().underline)
        assertEquals("content://image/1", decodedTable.cells.first().first().imageUri)
        assertEquals("x^2", decodedTable.cells.first().first().latex)
        assertEquals("A1", decodedTable.cells.first().first().text)
    }

    @Test
    fun encodeDecode_preservesPressureCurveSetting() {
        val document = TicDocument(
            settings = DocumentSettings(
                pressureCurve = 1.7f,
                stylusWritesByDefault = false,
                fingerPansByDefault = false
            )
        )
        val decoded = DocumentSerializer.decodeOrNull(DocumentSerializer.encode(document))
        assertNotNull(decoded)
        assertEquals(1.7f, decoded!!.settings.pressureCurve)
        assertEquals(false, decoded.settings.stylusWritesByDefault)
        assertEquals(false, decoded.settings.fingerPansByDefault)
    }

    @Test
    fun encodeDecode_roundTripsTextContainerBlock() {
        val container = TextContainerBlock(
            x = 320f,
            y = 240f,
            width = 800f,
            height = 420f,
            content = TextContainerContent(
                nodes = listOf(
                    ParagraphNode("before table"),
                    TableNode(rows = 2, columns = 2),
                    ParagraphNode("after table")
                )
            )
        )
        val document = TicDocument(pages = listOf(CanvasPage(blocks = listOf(container))))
        val decoded = DocumentSerializer.decodeOrNull(DocumentSerializer.encode(document))

        assertNotNull(decoded)
        val decodedContainer = decoded!!.pages.first().blocks.first() as TextContainerBlock
        assertEquals(320f, decodedContainer.x)
        assertEquals(240f, decodedContainer.y)
        assertEquals(3, decodedContainer.content.nodes.size)
        assertEquals("before table", (decodedContainer.content.nodes[0] as ParagraphNode).text)
    }

    @Test
    fun encodeDecode_preservesSharedCanvasCoordinatesAcrossBlockTypes() {
        val text = TextContainerBlock(x = 300f, y = 220f, width = 700f, height = 380f)
        val image = ImageBlock(x = -140f, y = 96f, width = 256f, height = 192f, assetPath = "files/notes/42/assets/a.png")
        val table = TableBlock(x = 48f, y = -72f, width = 640f, height = 320f, rows = 2, columns = 2)
        val document = TicDocument(pages = listOf(CanvasPage(blocks = listOf(text, image, table))))

        val decoded = DocumentSerializer.decodeOrNull(DocumentSerializer.encode(document))
        assertNotNull(decoded)
        val blocks = decoded!!.pages.first().blocks

        val decodedText = blocks[0] as TextContainerBlock
        val decodedImage = blocks[1] as ImageBlock
        val decodedTable = blocks[2] as TableBlock

        assertEquals(300f, decodedText.x)
        assertEquals(220f, decodedText.y)
        assertEquals(-140f, decodedImage.x)
        assertEquals(96f, decodedImage.y)
        assertEquals(48f, decodedTable.x)
        assertEquals(-72f, decodedTable.y)
    }

    @Test
    fun encodeDecode_preservesPersistentStrokeMetadata() {
        val text = TextContainerBlock(id = "block-1", x = 100f, y = 120f)
        val document = TicDocument(
            pages = listOf(
                CanvasPage(
                    blocks = listOf(text),
                    strokeIds = listOf("stroke-1"),
                    strokeAnchors = listOf(
                        StrokeAnchor(
                            blockId = "block-1",
                            strokeIds = listOf("stroke-1"),
                            anchorOriginX = 100f,
                            anchorOriginY = 120f,
                        )
                    ),
                    strokeTransforms = listOf(
                        StrokeTransform(
                            strokeId = "stroke-1",
                            translateX = 12f,
                            translateY = -4f,
                        )
                    )
                )
            )
        )

        val decoded = DocumentSerializer.decodeOrNull(DocumentSerializer.encode(document))
        assertNotNull(decoded)
        val page = decoded!!.pages.first()

        assertEquals("stroke-1", page.strokeIds.single())
        assertEquals("block-1", page.strokeAnchors.single().blockId)
        assertEquals("stroke-1", page.strokeAnchors.single().strokeIds.single())
        assertEquals(12f, page.strokeTransforms.single().translateX)
        assertEquals(-4f, page.strokeTransforms.single().translateY)
    }

    @Test
    fun encodeDecode_roundTripsFormulaBlockSourceAndRendered() {
        val formula = FormulaBlock(
            x = 220f,
            y = 140f,
            source = "x^2 + y^2 = z^2",
            rendered = "plain formula preview: x^2 + y^2 = z^2"
        )
        val document = TicDocument(pages = listOf(CanvasPage(blocks = listOf(formula))))
        val decoded = DocumentSerializer.decodeOrNull(DocumentSerializer.encode(document))

        assertNotNull(decoded)
        val decodedFormula = decoded!!.pages.first().blocks.first() as FormulaBlock
        assertEquals("x^2 + y^2 = z^2", decodedFormula.source)
        assertEquals("plain formula preview: x^2 + y^2 = z^2", decodedFormula.rendered)
        assertEquals(220f, decodedFormula.x)
        assertEquals(140f, decodedFormula.y)
    }
    @Test
    fun encodeDecode_roundTripsAssetManifestAndImageAssetReference() {
        val manifest = AssetManifestEntry(
            assetId = "asset-1",
            mimeType = "image/png",
            originalName = "sample.png",
            relativePath = "files/notes/42/assets/asset-1.png",
            width = 320,
            height = 240,
            createdAt = 1_800_000_000_000L
        )
        val image = ImageBlock(assetId = manifest.assetId, assetPath = manifest.relativePath)
        val document = TicDocument(
            pages = listOf(CanvasPage(blocks = listOf(image))),
            assetManifest = listOf(manifest)
        )

        val decoded = DocumentSerializer.decodeOrNull(DocumentSerializer.encode(document))

        assertNotNull(decoded)
        val decodedManifest = decoded!!.assetManifest.single()
        val decodedImage = decoded.pages.first().blocks.first() as ImageBlock
        assertEquals("asset-1", decodedManifest.assetId)
        assertEquals("image/png", decodedManifest.mimeType)
        assertEquals("sample.png", decodedManifest.originalName)
        assertEquals("files/notes/42/assets/asset-1.png", decodedManifest.relativePath)
        assertEquals(320, decodedManifest.width)
        assertEquals(240, decodedManifest.height)
        assertEquals("asset-1", decodedImage.assetId)
        assertEquals("files/notes/42/assets/asset-1.png", decodedImage.assetPath)
    }

}

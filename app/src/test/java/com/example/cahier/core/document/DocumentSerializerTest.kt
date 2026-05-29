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
        val image = ImageBlock(x = -140f, y = 96f, width = 256f, height = 192f, assetPath = "/tmp/a.png")
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
    fun encodeDecode_roundTripsFormulaBlockSourceAndRendered() {
        val formula = FormulaBlock(
            x = 220f,
            y = 140f,
            source = "x^2 + y^2 = z^2",
            rendered = "f(x): x^2 + y^2 = z^2"
        )
        val document = TicDocument(pages = listOf(CanvasPage(blocks = listOf(formula))))
        val decoded = DocumentSerializer.decodeOrNull(DocumentSerializer.encode(document))

        assertNotNull(decoded)
        val decodedFormula = decoded!!.pages.first().blocks.first() as FormulaBlock
        assertEquals("x^2 + y^2 = z^2", decodedFormula.source)
        assertEquals("f(x): x^2 + y^2 = z^2", decodedFormula.rendered)
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

    @Test
    fun encodeDecode_preservesMixedTextContainerNodeOrderAndContent() {
        val nodes = listOf(
            ParagraphNode(text = "intro", id = "p-1"),
            TableNode(
                rows = 1,
                columns = 2,
                cells = listOf(listOf(TableCell(text = "A"), TableCell(text = "B"))),
                id = "t-1"
            ),
            FormulaNode(source = "E=mc^2", id = "f-1"),
            ParagraphNode(text = "middle", id = "p-2"),
            ImageNode(assetPath = "/assets/diagram.png", id = "i-1"),
            TableNode(
                rows = 2,
                columns = 1,
                cells = listOf(listOf(TableCell(text = "C")), listOf(TableCell(text = "D"))),
                id = "t-2"
            ),
            ParagraphNode(text = "outro", id = "p-3")
        )
        val document = TicDocument(
            pages = listOf(
                CanvasPage(
                    blocks = listOf(
                        TextContainerBlock(
                            id = "block-1",
                            content = TextContainerContent(nodes = nodes)
                        )
                    )
                )
            )
        )

        val decoded = DocumentSerializer.decodeOrNull(DocumentSerializer.encode(document))

        assertNotNull(decoded)
        val decodedNodes = (decoded!!.pages.first().blocks.first() as TextContainerBlock).content.nodes
        assertEquals(nodes.size, decodedNodes.size)
        assertEquals(listOf("p-1", "t-1", "f-1", "p-2", "i-1", "t-2", "p-3"), decodedNodes.map { it.id })
        assertEquals("intro", (decodedNodes[0] as ParagraphNode).text)
        assertEquals("A", (decodedNodes[1] as TableNode).cells[0][0].text)
        assertEquals("E=mc^2", (decodedNodes[2] as FormulaNode).source)
        assertEquals("middle", (decodedNodes[3] as ParagraphNode).text)
        assertEquals("/assets/diagram.png", (decodedNodes[4] as ImageNode).assetPath)
        assertEquals("D", (decodedNodes[5] as TableNode).cells[1][0].text)
        assertEquals("outro", (decodedNodes[6] as ParagraphNode).text)
    }
}

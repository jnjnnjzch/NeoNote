package com.neonote

import androidx.compose.ui.geometry.Offset
import com.neonote.engine.InputEvent
import com.neonote.engine.InputMode
import com.neonote.engine.InputInkSample
import com.neonote.engine.InputPointer
import com.neonote.engine.InputRouter
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.engine.InlineStyle
import com.neonote.engine.PointerEventType
import com.neonote.engine.PointerTool
import com.neonote.engine.toPlainText
import com.neonote.input.InputDiagnostics
import com.neonote.input.withPressureSamples
import com.neonote.model.BlockFormula
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasSize
import com.neonote.model.EditorState
import com.neonote.model.EditorTool
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkLayer
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import com.neonote.model.ParagraphNode
import com.neonote.model.TableNode
import com.neonote.model.ViewportState
import com.neonote.model.RichContentBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NeoNoteEditorControllerTest {

    @Test
    fun `add page appends a blank page and switches to it`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 20f))
        val firstPageId = controller.state.currentPageId

        controller.addPage()

        assertEquals(2, controller.pageCount)
        assertEquals(2, controller.currentPageNumber)
        assertTrue(controller.state.currentPageId != firstPageId)
        assertTrue(controller.currentCanvas.objects.isEmpty())
        assertTrue(controller.currentCanvas.inkLayer.strokes.isEmpty())
    }

    @Test
    fun `switch page exposes only that page canvas for editing`() {
        val controller = NeoNoteEditorController()
        val firstPageId = controller.state.currentPageId!!
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 20f))
        val firstBoxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(firstBoxId, "first page")

        controller.addPage()
        val secondPageId = controller.state.currentPageId!!
        controller.focusOrCreateRichContentBox(CanvasPoint(100f, 120f))
        val secondBoxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(secondBoxId, "second page")

        controller.switchPage(firstPageId)
        assertEquals(1, controller.currentPageNumber)
        assertEquals("first page", (controller.currentCanvas.objects.single() as RichContentBox).toPlainText())

        controller.switchPage(secondPageId)
        assertEquals(2, controller.currentPageNumber)
        assertEquals("second page", (controller.currentCanvas.objects.single() as RichContentBox).toPlainText())
    }

    @Test
    fun `switch page clears selection focus and active ink session`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.addPage()
        controller.switchToPreviousPage()
        controller.selectCanvasObject(boxId)

        controller.switchToNextPage()

        assertTrue(controller.state.selection.selectedRefs.isEmpty())
        controller.switchToPreviousPage()
        controller.focusRichContentBox(boxId)
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(5f, 5f), tool = PointerTool.SPen)),
            ),
        )
        assertTrue(controller.activeInkStroke != null)

        controller.switchToNextPage()

        assertNull(controller.state.focusedRichContentBoxId)
        assertNull(controller.activeInkStroke)
        controller.switchToPreviousPage()
        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(false, box.isFocused)
        assertTrue(controller.currentCanvas.inkLayer.strokes.isEmpty())
    }
    @Test
    fun `toolbar equivalent commands update focused rich content`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "hello")
        controller.focusRichContentParagraph(boxId = boxId, blockIndex = 0, selectionStart = 0, selectionEnd = 5)

        controller.toggleActiveRichContentStyle(boxId = boxId, style = InlineStyle.Bold)
        controller.toggleActiveRichContentList(boxId = boxId, kind = ListKind.Todo)
        controller.insertRichContentTablePlaceholder(boxId = boxId)
        controller.insertRichContentFormulaPlaceholder(boxId = boxId)

        val box = controller.currentCanvas.objects.single() as RichContentBox
        val paragraph = assertIs<ParagraphNode>(box.content.blocks[0])
        val text = assertIs<InlineText>(paragraph.inlines.single())
        assertTrue(text.bold)
        assertEquals(ListKind.Todo, paragraph.listMetadata?.kind)
        assertFalse(paragraph.listMetadata?.checked ?: true)
        assertIs<BlockFormula>(box.content.blocks[1])
        assertIs<TableNode>(box.content.blocks[2])
        assertEquals(boxId, controller.state.focusedRichContentBoxId)
        assertTrue(box.isFocused)
    }


    @Test
    fun `toolbar placeholder commands insert after active paragraph and keep focus`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "alpha\nbravo\ncharlie")
        controller.focusRichContentParagraph(boxId = boxId, blockIndex = 1, selectionStart = 2)

        controller.insertRichContentFormulaPlaceholder(boxId = boxId, expression = "x")
        controller.insertRichContentTablePlaceholder(boxId = boxId)

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertIs<ParagraphNode>(box.content.blocks[1])
        assertIs<TableNode>(box.content.blocks[2])
        assertIs<BlockFormula>(box.content.blocks[3])
        assertIs<ParagraphNode>(box.content.blocks[4])
        assertEquals(boxId, controller.state.focusedRichContentBoxId)
        assertTrue(box.isFocused)
        assertEquals(1, controller.activeRichContentBlockIndex(boxId))
    }

    @Test
    fun `panning viewport does not move document objects`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(100f, 150f))
        val originalPosition = (controller.currentCanvas.objects.single() as RichContentBox).position

        controller.panViewportBy(screenDx = 40f, screenDy = -12f)

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(originalPosition, box.position)
        assertEquals(40f, controller.state.viewport.panOffsetX)
        assertEquals(-12f, controller.state.viewport.panOffsetY)
        assertEquals(CanvasPoint(140f, 138f), controller.documentToScreen(box.position))
    }

    @Test
    fun `zooming viewport around centroid keeps centroid document point stable without resizing objects`() {
        val controller = NeoNoteEditorController()
        controller.panViewportBy(screenDx = 20f, screenDy = -10f)
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 20f))
        val originalBox = controller.currentCanvas.objects.single() as RichContentBox
        val screenCentroid = CanvasPoint(60f, 50f)
        val documentAtCentroidBeforeZoom = controller.screenToDocument(screenCentroid)

        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = screenCentroid)

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(2f, controller.state.viewport.zoomScale)
        assertEquals(documentAtCentroidBeforeZoom, controller.screenToDocument(screenCentroid))
        assertEquals(originalBox.position, box.position)
        assertEquals(originalBox.size, box.size)
    }

    @Test
    fun `routed blank tap converts screen point to document point once`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.panViewportBy(screenDx = 50f, screenDy = 20f)
        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(0f, 0f))
        val screenTap = CanvasPoint(150f, 220f)

        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = screenTap, tool = PointerTool.Finger)),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = screenTap, tool = PointerTool.Finger)),
            ),
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(CanvasPoint(25f, 90f), box.position)
    }

    @Test
    fun `s pen routed events create committed ink stroke`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()

        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(10f, 12f), tool = PointerTool.SPen, pressure = 0.4f)),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(20f, 24f), tool = PointerTool.SPen, pressure = 0.8f)),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(20f, 24f), tool = PointerTool.SPen, pressure = 0.8f)),
            ),
        )

        val stroke = controller.currentCanvas.inkLayer.strokes.single()
        assertEquals("stroke-1", stroke.id)
        assertEquals(2, stroke.points.size)
        assertEquals(10f, stroke.points[0].x)
        assertEquals(12f, stroke.points[0].y)
        assertEquals(0.4f, stroke.points[0].pressure)
        assertEquals(16.5f, stroke.points[1].x)
        assertEquals(19.8f, stroke.points[1].y)
        assertEquals(0.8f, stroke.points[1].pressure, 0.0001f)
    }


    @Test
    fun `diagnostics throttle does not reduce committed stroke points or raw pressure`() {
        var now = 0L
        val controller = NeoNoteEditorController(
            inputDiagnosticsThrottleMillis = 50L,
            diagnosticsClockMillis = { now },
        )
        val router = InputRouter()

        val down = InputEvent(
            type = PointerEventType.Down,
            pointers = listOf(
                InputPointer(
                    id = 1,
                    position = CanvasPoint(0f, 0f),
                    tool = PointerTool.SPen,
                    pressure = 0.2f,
                    rawPressure = 0.2f,
                ),
            ),
        )
        val firstMove = InputEvent(
            type = PointerEventType.Move,
            pointers = listOf(
                InputPointer(
                    id = 1,
                    position = CanvasPoint(30f, 0f),
                    tool = PointerTool.SPen,
                    pressure = 0.6f,
                    rawPressure = 0.6f,
                    historicalSamples = listOf(
                        InputInkSample(position = CanvasPoint(10f, 0f), pressure = 0.4f, rawPressure = 0.4f),
                        InputInkSample(position = CanvasPoint(20f, 0f), pressure = 0.5f, rawPressure = 0.5f),
                    ),
                ),
            ),
        )
        val secondMove = InputEvent(
            type = PointerEventType.Move,
            pointers = listOf(
                InputPointer(
                    id = 1,
                    position = CanvasPoint(60f, 0f),
                    tool = PointerTool.SPen,
                    pressure = 1.1f,
                    rawPressure = 1.1f,
                    historicalSamples = listOf(
                        InputInkSample(position = CanvasPoint(40f, 0f), pressure = 0.8f, rawPressure = 0.8f),
                        InputInkSample(position = CanvasPoint(50f, 0f), pressure = 0.9f, rawPressure = 0.9f),
                    ),
                ),
            ),
        )
        val up = InputEvent(
            type = PointerEventType.Up,
            pointers = listOf(
                InputPointer(
                    id = 1,
                    position = CanvasPoint(60f, 0f),
                    tool = PointerTool.SPen,
                    pressure = 1.1f,
                    rawPressure = 1.1f,
                ),
            ),
        )

        assertTrue(controller.updateInputDiagnostics(down.toDiagnostics(), eventTimeMillis = now, force = true))
        controller.routeInputEvent(router, down)

        now = 5L
        assertEquals(false, controller.updateInputDiagnostics(firstMove.toDiagnostics(), eventTimeMillis = now))
        controller.routeInputEvent(router, firstMove)

        now = 10L
        assertEquals(false, controller.updateInputDiagnostics(secondMove.toDiagnostics(), eventTimeMillis = now))
        controller.routeInputEvent(router, secondMove)

        now = 12L
        assertTrue(controller.updateInputDiagnostics(up.toDiagnostics(), eventTimeMillis = now, force = true))
        controller.routeInputEvent(router, up)

        val stroke = controller.currentCanvas.inkLayer.strokes.single()
        val unthrottledController = NeoNoteEditorController(inputDiagnosticsThrottleMillis = 0L)
        val unthrottledRouter = InputRouter()
        listOf(down, firstMove, secondMove, up).forEach { event ->
            unthrottledController.updateInputDiagnostics(event.toDiagnostics())
            unthrottledController.routeInputEvent(unthrottledRouter, event)
        }
        val unthrottledStroke = unthrottledController.currentCanvas.inkLayer.strokes.single()

        assertEquals(7, stroke.points.size)
        assertEquals(unthrottledStroke.points.size, stroke.points.size)
        assertEquals(unthrottledStroke.points.map { it.pressure }, stroke.points.map { it.pressure })
        assertEquals(unthrottledStroke.points.map { it.rawPressure }, stroke.points.map { it.rawPressure })
        assertEquals(listOf(0.2f, 0.4f, 0.5f, 0.6f, 0.8f, 0.9f, 1.1f), stroke.points.map { it.rawPressure })
        assertTrue(stroke.points.last().pressure <= 1f)

        val diagnosticsText = controller.inputDiagnostics.asToolbarText()
        assertTrue("tool=SPen" in diagnosticsText)
        assertTrue("range=0.40..1.10" in diagnosticsText)
        assertTrue("samples=7" in diagnosticsText)
    }

    @Test
    fun `finger drag pans without creating ink strokes`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter(tapSlop = 8f)

        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(0f, 0f), tool = PointerTool.Finger)),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(12f, 0f), tool = PointerTool.Finger)),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(12f, 0f), tool = PointerTool.Finger)),
            ),
        )

        assertTrue(controller.currentCanvas.inkLayer.strokes.isEmpty())
        assertEquals(12f, controller.state.viewport.panOffsetX)
    }

    @Test
    fun `coalesced ink samples append in document order`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.panViewportBy(screenDx = 10f, screenDy = 20f)

        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(10f, 20f), tool = PointerTool.SPen)),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(
                    InputPointer(
                        id = 1,
                        position = CanvasPoint(40f, 50f),
                        tool = PointerTool.SPen,
                        historicalSamples = listOf(
                            InputInkSample(position = CanvasPoint(20f, 30f), pressure = 0.4f),
                            InputInkSample(position = CanvasPoint(30f, 40f), pressure = 0.6f),
                        ),
                    ),
                ),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(40f, 50f), tool = PointerTool.SPen)),
            ),
        )

        val points = controller.currentCanvas.inkLayer.strokes.single().points
        assertEquals(4, points.size)
        assertEquals(0f, points[0].x, 0.0001f)
        assertEquals(0f, points[0].y, 0.0001f)
        assertEquals(6.5f, points[1].x, 0.0001f)
        assertEquals(6.5f, points[1].y, 0.0001f)
        assertEquals(15.275f, points[2].x, 0.0001f)
        assertEquals(15.275f, points[2].y, 0.0001f)
        assertEquals(24.84625f, points[3].x, 0.0001f)
        assertEquals(24.84625f, points[3].y, 0.0001f)
        assertEquals(listOf(1f, 0.4f, 0.6f, 1f), points.map { it.rawPressure })
        assertTrue(points.map { it.pressure }.distinct().size > 1)
    }

    @Test
    fun `coalesced move samples preserve changing pressure`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()

        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(0f, 0f), tool = PointerTool.SPen, pressure = 0.2f, rawPressure = 0.2f)),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(
                    InputPointer(
                        id = 1,
                        position = CanvasPoint(30f, 0f),
                        tool = PointerTool.SPen,
                        pressure = 0.4f,
                        rawPressure = 0.4f,
                        historicalSamples = listOf(
                            InputInkSample(position = CanvasPoint(10f, 0f), pressure = 0.8f, rawPressure = 0.8f),
                            InputInkSample(position = CanvasPoint(20f, 0f), pressure = 0.6f, rawPressure = 0.6f),
                        ),
                    ),
                ),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(30f, 0f), tool = PointerTool.SPen, pressure = 0.4f, rawPressure = 0.4f)),
            ),
        )

        val points = controller.currentCanvas.inkLayer.strokes.single().points
        assertEquals(listOf(0.2f, 0.8f, 0.6f, 0.4f), points.map { it.rawPressure })
        assertTrue(points.map { it.pressure }.distinct().size > 1)
    }

    @Test
    fun `pan and zoom do not mutate stroke document coordinates`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.panViewportBy(screenDx = 50f, screenDy = 20f)
        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(0f, 0f))

        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(150f, 220f), tool = PointerTool.SPen)),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(170f, 260f), tool = PointerTool.SPen)),
            ),
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(170f, 260f), tool = PointerTool.SPen)),
            ),
        )

        val originalPoints = controller.currentCanvas.inkLayer.strokes.single().points
        assertEquals(25f, originalPoints[0].x)
        assertEquals(90f, originalPoints[0].y)
        assertEquals(31.5f, originalPoints[1].x)
        assertEquals(103f, originalPoints[1].y)

        controller.panViewportBy(screenDx = -30f, screenDy = 15f)
        controller.zoomViewportBy(zoomChange = 0.5f, screenCentroid = CanvasPoint(100f, 100f))

        assertEquals(originalPoints, controller.currentCanvas.inkLayer.strokes.single().points)
    }

    @Test
    fun `selection mode can move a selected rich content box in document coordinates under zoom`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        controller.panViewportBy(screenDx = 50f, screenDy = -20f)
        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(0f, 0f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id

        controller.setSelectionMode(true)
        controller.selectCanvasObject(boxId)
        controller.moveSelectedObjectsByScreenDelta(Offset(20f, 10f))

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertTrue(controller.state.selection.isObjectSelected(boxId))
        assertEquals(CanvasPoint(35f, 35f), box.position)
    }

    @Test
    fun `selection mode drag moves mixed lasso selection in document coordinates under zoom`() {
        val controller = NeoNoteEditorController(initialState = editorStateWithMixedCanvas())
        val router = InputRouter()

        controller.setSelectionMode(true)
        val lassoSelection = controller.selectWithLasso(
            listOf(
                CanvasPoint(0f, 0f),
                CanvasPoint(80f, 0f),
                CanvasPoint(80f, 80f),
                CanvasPoint(0f, 80f),
            ),
        )
        controller.panViewportBy(screenDx = 50f, screenDy = -20f)
        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(0f, 0f))

        val start = controller.documentToScreen(CanvasPoint(15f, 15f))
        val end = CanvasPoint(start.x + 20f, start.y + 10f)
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = start, tool = PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(id = 1, position = end, tool = PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = end, tool = PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )

        val movedBox = controller.currentCanvas.objects.single() as RichContentBox
        val movedStroke = controller.currentCanvas.inkLayer.strokes.single()
        assertTrue(lassoSelection.isObjectSelected("box-1"))
        assertTrue(lassoSelection.isStrokeSelected("stroke-1"))
        assertEquals(CanvasPoint(20f, 15f), movedBox.position)
        assertEquals(listOf(InkPoint(15f, 25f), InkPoint(70f, 25f)), movedStroke.points)
    }

    @Test
    fun `rich content box height grows with measured multiline content and selection bounds`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 20f))
        val initialBox = controller.currentCanvas.objects.single() as RichContentBox
        val text = (1..12).joinToString("\n") { "line $it" }

        controller.updateRichContentText(initialBox.id, text)
        controller.selectCanvasObject(initialBox.id)

        val box = controller.currentCanvas.objects.single() as RichContentBox
        val bounds = controller.selectedBounds!!
        assertEquals(initialBox.size.width, box.size.width)
        assertTrue(box.size.height > initialBox.size.height)
        assertEquals(box.position.y + box.size.height, bounds.bottom)
    }

    @Test
    fun `zooming viewport after content measurement does not change box document size`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 20f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, (1..10).joinToString("\n") { "line $it" })
        val sizeBeforeZoom = (controller.currentCanvas.objects.single() as RichContentBox).size

        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(100f, 100f))

        val sizeAfterZoom = (controller.currentCanvas.objects.single() as RichContentBox).size
        assertEquals(sizeBeforeZoom, sizeAfterZoom)
        assertEquals(2f, controller.state.viewport.zoomScale)
    }

    @Test
    fun `activating an existing text box in text mode focuses it for editing`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "editable")
        controller.setSelectionMode(true)
        controller.setSelectionMode(false)
        assertEquals(EditorTool.Pen, controller.state.currentTool)

        controller.activateRichContentBox(boxId)
        controller.updateRichContentText(boxId, "edited again")

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(boxId, controller.state.focusedRichContentBoxId)
        assertEquals(true, box.isFocused)
        assertTrue(controller.state.selection.selectedRefs.isEmpty())
        assertEquals("edited again", box.toPlainText())
    }

    @Test
    fun `platform input adapter keeps focused rich content input and multiline paste usable`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id

        controller.updateRichContentFromPlatformInput(
            boxId = boxId,
            previousText = "",
            nextText = "first line",
            selectionStart = 10,
            selectionEnd = 10,
            hasActiveComposition = false,
        )
        controller.updateRichContentFromPlatformInput(
            boxId = boxId,
            previousText = "first line",
            nextText = "first line\nsecond line",
            selectionStart = 22,
            selectionEnd = 22,
            hasActiveComposition = false,
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(boxId, controller.state.focusedRichContentBoxId)
        assertEquals(true, box.isFocused)
        assertEquals("first line\nsecond line", box.toPlainText())
        assertEquals(2, box.content.blocks.size)
    }

    @Test
    fun `platform input adapter preserves ime composition fallback`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id

        controller.updateRichContentFromPlatformInput(
            boxId = boxId,
            previousText = "ni",
            nextText = "你",
            selectionStart = 1,
            selectionEnd = 1,
            hasActiveComposition = true,
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals("你", box.toPlainText())
        assertEquals(boxId, controller.state.focusedRichContentBoxId)
        assertEquals(true, box.isFocused)
    }

    @Test
    fun `rich content shortcut controller paths remain usable for style and list commands`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "shortcut")

        controller.toggleRichContentStyle(
            boxId = boxId,
            style = InlineStyle.Bold,
            selectionStart = 0,
            selectionEnd = 8,
        )
        controller.toggleRichContentList(
            boxId = boxId,
            kind = ListKind.Bullet,
            selectionStart = 0,
            selectionEnd = 8,
        )

        val paragraph = (controller.currentCanvas.objects.single() as RichContentBox).content.blocks.single() as ParagraphNode
        val inline = paragraph.inlines.single() as InlineText
        assertEquals("shortcut", inline.text)
        assertEquals(true, inline.bold)
        assertEquals(ListKind.Bullet, paragraph.listMetadata?.kind)
    }


    @Test
    fun `one rich content box can edit multiple paragraph nodes independently`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "first\nsecond")

        controller.focusRichContentParagraph(boxId = boxId, blockIndex = 1, selectionStart = 6)
        controller.updateRichContentParagraphFromPlatformInput(
            boxId = boxId,
            blockIndex = 1,
            previousText = "second",
            nextText = "second!",
            selectionStart = 7,
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals("first\nsecond!", box.toPlainText())
        assertEquals(2, box.content.blocks.size)
        assertEquals(1, controller.activeRichContentBlockIndex(boxId))
        assertEquals("first", ((box.content.blocks[0] as ParagraphNode).inlines.single() as InlineText).text)
        assertEquals("second!", ((box.content.blocks[1] as ParagraphNode).inlines.single() as InlineText).text)
    }

    @Test
    fun `activating an existing text box in selection mode selects without focusing`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "select me")

        controller.setSelectionMode(true)
        controller.activateRichContentBox(boxId)
        controller.updateRichContentText(boxId, "keyboard edit should be ignored")

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertTrue(controller.state.selection.isObjectSelected(boxId))
        assertNull(controller.state.focusedRichContentBoxId)
        assertEquals(false, box.isFocused)
        assertEquals("select me", box.toPlainText())
    }

    @Test
    fun `routed selection mode tap on text box selects without focusing`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "select me")
        controller.setSelectionMode(true)
        val screenTap = controller.documentToScreen(CanvasPoint(30f, 35f))

        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = screenTap, tool = PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = screenTap, tool = PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertTrue(controller.state.selection.isObjectSelected(boxId))
        assertNull(controller.state.focusedRichContentBoxId)
        assertEquals(false, box.isFocused)
        assertEquals("select me", box.toPlainText())
    }

    @Test
    fun `routed selection mode stylus tap selects without inking`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "select me")
        controller.setSelectionMode(true)
        val screenTap = controller.documentToScreen(CanvasPoint(30f, 35f))

        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = screenTap, tool = PointerTool.SPen)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = screenTap, tool = PointerTool.SPen)),
            ),
            mode = InputMode.Selection,
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertTrue(controller.state.selection.isObjectSelected(boxId))
        assertNull(controller.state.focusedRichContentBoxId)
        assertEquals(false, box.isFocused)
        assertTrue(controller.currentCanvas.inkLayer.strokes.isEmpty())
        assertEquals("select me", box.toPlainText())
    }

    @Test
    fun `selection mode clears focus and ignores rich content text edits`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "editable")

        controller.setSelectionMode(true)
        controller.updateRichContentText(boxId, "ignored")

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(null, controller.state.focusedRichContentBoxId)
        assertEquals(false, box.isFocused)
        assertEquals("editable", box.toPlainText())
    }
    @Test
    fun `selection mode exposes active lasso path in document coordinates while drawing`() {
        val controller = NeoNoteEditorController(initialState = editorStateWithMixedCanvas())
        val router = InputRouter()
        controller.setSelectionMode(true)
        controller.panViewportBy(screenDx = 10f, screenDy = 20f)
        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(0f, 0f))

        val down = controller.documentToScreen(CanvasPoint(50f, 50f))
        val move = controller.documentToScreen(CanvasPoint(60f, 70f))
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = down, tool = PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(id = 1, position = move, tool = PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )

        assertEquals(listOf(CanvasPoint(50f, 50f), CanvasPoint(60f, 70f)), controller.activeLassoPath)

        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = move, tool = PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )

        assertTrue(controller.activeLassoPath.isEmpty())
    }

    @Test
    fun `selection mode drag can start from inside selected bounds between selected geometries`() {
        val controller = NeoNoteEditorController(initialState = editorStateWithMixedCanvas())
        val router = InputRouter()
        controller.setSelectionMode(true)
        controller.selectWithLasso(
            listOf(
                CanvasPoint(0f, 0f),
                CanvasPoint(80f, 0f),
                CanvasPoint(80f, 80f),
                CanvasPoint(0f, 80f),
            ),
        )

        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(50f, 28f), tool = PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(60f, 33f), tool = PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router = router,
            event = InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(id = 1, position = CanvasPoint(60f, 33f), tool = PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )

        val movedBox = controller.currentCanvas.objects.single() as RichContentBox
        val movedStroke = controller.currentCanvas.inkLayer.strokes.single()
        assertEquals(CanvasPoint(20f, 15f), movedBox.position)
        assertEquals(listOf(InkPoint(15f, 25f), InkPoint(70f, 25f)), movedStroke.points)
    }


    @Test
    fun `paragraph local input keeps consecutive empty paragraphs visible and resizes box`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id

        controller.updateRichContentParagraphFromPlatformInput(
            boxId = boxId,
            blockIndex = 0,
            previousText = "",
            nextText = "hello",
            selectionStart = 5,
        )
        val heightAfterHello = (controller.currentCanvas.objects.single() as RichContentBox).size.height

        controller.updateRichContentParagraphFromPlatformInput(
            boxId = boxId,
            blockIndex = 0,
            previousText = "hello",
            nextText = "hello\n",
            selectionStart = 6,
        )
        controller.updateRichContentParagraphFromPlatformInput(
            boxId = boxId,
            blockIndex = 1,
            previousText = "",
            nextText = "\n",
            selectionStart = 1,
        )
        controller.updateRichContentParagraphFromPlatformInput(
            boxId = boxId,
            blockIndex = 2,
            previousText = "",
            nextText = "\n",
            selectionStart = 1,
        )
        controller.updateRichContentParagraphFromPlatformInput(
            boxId = boxId,
            blockIndex = 3,
            previousText = "",
            nextText = "world",
            selectionStart = 5,
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals("hello\n\n\nworld", box.toPlainText())
        assertEquals(4, box.content.blocks.size)
        assertEquals("", assertIs<ParagraphNode>(box.content.blocks[1]).toPlainTextForControllerTest())
        assertEquals("", assertIs<ParagraphNode>(box.content.blocks[2]).toPlainTextForControllerTest())
        assertEquals(3, controller.activeRichContentBlockIndex(boxId))
        assertTrue(box.size.height > heightAfterHello)
        assertEquals(boxId, controller.state.focusedRichContentBoxId)
        assertTrue(box.isFocused)
    }

    @Test
    fun `toolbar actions reuse active paragraph selection after platform focus loss commit`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "hello")
        controller.focusRichContentParagraph(boxId = boxId, blockIndex = 0, selectionStart = 0, selectionEnd = 5)

        controller.commitRichContentEditing(boxId)
        controller.toggleActiveRichContentStyle(boxId = boxId, style = InlineStyle.Bold)
        controller.toggleActiveRichContentList(boxId = boxId, kind = ListKind.Bullet)

        val box = controller.currentCanvas.objects.single() as RichContentBox
        val paragraph = assertIs<ParagraphNode>(box.content.blocks.single())
        val text = assertIs<InlineText>(paragraph.inlines.single())
        assertTrue(text.bold)
        assertEquals(ListKind.Bullet, paragraph.listMetadata?.kind)
        assertEquals(boxId, controller.state.focusedRichContentBoxId)
        assertTrue(box.isFocused)
    }

    @Test
    fun `multiline rich content input expands height conservatively and delete keeps minimum height`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id

        controller.updateRichContentText(boxId, "first line\nsecond line\nthird line")
        val multilineBox = controller.currentCanvas.objects.single() as RichContentBox

        assertTrue(multilineBox.size.height > RichContentLayoutDefaults.MinimumBoxHeight)
        assertEquals(320f, multilineBox.size.width)

        controller.updateRichContentText(boxId, "")
        val emptiedBox = controller.currentCanvas.objects.single() as RichContentBox

        assertEquals(320f, emptiedBox.size.width)
        assertTrue(emptiedBox.size.height >= RichContentLayoutDefaults.MinimumBoxHeight)
        assertTrue(emptiedBox.size.height <= multilineBox.size.height)
    }

    @Test
    fun `zooming viewport does not mutate content driven rich content height`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "first line\nsecond line\nthird line\nfourth line")
        val heightBeforeZoom = (controller.currentCanvas.objects.single() as RichContentBox).size.height

        controller.zoomViewportBy(zoomChange = 1.75f, screenCentroid = CanvasPoint(100f, 100f))
        controller.zoomViewportBy(zoomChange = 0.5f, screenCentroid = CanvasPoint(100f, 100f))

        val boxAfterZoom = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(heightBeforeZoom, boxAfterZoom.size.height)
        assertTrue(boxAfterZoom.size.height >= RichContentLayoutDefaults.MinimumBoxHeight)
    }

}

private fun editorStateWithMixedCanvas(): EditorState = EditorState(
    document = NeoNoteDocument(
        id = "test-document-mixed",
        title = "Mixed selection test",
        assetStoreId = "test-assets",
        pages = listOf(
            NotePage(
                id = "test-page-mixed",
                canvas = InfiniteCanvas(
                    objects = listOf(
                        RichContentBox(
                            id = "box-1",
                            position = CanvasPoint(10f, 10f),
                            size = CanvasSize(20f, 20f),
                        ),
                    ),
                    inkLayer = InkLayer(
                        strokes = listOf(
                            InkStroke(
                                id = "stroke-1",
                                points = listOf(InkPoint(5f, 20f), InkPoint(60f, 20f)),
                            ),
                        ),
                    ),
                ),
            ),
        ),
    ),
    currentPageId = "test-page-mixed",
    viewport = ViewportState(),
)

private fun ParagraphNode.toPlainTextForControllerTest(): String = inlines.joinToString("") { inline ->
    when (inline) {
        is InlineText -> inline.text
        else -> ""
    }
}

private fun InputEvent.toDiagnostics(): InputDiagnostics = InputDiagnostics(
    tool = pointers.first().tool,
    pressure = primaryPressure,
    pointerCount = pointers.size,
).withPressureSamples(primaryInkSamples)

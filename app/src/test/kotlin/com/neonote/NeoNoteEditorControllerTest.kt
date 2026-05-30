package com.neonote

import androidx.compose.ui.geometry.Offset
import com.neonote.engine.InputEvent
import com.neonote.engine.InputMode
import com.neonote.engine.InputInkSample
import com.neonote.engine.InputPointer
import com.neonote.engine.InputRouter
import com.neonote.engine.PointerEventType
import com.neonote.engine.PointerTool
import com.neonote.engine.toPlainText
import com.neonote.model.CanvasPoint
import com.neonote.model.CanvasSize
import com.neonote.model.EditorState
import com.neonote.model.InfiniteCanvas
import com.neonote.model.InkLayer
import com.neonote.model.InkPoint
import com.neonote.model.InkStroke
import com.neonote.model.NeoNoteDocument
import com.neonote.model.NotePage
import com.neonote.model.ViewportState
import com.neonote.model.RichContentBox
import kotlin.test.Test
import kotlin.test.assertEquals
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
        assertEquals(0.66f, stroke.points[1].pressure, 0.0001f)
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

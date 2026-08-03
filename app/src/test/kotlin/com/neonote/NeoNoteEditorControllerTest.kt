package com.neonote

import com.neonote.engine.InputAction
import com.neonote.engine.InputEvent
import com.neonote.engine.InputInkSample
import com.neonote.engine.InputMode
import com.neonote.engine.InputPointer
import com.neonote.engine.InputRouter
import com.neonote.engine.PointerEventType
import com.neonote.engine.PointerTool
import com.neonote.engine.RichContentLayoutDefaults
import com.neonote.engine.InlineStyle
import com.neonote.input.InputDiagnostics
import com.neonote.model.BlockFormula
import com.neonote.model.BlockImage
import com.neonote.model.CanvasPoint
import com.neonote.model.EditorTool
import com.neonote.model.InkPoint
import com.neonote.model.InlineText
import com.neonote.model.ListKind
import com.neonote.model.ParagraphNode
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox
import com.neonote.model.TableCellAddress
import com.neonote.model.TableNode
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class NeoNoteEditorControllerTest {
    @Test
    fun `s pen routed events create committed ink stroke`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()

        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, CanvasPoint(10f, 10f), PointerTool.SPen, pressure = 0.25f)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(1, CanvasPoint(20f, 18f), PointerTool.SPen, pressure = 0.75f)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, CanvasPoint(20f, 18f), PointerTool.SPen, pressure = 0.75f)),
            ),
        )

        assertEquals(1, controller.currentCanvas.inkLayer.strokes.size)
        assertEquals(listOf(0.25f, 0.75f), controller.currentCanvas.inkLayer.strokes.single().points.map { it.pressure })
        assertNull(controller.activeInkStroke)
    }

    @Test
    fun `finger drag pans without creating ink strokes`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter(tapSlop = 4f)

        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, CanvasPoint(0f, 0f), PointerTool.Finger)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(1, CanvasPoint(12f, 9f), PointerTool.Finger)),
            ),
        )

        assertEquals(12f, controller.state.viewport.panOffsetX)
        assertEquals(9f, controller.state.viewport.panOffsetY)
        assertTrue(controller.currentCanvas.inkLayer.strokes.isEmpty())
        assertNull(controller.state.focusedRichContentBoxId)
    }

    @Test
    fun `routed blank tap converts screen point to document point once`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.panViewportBy(screenDx = 20f, screenDy = 30f)
        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(0f, 0f))

        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, CanvasPoint(100f, 130f), PointerTool.Finger)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, CanvasPoint(100f, 130f), PointerTool.Finger)),
            ),
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(CanvasPoint(40f, 50f), box.position)
        assertEquals(box.id, controller.state.focusedRichContentBoxId)
    }

    @Test
    fun `panning viewport does not move document objects`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(50f, 70f))
        val objectPosition = (controller.currentCanvas.objects.single() as RichContentBox).position

        controller.panViewportBy(screenDx = 200f, screenDy = -100f)

        assertEquals(objectPosition, (controller.currentCanvas.objects.single() as RichContentBox).position)
        assertEquals(200f, controller.state.viewport.panOffsetX)
        assertEquals(-100f, controller.state.viewport.panOffsetY)
    }

    @Test
    fun `pan and zoom do not mutate stroke document coordinates`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, CanvasPoint(10f, 10f), PointerTool.SPen, pressure = 0.4f)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(1, CanvasPoint(20f, 20f), PointerTool.SPen, pressure = 0.8f)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, CanvasPoint(20f, 20f), PointerTool.SPen, pressure = 0.8f)),
            ),
        )
        val pointsBeforeTransform = controller.currentCanvas.inkLayer.strokes.single().points

        controller.panViewportBy(screenDx = 50f, screenDy = 60f)
        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(100f, 100f))

        assertEquals(pointsBeforeTransform, controller.currentCanvas.inkLayer.strokes.single().points)
    }

    @Test
    fun `zooming viewport around centroid keeps centroid document point stable without resizing objects`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(120f, 90f))
        val boxBeforeZoom = controller.currentCanvas.objects.single() as RichContentBox
        controller.panViewportBy(screenDx = 15f, screenDy = 25f)
        val centroid = CanvasPoint(300f, 220f)
        val documentPointBefore = controller.screenToDocument(centroid)

        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = centroid)

        val documentPointAfter = controller.screenToDocument(centroid)
        val boxAfterZoom = controller.currentCanvas.objects.single() as RichContentBox
        assertTrue(abs(documentPointBefore.x - documentPointAfter.x) < 0.0001f)
        assertTrue(abs(documentPointBefore.y - documentPointAfter.y) < 0.0001f)
        assertEquals(boxBeforeZoom.position, boxAfterZoom.position)
        assertEquals(boxBeforeZoom.size, boxAfterZoom.size)
    }

    @Test
    fun `coalesced ink samples append in document order`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, CanvasPoint(1f, 1f), PointerTool.SPen, pressure = 0.2f)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(
                    InputPointer(
                        id = 1,
                        position = CanvasPoint(4f, 4f),
                        tool = PointerTool.SPen,
                        pressure = 0.8f,
                        historicalSamples = listOf(
                            InputInkSample(CanvasPoint(2f, 2f), 0.4f),
                            InputInkSample(CanvasPoint(3f, 3f), 0.6f),
                        ),
                    ),
                ),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, CanvasPoint(4f, 4f), PointerTool.SPen, pressure = 0.8f)),
            ),
        )

        assertEquals(
            listOf(
                InkPoint(1f, 1f, 0.2f),
                InkPoint(2f, 2f, 0.4f),
                InkPoint(3f, 3f, 0.6f),
                InkPoint(4f, 4f, 0.8f),
            ),
            controller.currentCanvas.inkLayer.strokes.single().points,
        )
    }

    @Test
    fun `coalesced move samples preserve changing pressure`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, CanvasPoint(1f, 1f), PointerTool.SPen, pressure = 0.15f, rawPressure = 0.15f)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(
                    InputPointer(
                        id = 1,
                        position = CanvasPoint(5f, 5f),
                        tool = PointerTool.SPen,
                        pressure = 0.95f,
                        rawPressure = 0.95f,
                        historicalSamples = listOf(
                            InputInkSample(CanvasPoint(2f, 2f), 0.25f, 0.25f),
                            InputInkSample(CanvasPoint(3f, 3f), 0.55f, 0.55f),
                            InputInkSample(CanvasPoint(4f, 4f), 0.75f, 0.75f),
                        ),
                    ),
                ),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, CanvasPoint(5f, 5f), PointerTool.SPen, pressure = 0.95f, rawPressure = 0.95f)),
            ),
        )

        assertEquals(
            listOf(0.15f, 0.25f, 0.55f, 0.75f, 0.95f),
            controller.currentCanvas.inkLayer.strokes.single().points.map { it.pressure },
        )
        assertEquals(
            listOf(0.15f, 0.25f, 0.55f, 0.75f, 0.95f),
            controller.currentCanvas.inkLayer.strokes.single().points.map { it.rawPressure },
        )
    }

    @Test
    fun `diagnostics throttle does not reduce committed stroke points or raw pressure`() {
        var now = 0L
        val controller = NeoNoteEditorController(
            inputDiagnosticsThrottleMillis = 1_000L,
            diagnosticsClockMillis = { now },
        )
        val router = InputRouter()
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, CanvasPoint(1f, 1f), PointerTool.SPen, pressure = 0.2f, rawPressure = 0.2f)),
            ),
        )
        controller.updateInputDiagnostics(InputDiagnostics(tool = PointerTool.SPen, pressure = 0.2f, rawPressure = 0.2f), eventTimeMillis = now)
        now += 10
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(
                    InputPointer(
                        id = 1,
                        position = CanvasPoint(4f, 4f),
                        tool = PointerTool.SPen,
                        pressure = 0.8f,
                        rawPressure = 0.8f,
                        historicalSamples = listOf(
                            InputInkSample(CanvasPoint(2f, 2f), 0.4f, 0.4f),
                            InputInkSample(CanvasPoint(3f, 3f), 0.6f, 0.6f),
                        ),
                    ),
                ),
            ),
        )
        controller.updateInputDiagnostics(InputDiagnostics(tool = PointerTool.SPen, pressure = 0.8f, rawPressure = 0.8f), eventTimeMillis = now)
        now += 10
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, CanvasPoint(4f, 4f), PointerTool.SPen, pressure = 0.8f, rawPressure = 0.8f)),
            ),
        )

        assertEquals(4, controller.currentCanvas.inkLayer.strokes.single().points.size)
        assertEquals(listOf(0.2f, 0.4f, 0.6f, 0.8f), controller.currentCanvas.inkLayer.strokes.single().points.map { it.rawPressure })
        assertEquals(0.2f, controller.inputDiagnostics.pressureMin)
        assertEquals(0.8f, controller.inputDiagnostics.pressureMax)
    }

    @Test
    fun `selection mode exposes active lasso path in document coordinates while drawing`() {
        val controller = NeoNoteEditorController()
        controller.setSelectionMode(true)
        val router = InputRouter()

        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, CanvasPoint(10f, 10f), PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(1, CanvasPoint(20f, 25f), PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )

        assertEquals(listOf(CanvasPoint(10f, 10f), CanvasPoint(20f, 25f)), controller.activeLassoPath)
    }

    @Test
    fun `selection mode drag moves mixed lasso selection in document coordinates under zoom`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.focusOrCreateRichContentBox(CanvasPoint(40f, 40f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(2, CanvasPoint(50f, 50f), PointerTool.SPen)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(2, CanvasPoint(60f, 60f), PointerTool.SPen)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(2, CanvasPoint(60f, 60f), PointerTool.SPen)),
            ),
        )
        val strokeId = controller.currentCanvas.inkLayer.strokes.single().id
        controller.setSelectionMode(true)
        controller.replaceSelection(objectIds = setOf(boxId), strokeIds = setOf(strokeId))
        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(0f, 0f))

        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(3, CanvasPoint(100f, 100f), PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(3, CanvasPoint(120f, 140f), PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(3, CanvasPoint(120f, 140f), PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )

        val movedBox = controller.currentCanvas.objects.single() as RichContentBox
        val movedStroke = controller.currentCanvas.inkLayer.strokes.single()
        assertEquals(CanvasPoint(50f, 60f), movedBox.position)
        assertEquals(CanvasPoint(60f, 70f), CanvasPoint(movedStroke.points.first().x, movedStroke.points.first().y))
    }

    @Test
    fun `selection mode drag can start from inside selected bounds between selected geometries`() {
        val controller = NeoNoteEditorController()
        val router = InputRouter()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 10f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(4, CanvasPoint(400f, 10f), PointerTool.SPen)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(4, CanvasPoint(420f, 20f), PointerTool.SPen)),
            ),
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(4, CanvasPoint(420f, 20f), PointerTool.SPen)),
            ),
        )
        val strokeId = controller.currentCanvas.inkLayer.strokes.single().id
        controller.setSelectionMode(true)
        controller.replaceSelection(objectIds = setOf(boxId), strokeIds = setOf(strokeId))
        val bounds = assertNotNull(controller.selectedBounds)
        val interiorGap = CanvasPoint((bounds.left + bounds.right) / 2f, (bounds.top + bounds.bottom) / 2f)

        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(5, interiorGap, PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(5, CanvasPoint(interiorGap.x + 30f, interiorGap.y + 15f), PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )

        val movedBox = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(CanvasPoint(40f, 25f), movedBox.position)
    }

    @Test
    fun `selection mode can move a selected rich content box in document coordinates under zoom`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(20f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.setSelectionMode(true)
        controller.selectCanvasObject(boxId)
        controller.zoomViewportBy(zoomChange = 2f, screenCentroid = CanvasPoint(0f, 0f))
        val router = InputRouter()

        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, CanvasPoint(60f, 80f), PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(InputPointer(1, CanvasPoint(100f, 120f), PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, CanvasPoint(100f, 120f), PointerTool.Finger)),
            ),
            mode = InputMode.Selection,
        )

        assertEquals(CanvasPoint(40f, 50f), (controller.currentCanvas.objects.single() as RichContentBox).position)
    }

    @Test
    fun `routed selection mode tap on text box selects without focusing`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 20f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.setSelectionMode(true)
        val router = InputRouter()
        val tapPoint = CanvasPoint(15f, 25f)

        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, tapPoint, PointerTool.Finger)),
                targetObjectId = boxId,
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, tapPoint, PointerTool.Finger)),
                targetObjectId = boxId,
            ),
            mode = InputMode.Selection,
        )

        assertTrue(controller.state.selection.isObjectSelected(boxId))
        assertNull(controller.state.focusedRichContentBoxId)
        assertEquals(EditorTool.Selection, controller.state.currentTool)
    }

    @Test
    fun `routed selection mode stylus tap selects without inking`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 20f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.setSelectionMode(true)
        val router = InputRouter()
        val tapPoint = CanvasPoint(15f, 25f)

        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Down,
                pointers = listOf(InputPointer(1, tapPoint, PointerTool.SPen)),
                targetObjectId = boxId,
            ),
            mode = InputMode.Selection,
        )
        controller.routeInputEvent(
            router,
            InputEvent(
                type = PointerEventType.Up,
                pointers = listOf(InputPointer(1, tapPoint, PointerTool.SPen)),
                targetObjectId = boxId,
            ),
            mode = InputMode.Selection,
        )

        assertTrue(controller.currentCanvas.inkLayer.strokes.isEmpty())
        assertTrue(controller.state.selection.isObjectSelected(boxId))
    }

    @Test
    fun `switch page exposes only that page canvas for editing`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 20f))
        val firstPageId = controller.state.currentPageId
        val firstPageObjectId = (controller.currentCanvas.objects.single() as RichContentBox).id

        controller.addPage()
        val secondPageId = controller.state.currentPageId
        assertNotEquals(firstPageId, secondPageId)
        assertTrue(controller.currentCanvas.objects.isEmpty())
        controller.focusOrCreateRichContentBox(CanvasPoint(30f, 40f))
        val secondPageObjectId = (controller.currentCanvas.objects.single() as RichContentBox).id
        assertNotEquals(firstPageObjectId, secondPageObjectId)

        controller.switchPage(requireNotNull(firstPageId))
        assertEquals(firstPageObjectId, (controller.currentCanvas.objects.single() as RichContentBox).id)
        controller.switchPage(requireNotNull(secondPageId))
        assertEquals(secondPageObjectId, (controller.currentCanvas.objects.single() as RichContentBox).id)
    }

    @Test
    fun `add page appends a blank page and switches to it`() {
        val controller = NeoNoteEditorController()
        val firstPageId = controller.state.currentPageId

        controller.addPage()

        assertEquals(2, controller.pageCount)
        assertEquals(2, controller.currentPageNumber)
        assertNotEquals(firstPageId, controller.state.currentPageId)
        assertTrue(controller.currentCanvas.objects.isEmpty())
        assertNull(controller.state.focusedRichContentBoxId)
        assertTrue(controller.state.selection.selectedRefs.isEmpty())
    }

    @Test
    fun `switch page clears selection focus and active ink session`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 20f))
        val firstPageId = requireNotNull(controller.state.currentPageId)
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.addPage()
        val secondPageId = requireNotNull(controller.state.currentPageId)
        controller.switchPage(firstPageId)
        controller.setSelectionMode(true)
        controller.selectCanvasObject(boxId)
        controller.setSelectionMode(false)
        controller.focusRichContentBox(boxId)
        controller.beginInk(CanvasPoint(1f, 1f), pressure = 0.4f)

        controller.switchPage(secondPageId)

        assertTrue(controller.state.selection.selectedRefs.isEmpty())
        assertNull(controller.state.focusedRichContentBoxId)
        assertNull(controller.activeInkStroke)
    }

    @Test
    fun `multiline rich content input expands height conservatively and delete keeps minimum height`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(20f, 20f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        val initialHeight = (controller.currentCanvas.objects.single() as RichContentBox).size.height

        controller.updateRichContentText(boxId, "alpha\nbeta\ngamma\ndelta")
        val grownHeight = (controller.currentCanvas.objects.single() as RichContentBox).size.height
        controller.updateRichContentText(boxId, "")
        val shrunkHeight = (controller.currentCanvas.objects.single() as RichContentBox).size.height

        assertTrue(grownHeight > initialHeight)
        assertTrue(shrunkHeight < grownHeight)
        assertTrue(shrunkHeight >= RichContentLayoutDefaults.MinimumBoxHeight)
    }

    @Test
    fun `rich content box height grows with measured multiline content and selection bounds`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(20f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, (1..14).joinToString("\n") { "line $it" })
        val box = controller.currentCanvas.objects.single() as RichContentBox
        controller.setSelectionMode(true)
        controller.selectCanvasObject(boxId)
        val bounds = assertNotNull(controller.selectedBounds)

        assertEquals(box.position.x, bounds.left)
        assertEquals(box.position.y, bounds.top)
        assertEquals(box.position.x + box.size.width, bounds.right)
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
        controller.setTool(EditorTool.Text)

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
            nextText = "alpha\nbeta",
            selectionStart = 10,
        )
        controller.updateRichContentFromPlatformInput(
            boxId = boxId,
            previousText = "alpha\nbeta",
            nextText = "alpha\nbeta\ngamma\ndelta",
            selectionStart = 22,
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals("alpha\nbeta\ngamma\ndelta", box.toPlainText())
        assertTrue(box.size.height > RichContentLayoutDefaults.MinimumBoxHeight)
    }

    @Test
    fun `platform input adapter preserves ime composition fallback`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id

        controller.updateRichContentFromPlatformInput(
            boxId = boxId,
            previousText = "",
            nextText = "ni",
            selectionStart = 2,
            hasActiveComposition = true,
        )
        controller.updateRichContentFromPlatformInput(
            boxId = boxId,
            previousText = "ni",
            nextText = "你",
            selectionStart = 1,
            hasActiveComposition = false,
        )

        assertEquals("你", (controller.currentCanvas.objects.single() as RichContentBox).toPlainText())
    }

    @Test
    fun `selection mode clears focus and ignores rich content text edits`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "before")

        controller.setSelectionMode(true)
        controller.updateRichContentText(boxId, "after")

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals("before", box.toPlainText())
        assertNull(controller.state.focusedRichContentBoxId)
        assertEquals(false, box.isFocused)
    }

    @Test
    fun `activating an existing text box in selection mode selects without focusing`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(25f, 30f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.setSelectionMode(true)

        controller.activateRichContentBox(boxId)

        assertTrue(controller.state.selection.isObjectSelected(boxId))
        assertNull(controller.state.focusedRichContentBoxId)
        assertEquals(EditorTool.Selection, controller.state.currentTool)
    }

    @Test
    fun `one rich content box can edit multiple paragraph nodes independently`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 10f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "first\nsecond")

        controller.focusRichContentParagraph(boxId, blockIndex = 1, selectionStart = 6)
        controller.updateRichContentParagraphFromPlatformInput(
            boxId = boxId,
            blockIndex = 1,
            previousText = "second",
            nextText = "changed",
            selectionStart = 7,
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        val paragraphs = box.content.blocks.filterIsInstance<ParagraphNode>()
        assertEquals("first", (paragraphs[0].inlines.single() as InlineText).text)
        assertEquals("changed", (paragraphs[1].inlines.single() as InlineText).text)
    }

    @Test
    fun `paragraph local input keeps consecutive empty paragraphs visible and resizes box`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 10f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "first\n\nthird")
        val heightBefore = (controller.currentCanvas.objects.single() as RichContentBox).size.height

        controller.focusRichContentParagraph(boxId, blockIndex = 1, selectionStart = 0)
        controller.updateRichContentParagraphFromPlatformInput(
            boxId = boxId,
            blockIndex = 1,
            previousText = "",
            nextText = "middle\nnext",
            selectionStart = 11,
        )

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals("first\nmiddle\nnext\nthird", box.toPlainText())
        assertEquals(4, box.content.blocks.filterIsInstance<ParagraphNode>().size)
        assertTrue(box.size.height > heightBefore)
    }

    @Test
    fun `toolbar actions reuse active paragraph selection after platform focus loss commit`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 10f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "first\nsecond")
        controller.focusRichContentParagraph(boxId, blockIndex = 1, selectionStart = 0, selectionEnd = 6)

        controller.commitRichContentEditing(boxId)
        controller.toggleActiveRichContentStyle(boxId, InlineStyle.Bold)

        val paragraphs = (controller.currentCanvas.objects.single() as RichContentBox).content.blocks.filterIsInstance<ParagraphNode>()
        assertEquals("first", (paragraphs[0].inlines.single() as InlineText).text)
        assertEquals("second", (paragraphs[1].inlines.single() as InlineText).text)
        assertEquals(false, (paragraphs[0].inlines.single() as InlineText).bold)
        assertEquals(true, (paragraphs[1].inlines.single() as InlineText).bold)
    }

    @Test
    fun `inserted object blocks keep editable trailing paragraph and focus target`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 10f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "before")
        controller.focusRichContentParagraph(boxId, blockIndex = 0, selectionStart = 6)

        controller.insertRichContentFormulaPlaceholder(boxId, expression = "x^2")

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(listOf(ParagraphNode::class, BlockFormula::class, ParagraphNode::class), box.content.blocks.map { it::class })
        assertEquals(2, controller.activeRichContentBlockIndex(boxId))
    }

    @Test
    fun `formula block can be focused edited and blurred in place`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 10f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "before")
        controller.insertRichContentFormulaPlaceholder(boxId, expression = "x")

        controller.focusRichContentFormulaBlock(boxId, blockIndex = 1)
        assertEquals(1, controller.activeRichContentFormulaBlockIndex(boxId))
        controller.updateRichContentFormulaExpression(boxId, blockIndex = 1, expression = "x^2 + y^2")
        controller.blurRichContentFormulaBlock(boxId, blockIndex = 1)

        val formula = (controller.currentCanvas.objects.single() as RichContentBox).content.blocks[1] as BlockFormula
        assertEquals("x^2 + y^2", formula.expression)
        assertNull(controller.activeRichContentFormulaBlockIndex(boxId))
    }

    @Test
    fun `toolbar placeholder commands insert after active paragraph and keep focus`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 10f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "before")

        controller.insertRichContentTablePlaceholder(boxId)
        controller.insertRichContentFormulaPlaceholder(boxId, expression = "x^2")
        controller.insertRichContentImagePlaceholder(boxId, assetId = "asset-1", altText = "figure")

        val box = controller.currentCanvas.objects.single() as RichContentBox
        assertEquals(1, box.content.blocks.count { it is TableNode })
        assertEquals(1, box.content.blocks.count { it is BlockFormula })
        assertEquals(1, box.content.blocks.count { it is BlockImage })
        assertEquals(boxId, controller.state.focusedRichContentBoxId)
        assertEquals(EditorTool.Text, controller.state.currentTool)
    }

    @Test
    fun `toolbar equivalent commands update focused rich content`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 10f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "hello")

        controller.toggleRichContentStyle(boxId, InlineStyle.Bold, selectionStart = 0, selectionEnd = 5)
        controller.toggleRichContentList(boxId, ListKind.Bullet, selectionStart = 0, selectionEnd = 5)

        val paragraph = (controller.currentCanvas.objects.single() as RichContentBox).content.blocks.single() as ParagraphNode
        val text = paragraph.inlines.single() as InlineText
        assertEquals(true, text.bold)
        assertEquals(ListKind.Bullet, paragraph.listKind)
    }

    @Test
    fun `rich content shortcut controller paths remain usable for style and list commands`() {
        val controller = NeoNoteEditorController()
        controller.focusOrCreateRichContentBox(CanvasPoint(10f, 10f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.updateRichContentText(boxId, "hello")

        controller.toggleRichContentParagraphStyle(
            boxId = boxId,
            blockIndex = 0,
            style = InlineStyle.Italic,
            selectionStart = 0,
            selectionEnd = 5,
        )
        controller.toggleRichContentParagraphList(
            boxId = boxId,
            blockIndex = 0,
            kind = ListKind.Numbered,
            selectionStart = 0,
            selectionEnd = 5,
        )

        val paragraph = (controller.currentCanvas.objects.single() as RichContentBox).content.blocks.single() as ParagraphNode
        val text = paragraph.inlines.single() as InlineText
        assertEquals(true, text.italic)
        assertEquals(ListKind.Numbered, paragraph.listKind)
    }
}

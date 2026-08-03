package com.neonote

import com.neonote.engine.InputAction
import com.neonote.engine.InputEvent
import com.neonote.engine.InputMode
import com.neonote.engine.InputPointer
import com.neonote.engine.InputRouter
import com.neonote.engine.PointerEventType
import com.neonote.engine.PointerTool
import com.neonote.model.CanvasPoint
import com.neonote.model.EditorTool
import com.neonote.model.RichContentBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class InteractionRegressionTest {
    @Test
    fun `physical eraser overrides selection mode`() {
        val result = InputRouter().route(
            canvas = com.neonote.model.InfiniteCanvas(),
            event = InputEvent(
                type = PointerEventType.Move,
                pointers = listOf(
                    InputPointer(
                        id = 9,
                        position = CanvasPoint(80f, 90f),
                        tool = PointerTool.Eraser,
                    ),
                ),
            ),
            mode = InputMode.Selection,
        )

        assertIs<InputAction.EraseAt>(result.action)
    }

    @Test
    fun `undo preserves the active user tool`() {
        val controller = NeoNoteEditorController()
        controller.setTool(EditorTool.Pen)
        controller.renameDocument("Renamed")

        controller.undo()

        assertEquals(EditorTool.Pen, controller.state.currentTool)
    }

    @Test
    fun `selecting an object does not dirty the document`() {
        val controller = NeoNoteEditorController()
        controller.setTool(EditorTool.Text)
        controller.focusOrCreateRichContentBox(CanvasPoint(40f, 40f))
        val boxId = (controller.currentCanvas.objects.single() as RichContentBox).id
        controller.setTool(EditorTool.Selection)
        val revisionBeforeSelection = controller.state.document.revision

        controller.selectCanvasObject(boxId)

        assertEquals(revisionBeforeSelection, controller.state.document.revision)
    }

    @Test
    fun `page navigation does not mutate document revision`() {
        val controller = NeoNoteEditorController()
        controller.addPage()
        val revisionBeforeNavigation = controller.state.document.revision

        controller.switchToPreviousPage()

        assertEquals(revisionBeforeNavigation, controller.state.document.revision)
    }
}

package com.neonote

import com.neonote.model.CanvasPoint
import com.neonote.model.EditorTool
import com.neonote.model.RichContentBox
import kotlin.test.Test
import kotlin.test.assertTrue

class RichContentBoxSizingTest {
    @Test
    fun newTextBoxUsesReadableScreenAwareWidth() {
        val controller = NeoNoteEditorController()
        controller.updateViewportMetrics(1600, 2.5f)
        controller.setTool(EditorTool.Text)
        controller.focusOrCreateRichContentBox(CanvasPoint(200f, 200f))
        val box = controller.currentCanvas.objects.filterIsInstance<RichContentBox>().single()
        assertTrue(box.size.width >= 800f)
        assertTrue(box.size.height >= 150f)
    }

    @Test
    fun measuredComposeContentDrivesAutoHeight() {
        val controller = NeoNoteEditorController()
        controller.updateViewportMetrics(1600, 2f)
        controller.setTool(EditorTool.Text)
        controller.focusOrCreateRichContentBox(CanvasPoint(200f, 200f))
        val id = controller.currentCanvas.objects.filterIsInstance<RichContentBox>().single().id
        controller.updateRichContentBoxMeasuredHeight(id, 460f)
        val grown = controller.currentCanvas.objects.filterIsInstance<RichContentBox>().single()
        assertTrue(grown.size.height >= 460f)
    }
}

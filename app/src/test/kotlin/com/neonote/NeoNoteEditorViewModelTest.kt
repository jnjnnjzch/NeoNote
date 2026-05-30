package com.neonote

import com.neonote.model.CanvasPoint
import kotlin.test.Test
import kotlin.test.assertSame
import kotlin.test.assertTrue

class NeoNoteEditorViewModelTest {

    @Test
    fun `view model exposes one stable editor controller instance`() {
        val viewModel = NeoNoteEditorViewModel()

        val firstController = viewModel.controller
        firstController.focusOrCreateRichContentBox(CanvasPoint(12f, 34f))

        assertSame(firstController, viewModel.controller)
        assertTrue(viewModel.controller.currentCanvas.objects.isNotEmpty())
    }
}

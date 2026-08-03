package com.neonote

import androidx.lifecycle.ViewModel

/**
 * Activity-scoped holder for the editor controller.
 *
 * Keeping the controller in a ViewModel lets Compose rebuild and the Activity
 * recreate during configuration changes without dropping the in-memory document,
 * viewport, selection, or active editor state.
 */
public class NeoNoteEditorViewModel : ViewModel() {
    public val controller: NeoNoteEditorController = NeoNoteEditorController(
        initialState = createTestEditorState().let { state ->
            state.copy(document = state.document.copy(title = "Untitled Note"))
        },
    )
}

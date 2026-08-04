package com.neonote

import androidx.lifecycle.ViewModel

/** Activity-scoped editor holder surviving configuration changes. */
public class NeoNoteEditorViewModel : ViewModel() {
    public val controller: NeoNoteEditorController = NeoNoteEditorController(
        initialState = createInitialEditorState(),
    )
}

package com.neonote.model

/**
 * Stable reference to something the unified selection engine can select.
 */
sealed interface SelectableRef {
    val id: String
}

data class CanvasObjectRef(
    val objectId: String,
) : SelectableRef {
    override val id: String get() = objectId
}

data class InkStrokeRef(
    val strokeId: String,
) : SelectableRef {
    override val id: String get() = strokeId
}

/**
 * Canonical selection state shared by EditorState and SelectionEngine.
 */
data class SelectionState(
    val selectedRefs: Set<SelectableRef> = emptySet(),
) {
    val selectedObjectIds: Set<String>
        get() = selectedRefs.filterIsInstance<CanvasObjectRef>().mapTo(mutableSetOf()) { it.objectId }

    val selectedStrokeIds: Set<String>
        get() = selectedRefs.filterIsInstance<InkStrokeRef>().mapTo(mutableSetOf()) { it.strokeId }

    fun isObjectSelected(id: String): Boolean = CanvasObjectRef(id) in selectedRefs

    fun isStrokeSelected(id: String): Boolean = InkStrokeRef(id) in selectedRefs
}

package com.neonote.engine

import com.neonote.model.CanvasSize
import com.neonote.model.RichContent
import com.neonote.model.RichContentBox

/** Convenience facade for applying measured rich-content height to boxes. */
public class RichContentMeasurer(
    private val layoutEngine: RichContentLayoutEngine = RichContentLayoutEngine(),
) {
    public fun measure(content: RichContent, availableWidth: Float): RichContentLayoutResult =
        layoutEngine.layout(content = content, availableWidth = availableWidth)

    public fun measure(box: RichContentBox): RichContentLayoutResult =
        measure(content = box.content, availableWidth = box.size.width)

    public fun resizeBoxToMeasuredContent(box: RichContentBox): RichContentBox {
        val measured = measure(box)
        return box.copy(size = CanvasSize(width = box.size.width, height = measured.measuredSize.height))
    }
}

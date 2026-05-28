package com.example.cahier.features.drawing

import java.util.UUID

object StrokeIdMapper {
    fun <T> remapIds(
        oldStrokes: List<T>,
        newStrokes: List<T>,
        oldIds: List<String>
    ): List<String> {
        if (newStrokes.isEmpty()) return emptyList()
        if (oldStrokes.isEmpty() || oldIds.size != oldStrokes.size) {
            return List(newStrokes.size) { UUID.randomUUID().toString() }
        }
        val usedOldIndices = BooleanArray(oldStrokes.size)
        return newStrokes.map { candidate ->
            val found = oldStrokes.indices.firstOrNull { idx ->
                !usedOldIndices[idx] && oldStrokes[idx] == candidate
            }
            if (found != null) {
                usedOldIndices[found] = true
                oldIds[found]
            } else {
                UUID.randomUUID().toString()
            }
        }
    }
}

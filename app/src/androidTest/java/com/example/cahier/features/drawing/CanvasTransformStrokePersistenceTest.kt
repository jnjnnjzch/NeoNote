package com.example.cahier.features.drawing

import android.graphics.Color
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.brush.StockBrushes
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cahier.core.ui.Converters
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CanvasTransformStrokePersistenceTest {

    @Test
    fun panZoomedAuthoredStrokeCoordinates_surviveSaveReloadInDocumentSpace() {
        val transform = CanvasTransform(scale = 1.8f, panX = -36f, panY = 92f)
        val documentPoints = listOf(40f to 55f, 96f to 70f, 130f to 110f)
        val inputs = MutableStrokeInputBatch()
        documentPoints.forEachIndexed { index, (documentX, documentY) ->
            val screenX = CanvasTransformMapper.docToScreenX(documentX, transform)
            val screenY = CanvasTransformMapper.docToScreenY(documentY, transform)
            inputs.add(
                type = InputToolType.STYLUS,
                x = CanvasTransformMapper.screenToDocX(screenX, transform),
                y = CanvasTransformMapper.screenToDocY(screenY, transform),
                elapsedTimeMillis = index * 10L,
                pressure = 0.5f
            )
        }
        val brush = Brush.createWithColorIntArgb(
            family = StockBrushes.marker(),
            colorIntArgb = Color.BLACK,
            size = 8f,
            epsilon = 0.1f
        )
        val stroke = Stroke(brush = brush, inputs = inputs)

        val serialized = Converters().serializeStroke(stroke, emptyList())
        val reloaded = Converters().deserializeStrokeFromString(serialized, emptyList())!!

        assertEquals(documentPoints.size, reloaded.inputs.size)
        documentPoints.forEachIndexed { index, (documentX, documentY) ->
            assertEquals(documentX, reloaded.inputs[index].x, 0.0001f)
            assertEquals(documentY, reloaded.inputs[index].y, 0.0001f)
        }
    }
}

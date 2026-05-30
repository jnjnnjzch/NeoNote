package com.neonote.input

import com.neonote.engine.InputInkSample
import com.neonote.engine.PointerTool
import com.neonote.model.CanvasPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ComposeInputAdapterTest {
    @Test
    fun `s pen stylus tool classification wins over touch source`() {
        val tool = classifyPointerTool(
            composeType = PlatformPointerType.Touch,
            androidToolType = AndroidToolTypes.Stylus,
            androidSource = AndroidInputSources.Touchscreen,
        )

        assertEquals(PointerTool.SPen, tool)
    }

    @Test
    fun `finger classification uses finger tool type`() {
        val tool = classifyPointerTool(
            composeType = PlatformPointerType.Unknown,
            androidToolType = AndroidToolTypes.Finger,
            androidSource = AndroidInputSources.Touchscreen,
        )

        assertEquals(PointerTool.Finger, tool)
    }

    @Test
    fun `mouse classification uses android source when tool type is unknown`() {
        val tool = classifyPointerTool(
            composeType = PlatformPointerType.Unknown,
            androidToolType = AndroidToolTypes.Unknown,
            androidSource = AndroidInputSources.Mouse,
        )

        assertEquals(PointerTool.Mouse, tool)
    }

    @Test
    fun `diagnostic text includes pressure pointer count and source`() {
        val diagnostics = InputDiagnostics(
            tool = PointerTool.SPen,
            pressure = 0.625f,
            pointerCount = 2,
            deviceId = 7,
            source = AndroidInputSources.Stylus,
            sourceDescription = describeAndroidSource(AndroidInputSources.Stylus),
        )

        val text = diagnostics.asToolbarText()

        assertTrue("tool=SPen" in text)
        assertTrue("pressure=0.63" in text)
        assertTrue("range=0.63..0.63" in text)
        assertTrue("samples=1" in text)
        assertTrue("hist=0" in text)
        assertTrue("pointers=2" in text)
        assertTrue("device=7" in text)
        assertTrue("source=stylus" in text)
    }

    @Test
    fun `diagnostic text includes pressure range and historical sample count`() {
        val diagnostics = InputDiagnostics(
            tool = PointerTool.SPen,
            pressure = 0.8f,
            pointerCount = 1,
            sourceDescription = describeAndroidSource(AndroidInputSources.Stylus),
        ).withPressureSamples(
            listOf(
                InputInkSample(position = CanvasPoint(1f, 1f), pressure = 0.25f, rawPressure = 0.25f),
                InputInkSample(position = CanvasPoint(2f, 2f), pressure = 0.6f, rawPressure = 0.6f),
                InputInkSample(position = CanvasPoint(3f, 3f), pressure = 0.8f, rawPressure = 0.8f),
            ),
        )

        val text = diagnostics.asToolbarText()

        assertTrue("pressure=0.80" in text)
        assertTrue("range=0.25..0.80" in text)
        assertTrue("samples=3" in text)
        assertTrue("hist=2" in text)
    }
}

package com.neonote.input

import com.neonote.engine.PointerTool
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
        assertTrue("pointers=2" in text)
        assertTrue("device=7" in text)
        assertTrue("source=stylus" in text)
    }
}

package com.neonote

import com.neonote.engine.InkPressureNormalizer
import com.neonote.engine.InkStrokeSmoother
import com.neonote.engine.InkStrokeWidthMapper
import com.neonote.model.InkPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InkQualityTest {
    @Test
    fun `pressure normalization clamps display pressure and preserves raw pressure`() {
        val normalizer = InkPressureNormalizer()

        val point = normalizer.normalize(InkPoint(x = 1f, y = 2f, pressure = 1.7f))

        assertEquals(1f, point.pressure)
        assertEquals(1.7f, point.rawPressure)
    }

    @Test
    fun `invalid pressure normalizes to fallback pressure`() {
        val normalizer = InkPressureNormalizer(fallbackPressure = 0.42f)

        val point = normalizer.normalize(InkPoint(x = 1f, y = 2f, pressure = Float.NaN))

        assertEquals(0.42f, point.pressure)
        assertTrue(point.rawPressure?.isNaN() == true)
    }

    @Test
    fun `stroke width mapping is monotonic and bounded`() {
        val light = InkStrokeWidthMapper.widthForPressure(0f)
        val medium = InkStrokeWidthMapper.widthForPressure(0.5f)
        val heavy = InkStrokeWidthMapper.widthForPressure(1f)

        assertEquals(InkStrokeWidthMapper.MinStrokeWidth, light)
        assertEquals(InkStrokeWidthMapper.MaxStrokeWidth, heavy)
        assertTrue(light < medium)
        assertTrue(medium < heavy)
        assertEquals(InkStrokeWidthMapper.MinStrokeWidth, InkStrokeWidthMapper.widthForPressure(-10f))
        assertEquals(InkStrokeWidthMapper.MaxStrokeWidth, InkStrokeWidthMapper.widthForPressure(10f))
    }

    @Test
    fun `segment width uses adjacent point pressure average`() {
        val start = InkPoint(x = 0f, y = 0f, pressure = 0.2f)
        val end = InkPoint(x = 10f, y = 0f, pressure = 0.8f)

        val width = InkStrokeWidthMapper.widthForSegment(start, end)

        assertEquals(InkStrokeWidthMapper.widthForPressure(0.5f), width)
        assertTrue(width > InkStrokeWidthMapper.widthForPressure(start.pressure))
        assertTrue(width < InkStrokeWidthMapper.widthForPressure(end.pressure))
    }

    @Test
    fun `smoothing keeps first point unchanged`() {
        val smoother = InkStrokeSmoother(smoothing = 0.5f)
        val current = InkPoint(x = 10f, y = 20f, pressure = 0.8f)

        assertEquals(current, smoother.smooth(previous = null, current = current))
    }

    @Test
    fun `smoothing stays between previous and current samples`() {
        val smoother = InkStrokeSmoother(smoothing = 0.5f)
        val previous = InkPoint(x = 0f, y = 10f, pressure = 0.2f)
        val current = InkPoint(x = 10f, y = 0f, pressure = 0.8f)

        val smoothed = smoother.smooth(previous = previous, current = current)

        assertTrue(smoothed.x in previous.x..current.x)
        assertTrue(smoothed.y in current.y..previous.y)
        assertEquals(current.pressure, smoothed.pressure)
    }

    @Test
    fun `smoothing diagnostics compare raw and smoothed samples without changing pressure`() {
        val smoother = InkStrokeSmoother(smoothing = 0.5f)
        val previous = InkPoint(x = 0f, y = 0f, pressure = 0.2f)
        val current = InkPoint(x = 10f, y = 0f, pressure = 0.8f)

        val result = smoother.smoothWithDiagnostics(previous = previous, current = current)

        assertEquals(5f, result.point.x)
        assertEquals(0f, result.point.y)
        assertEquals(current.pressure, result.point.pressure)
        assertTrue(result.diagnostics.didSmooth)
        assertEquals(current.x, result.diagnostics.rawX)
        assertEquals(result.point.x, result.diagnostics.smoothedX)
        assertEquals(-5f, result.diagnostics.deltaX)
        assertEquals(5f, result.diagnostics.deltaDistance)
    }
}

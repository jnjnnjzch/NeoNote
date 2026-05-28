package com.example.cahier.features.drawing

import org.junit.Assert.assertTrue
import org.junit.Test

class PressureCurveMapperTest {

    @Test
    fun mapPressureToScale_respectsBounds() {
        val low = PressureCurveMapper.mapPressureToScale(-1f, 0.1f)
        val high = PressureCurveMapper.mapPressureToScale(2f, 10f)
        assertTrue(low >= 0.5f && low <= 2.0f)
        assertTrue(high >= 0.5f && high <= 2.0f)
    }

    @Test
    fun mapPressureToScale_higherPressureGivesLargerScale() {
        val curve = 1.2f
        val p1 = PressureCurveMapper.mapPressureToScale(0.2f, curve)
        val p2 = PressureCurveMapper.mapPressureToScale(0.8f, curve)
        assertTrue(p2 > p1)
    }

    @Test
    fun mapPressureToScale_curveChangesResponse() {
        val pressure = 0.5f
        val soft = PressureCurveMapper.mapPressureToScale(pressure, 0.6f)
        val hard = PressureCurveMapper.mapPressureToScale(pressure, 1.8f)
        assertTrue(hard > soft)
    }
}

package com.example.cahier.features.drawing

object PressureCurveMapper {
    fun mapPressureToScale(pressure: Float, curve: Float): Float {
        val p = pressure.coerceIn(0f, 1f)
        val c = curve.coerceIn(0.5f, 2.0f)
        val normalized = Math.pow(p.toDouble(), (1.0 / c.toDouble())).toFloat()
        return (0.5f + normalized * 1.5f).coerceIn(0.5f, 2.0f)
    }
}

package com.neonote

import kotlin.test.Test
import kotlin.test.assertEquals

class AssetImageLoaderTest {
    @Test
    fun `sample size follows destination and maximum dimension`() {
        assertEquals(4, AssetImageLoader.calculateSampleSize(4000, 3000, 900, 700))
        assertEquals(4, AssetImageLoader.calculateSampleSize(5000, 1000, 1800, 800))
        assertEquals(1, AssetImageLoader.calculateSampleSize(800, 600, 1200, 900))
    }
}

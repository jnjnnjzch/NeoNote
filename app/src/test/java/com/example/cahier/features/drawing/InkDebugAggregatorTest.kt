package com.example.cahier.features.drawing

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class InkDebugAggregatorTest {

    @Test
    fun ingest_beforePublishInterval_returnsNull() {
        val agg = InkDebugAggregator(publishIntervalMs = 100L)
        val sample = InkDebugSample(0.5f, "stylus", 0, false, false, null)

        val first = agg.ingest(0L, sample, InkDebugMetrics())
        val second = agg.ingest(50L, sample, InkDebugMetrics())

        assertNull(first)
        assertNull(second)
    }

    @Test
    fun ingest_afterPublishInterval_publishesAggregatedMetrics() {
        val agg = InkDebugAggregator(publishIntervalMs = 100L)
        val base = InkDebugMetrics()
        val sample = InkDebugSample(0.7f, "stylus", 2, false, false, 0.2f)

        agg.ingest(0L, sample, base)
        val out = agg.ingest(120L, sample, base)

        assertNotNull(out)
        assertEquals(0.7f, out!!.pressure)
        assertEquals("stylus", out.toolType)
        assertEquals(0.2f, out.tiltRadians)
        assertEquals(6L, out.pointCount) // (2+1) * 2 events
    }

    @Test
    fun ingest_accumulatesCancelAndPalmCounters() {
        val agg = InkDebugAggregator(publishIntervalMs = 100L)
        val base = InkDebugMetrics()
        agg.ingest(0L, InkDebugSample(0.1f, "finger", 0, true, false, null), base)
        val out = agg.ingest(120L, InkDebugSample(0.1f, "palm", 0, false, true, null), base)

        assertNotNull(out)
        assertEquals(1L, out!!.cancelEventCount)
        assertEquals(1L, out.palmEventCount)
    }
}

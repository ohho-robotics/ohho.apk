package com.varunvaidhiya.robotcontrol.data.repository

import com.varunvaidhiya.robotcontrol.data.models.HomeTelemetry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RosHomeTelemetryParserTest {

    @Test
    fun `battery percentage scales 0-1 to percent`() {
        val pct = RosHomeTelemetryParser.batteryPercentage(mapOf("percentage" to 0.82))
        assertEquals(82f, pct!!, 0.01f)
    }

    @Test
    fun `battery percentage accepts 0-100`() {
        val pct = RosHomeTelemetryParser.batteryPercentage(mapOf("percentage" to 67.5))
        assertEquals(67.5f, pct!!, 0.01f)
    }

    @Test
    fun `diagnostics merges rpi and arm battery keys`() {
        val msg = mapOf(
            "status" to listOf(
                mapOf(
                    "name" to "omnibot: arm_battery",
                    "values" to listOf(mapOf("key" to "percentage", "value" to "88.0")),
                ),
                mapOf(
                    "name" to "omnibot: rpi",
                    "values" to listOf(
                        mapOf("key" to "temp_c", "value" to "54.2"),
                        mapOf("key" to "ram_gb", "value" to "2.10"),
                        mapOf("key" to "core1", "value" to "40"),
                        mapOf("key" to "core2", "value" to "48"),
                        mapOf("key" to "core3", "value" to "34"),
                        mapOf("key" to "core4", "value" to "42"),
                    ),
                ),
            ),
        )
        val merged = RosHomeTelemetryParser.mergeDiagnostics(HomeTelemetry(), msg)
        assertEquals(88f, merged.batteryArmPct!!, 0.01f)
        assertEquals(54.2f, merged.rpiTempC!!, 0.01f)
        assertEquals(2.10f, merged.rpiRamUsedGb!!, 0.01f)
        assertEquals(listOf(40f, 48f, 34f, 42f), merged.rpiCoreLoads)
        assertEquals(true, merged.hasDiagnosticsFromRos)
    }

    @Test
    fun `battery parser returns null without percentage`() {
        assertNull(RosHomeTelemetryParser.batteryPercentage(emptyMap()))
    }
}

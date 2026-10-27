package com.varunvaidhiya.robotcontrol.data.repository

import com.varunvaidhiya.robotcontrol.data.models.HomeTelemetry

/**
 * Pure parsers for ROSBridge JSON maps (no Android APIs — JVM-testable).
 */
object RosHomeTelemetryParser {

    /** sensor_msgs/BatteryState.percentage is 0–1 in ROS 2; some stacks send 0–100. */
    fun batteryPercentage(msg: Map<String, Any>): Float? {
        val raw = (msg["percentage"] as? Number)?.toFloat() ?: return null
        return if (raw <= 1f) raw * 100f else raw
    }

    @Suppress("UNCHECKED_CAST")
    fun mergeDiagnostics(existing: HomeTelemetry, msg: Map<String, Any>): HomeTelemetry {
        val statuses = msg["status"] as? List<Map<String, Any>> ?: return existing
        var batteryArm = existing.batteryArmPct
        var cores = existing.rpiCoreLoads?.toMutableList()
        var temp = existing.rpiTempC
        var ramGb = existing.rpiRamUsedGb

        for (status in statuses) {
            val name = (status["name"] as? String)?.lowercase() ?: ""
            val values = status["values"] as? List<Map<String, Any>> ?: emptyList()
            val kv = values.associate { (it["key"] as? String)?.lowercase() to it["value"] as? String }

            if (batteryArm == null && name.contains("arm") && name.contains("batt")) {
                batteryArm = kv["percentage"]?.toFloatOrNull()
                    ?: kv["charge"]?.toFloatOrNull()
            }

            if (name.contains("rpi") || name.contains("omnibot: rpi")) {
                kv["temp_c"]?.toFloatOrNull()?.let { temp = it }
                kv["ram_gb"]?.toFloatOrNull()?.let { ramGb = it }
                val coreLoads = (1..4).mapNotNull { i -> kv["core$i"]?.toFloatOrNull() }
                if (coreLoads.size == 4) cores = coreLoads.toMutableList()
            }
        }

        return existing.copy(
            batteryArmPct = batteryArm,
            rpiCoreLoads = cores,
            rpiTempC = temp,
            rpiRamUsedGb = ramGb,
            hasDiagnosticsFromRos = true,
        )
    }

    fun withBattery(existing: HomeTelemetry, mainPct: Float): HomeTelemetry =
        existing.copy(batteryMainPct = mainPct, hasBatteryFromRos = true)
}

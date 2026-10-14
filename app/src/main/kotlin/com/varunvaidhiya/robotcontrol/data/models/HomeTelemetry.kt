package com.varunvaidhiya.robotcontrol.data.models

/**
 * Home-screen metrics from ROS (/battery_state, /diagnostics) when available.
 * Null fields mean the UI should keep demo placeholders for that card.
 */
data class HomeTelemetry(
    val batteryMainPct: Float? = null,
    val batteryArmPct: Float? = null,
    val rpiCoreLoads: List<Float>? = null,
    val rpiTempC: Float? = null,
    val rpiRamUsedGb: Float? = null,
    val hasBatteryFromRos: Boolean = false,
    val hasDiagnosticsFromRos: Boolean = false,
)

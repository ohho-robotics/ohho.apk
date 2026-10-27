package com.varunvaidhiya.robotcontrol.data.models

/**
 * Odometry from /odom (nav_msgs/Odometry).
 * [x] and [y] are meters in [frameId] (the header frame), not necessarily the map frame.
 */
data class OdomData(
    val x: Float = 0f,
    val y: Float = 0f,
    val yaw: Float = 0f,        // radians, counterclockwise about +Z
    val vx: Float = 0f,         // m/s, robot frame
    val vy: Float = 0f,
    val vz: Float = 0f,         // rad/s
    val frameId: String = "",
    val timestamp: Long = System.currentTimeMillis()
)

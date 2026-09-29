package com.varunvaidhiya.robotcontrol.data.repository

import com.varunvaidhiya.robotcontrol.data.models.WheelSpeed
import org.junit.Assert.assertEquals
import org.junit.Test

class RobotRepositoryTest {

    @Test
    fun `test wheel speed parsing`() {
        val message = mapOf(
            "front_left" to 1.0,
            "front_right" to 2.0,
            "rear_left" to 3.0,
            "rear_right" to 4.0
        )

        val speeds = WheelSpeed(
            frontLeft = (message["front_left"] as Double).toFloat(),
            frontRight = (message["front_right"] as Double).toFloat(),
            backLeft = (message["rear_left"] as Double).toFloat(),
            backRight = (message["rear_right"] as Double).toFloat()
        )

        assertEquals(1.0f, speeds.frontLeft, 0.001f)
        assertEquals(2.0f, speeds.frontRight, 0.001f)
        assertEquals(3.0f, speeds.backLeft, 0.001f)
        assertEquals(4.0f, speeds.backRight, 0.001f)
    }

    @Test
    fun `rosbridge url uses the saved host and port`() {
        assertEquals(
            "ws://10.0.2.2:9090",
            RobotRepository.rosBridgeUrl("10.0.2.2", 9090)
        )
    }
}

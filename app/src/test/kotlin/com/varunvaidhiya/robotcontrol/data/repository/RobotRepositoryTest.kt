package com.varunvaidhiya.robotcontrol.data.repository

import com.varunvaidhiya.robotcontrol.data.models.WheelSpeed
import com.varunvaidhiya.robotcontrol.network.ROSBridgeManager
import com.varunvaidhiya.robotcontrol.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `arm joint commands publish on the teleop mux input`() {
        assertEquals("/arm/joint_commands/teleop", Constants.TOPIC_ARM_JOINT_COMMANDS)
    }

    @Test
    fun `connect sets connecting state and endpoint on robot status`() {
        val repo = RobotRepository()
        assertEquals(ROSBridgeManager.ConnectionState.DISCONNECTED, repo.connectionState.value)

        repo.connect("192.168.1.50", 9091)

        assertEquals(ROSBridgeManager.ConnectionState.CONNECTING, repo.connectionState.value)
        assertEquals("192.168.1.50", repo.robotStatus.value.ipAddress)
        assertEquals(9091, repo.robotStatus.value.port)
    }

    @Test
    fun `ros connected listener marks repository connected`() {
        val repo = RobotRepository()
        repo.deliverRosConnectedForTest()

        assertEquals(ROSBridgeManager.ConnectionState.CONNECTED, repo.connectionState.value)
        assertTrue(repo.robotStatus.value.isConnected)
    }

    @Test
    fun `ros disconnected listener clears connection and home telemetry`() {
        val repo = RobotRepository()
        repo.deliverRosConnectedForTest()
        repo.deliverRosMessageForTest(
            Constants.TOPIC_BATTERY_STATE,
            mapOf("percentage" to 0.75),
        )
        assertTrue(repo.homeTelemetry.value.hasBatteryFromRos)

        repo.deliverRosDisconnectedForTest()

        assertEquals(ROSBridgeManager.ConnectionState.DISCONNECTED, repo.connectionState.value)
        assertFalse(repo.robotStatus.value.isConnected)
        assertFalse(repo.homeTelemetry.value.hasBatteryFromRos)
    }

    @Test
    fun `map message updates occupancy grid`() {
        val repo = RobotRepository()
        repo.deliverRosMessageForTest(
            Constants.TOPIC_MAP,
            mapOf(
                "info" to mapOf(
                    "width" to 2,
                    "height" to 1,
                    "resolution" to 0.05,
                    "origin" to mapOf("position" to mapOf("x" to 0.0, "y" to 0.0)),
                ),
                "data" to listOf(0, 100),
            ),
        )
        val map = repo.mapData.value
        assertEquals(2, map.width)
        assertEquals(1, map.height)
        assertEquals("", map.frameId)
        assertTrue(map.cells.contentEquals(intArrayOf(0, 100)))
    }

    @Test
    fun `odom message keeps the header frame`() {
        val repo = RobotRepository()
        repo.deliverRosMessageForTest(Constants.TOPIC_ODOM, odomMessage(x = 1.25, frameId = "odom"))
        assertEquals("odom", repo.odomData.value.frameId)
        assertEquals(1.25f, repo.odomData.value.x, 0.001f)
        assertEquals(0f, repo.odomData.value.yaw, 0.001f)
    }

    @Test
    fun `map message keeps the header frame`() {
        val repo = RobotRepository()
        repo.deliverRosMessageForTest(Constants.TOPIC_MAP, mapMessage(frameId = "map"))
        assertEquals("map", repo.mapData.value.frameId)
        assertEquals(2, repo.mapData.value.width)
    }

    @Test
    fun `connect and disconnect clear the previous map and odometry`() {
        val repo = RobotRepository()
        repo.deliverRosMessageForTest(Constants.TOPIC_MAP, mapMessage(frameId = "map"))
        repo.deliverRosMessageForTest(Constants.TOPIC_ODOM, odomMessage(x = 1.5, frameId = "odom"))
        assertTrue(repo.mapData.value.cells.isNotEmpty())
        assertEquals(1.5f, repo.odomData.value.x, 0.001f)

        repo.connect("192.0.2.1", 9)
        assertTrue(repo.mapData.value.cells.isEmpty())
        assertEquals(0f, repo.odomData.value.x, 0.001f)
        assertEquals("", repo.odomData.value.frameId)
        assertEquals("", repo.mapData.value.frameId)

        repo.deliverRosMessageForTest(Constants.TOPIC_MAP, mapMessage(frameId = "map"))
        assertEquals("map", repo.mapData.value.frameId)
        assertTrue(repo.mapData.value.cells.isNotEmpty())

        repo.disconnect()
        assertTrue(repo.mapData.value.cells.isEmpty())
        assertEquals(0f, repo.odomData.value.x, 0.001f)
    }

    @Test
    fun `battery_state message updates home telemetry`() {
        val repo = RobotRepository()
        repo.deliverRosMessageForTest(
            Constants.TOPIC_BATTERY_STATE,
            mapOf("percentage" to 0.63),
        )
        assertEquals(63f, repo.homeTelemetry.value.batteryMainPct!!, 0.01f)
        assertTrue(repo.homeTelemetry.value.hasBatteryFromRos)
    }

    private fun mapMessage(frameId: String) = mapOf(
        "header" to mapOf("frame_id" to frameId),
        "info" to mapOf(
            "width" to 2,
            "height" to 1,
            "resolution" to 0.05,
            "origin" to mapOf("position" to mapOf("x" to 0.0, "y" to 0.0)),
        ),
        "data" to listOf(0, 100),
    )

    private fun odomMessage(x: Double, frameId: String) = mapOf(
        "header" to mapOf("frame_id" to frameId),
        "pose" to mapOf(
            "pose" to mapOf(
                "position" to mapOf("x" to x, "y" to 0.0, "z" to 0.0),
                "orientation" to mapOf("x" to 0.0, "y" to 0.0, "z" to 0.0, "w" to 1.0),
            ),
        ),
        "twist" to mapOf(
            "twist" to mapOf(
                "linear" to mapOf("x" to 0.0, "y" to 0.0, "z" to 0.0),
                "angular" to mapOf("x" to 0.0, "y" to 0.0, "z" to 0.0),
            ),
        ),
    )
}

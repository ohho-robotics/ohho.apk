package com.varunvaidhiya.robotcontrol.data.repository

import com.varunvaidhiya.robotcontrol.data.models.VelocityCommand
import com.varunvaidhiya.robotcontrol.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Selecting a drone profile or URI must not publish `/cmd_vel/teleop`.
 * A ground robot still publishes the same Twist as before.
 */
class RobotRepositoryDroneTeleopTest {

    @Test
    fun `selecting a drone URI publishes nothing on cmd_vel teleop`() {
        val uris = listOf(
            "px4+dds://127.0.0.1:8888?ns=px4",
            "mavlink+udp://127.0.0.1:14550",
            "mavlink+serial://COM3?baud=921600",
            "crazyflie://radio://0/80/2M/E7E7E7E7E7",
            "crazyflie+ros2://cf21",
        )
        uris.forEach { uri ->
            val published = mutableListOf<Pair<String, Map<String, Any>>>()
            val repository = recordingRepository(published)
            repository.selectRobot(profileId = "custom-airframe", transportUri = uri)
            repository.sendVelocity(VelocityCommand(linearX = 1f, linearY = 0.2f, angular = 0.4f))
            repository.sendVelocity(VelocityCommand())
            assertTrue("published for $uri: $published", published.isEmpty())
            assertTrue(repository.droneTeleopRefused.value)
        }
    }

    @Test
    fun `selecting an aerial profile publishes nothing on cmd_vel teleop`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published)
        repository.selectRobot(
            profileId = "px4-x500",
            transportUri = "ws://10.0.0.4:9090",
            capabilities = setOf("aerial", "base.drive"),
        )
        repository.sendVelocity(VelocityCommand(linearX = 0.5f, angular = 0.2f))
        assertTrue(published.isEmpty())
        assertTrue(repository.droneTeleopRefused.value)
    }

    @Test
    fun `selecting a drone profile id publishes nothing on cmd_vel teleop`() {
        listOf("px4-sitl", "crazyflie-cf21", "crazyflie-sim").forEach { id ->
            val published = mutableListOf<Pair<String, Map<String, Any>>>()
            val repository = recordingRepository(published)
            repository.selectRobot(profileId = id)
            repository.sendVelocity(VelocityCommand(linearX = 0.3f))
            assertTrue("published for $id", published.isEmpty())
        }
    }

    @Test
    fun `ground robot teleop is unchanged`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published)
        repository.selectRobot(
            profileId = "omnibot",
            transportUri = "ros2://omnibot",
            capabilities = setOf("base.drive", "base.holonomic_drive"),
        )
        val command = VelocityCommand(linearX = 0.4f, linearY = -0.2f, angular = 0.5f)
        repository.sendVelocity(command)
        assertEquals(1, published.size)
        assertEquals("/cmd_vel/teleop", published[0].first)
        assertEquals(Constants.TOPIC_CMD_VEL, published[0].first)
        assertEquals(command.toROSTwist(), published[0].second)
        assertTrue(!repository.droneTeleopRefused.value)
    }

    @Test
    fun `no selection still publishes ground teleop`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published)
        val command = VelocityCommand(linearX = 0.1f, angular = -0.2f)
        repository.sendVelocity(command)
        assertEquals(listOf("/cmd_vel/teleop" to command.toROSTwist()), published)
    }

    @Test
    fun `ground teleop works again after a drone selection is cleared`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published)
        repository.selectRobot(transportUri = "px4+dds://127.0.0.1:8888")
        repository.sendVelocity(VelocityCommand(linearX = 1f))
        assertTrue(published.isEmpty())
        repository.selectRobot(
            profileId = "omnibot",
            transportUri = "ws://192.168.1.50:9090",
            capabilities = setOf("base.drive"),
        )
        val command = VelocityCommand(linearX = 0.2f, angular = 0.1f)
        repository.sendVelocity(command)
        assertEquals(listOf("/cmd_vel/teleop" to command.toROSTwist()), published)
        assertTrue(!repository.droneTeleopRefused.value)
    }

    @Test
    fun `connecting with a drone URI publishes nothing and does not open teleop`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published)
        repository.connect("px4+dds://127.0.0.1:8888", 8888)
        repository.sendVelocity(VelocityCommand(linearX = 1f, angular = 0.3f))
        assertTrue(published.isEmpty())
        assertTrue(repository.droneTeleopRefused.value)
    }

    private fun recordingRepository(
        published: MutableList<Pair<String, Map<String, Any>>>,
    ): RobotRepository {
        val repository = RobotRepository()
        repository.cmdVelPublisher = { topic, message -> published += topic to message }
        return repository
    }
}

package com.varunvaidhiya.robotcontrol.data.repository

import com.varunvaidhiya.robotcontrol.data.models.RobotMode
import com.varunvaidhiya.robotcontrol.data.models.VelocityCommand
import com.varunvaidhiya.robotcontrol.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Selecting a humanoid profile must not publish `/cmd_vel/teleop` or any other
 * teleop topic. Ground, arm, and aerial refusal stay on their existing paths.
 */
class RobotRepositoryHumanoidTeleopTest {

    @Test
    fun `selecting a humanoid profile publishes nothing`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val safety = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published, safety)
        repository.selectRobot(
            profileId = "lab-biped",
            transportUri = "ws://10.0.0.8:9090",
            capabilities = setOf("base.drive", "manipulation"),
            robotClass = "humanoid",
        )
        sendEveryTeleopCommand(repository)
        assertTrue("published $published", published.isEmpty())
        assertTrue(repository.humanoidTeleopRefused.value)
        assertTrue(!repository.droneTeleopRefused.value)

        repository.sendEmergencyStop()
        assertEquals(Constants.TOPIC_EMERGENCY_STOP, safety.single().first)
        assertEquals(mapOf("data" to true), safety.single().second)
        assertTrue(published.isEmpty())
    }

    @Test
    fun `humanoid posture capability publishes nothing on a ground uri`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published)
        repository.selectRobot(
            profileId = "lab-biped",
            transportUri = "ws://10.0.0.8:9090",
            capabilities = setOf("base.drive, humanoid.posture"),
        )
        sendEveryTeleopCommand(repository)
        assertTrue(published.isEmpty())
        assertTrue(repository.humanoidTeleopRefused.value)
        assertTrue(!repository.droneTeleopRefused.value)
    }

    @Test
    fun `humanoid descriptor publishes nothing`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published)
        repository.selectRobot(
            mapOf(
                "id" to "lab-biped",
                "transport_uri" to "ws://10.0.0.20:9090",
                "spec" to mapOf(
                    "robot_class" to "humanoid",
                    "capabilities" to listOf("base.drive", "humanoid.posture"),
                ),
            ),
        )
        repository.sendVelocity(VelocityCommand(linearX = 0.5f, linearY = 0.1f, angular = 0.2f))
        repository.sendVelocity(VelocityCommand())
        assertTrue(published.isEmpty())
        assertTrue(repository.humanoidTeleopRefused.value)
    }

    @Test
    fun `ground profile teleop is unchanged`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published)
        repository.selectRobot(
            profileId = "omnibot",
            transportUri = "ros2://omnibot",
            capabilities = setOf("base.drive", "base.holonomic_drive"),
            robotClass = "mobile_base",
        )
        val command = VelocityCommand(linearX = 0.4f, linearY = -0.2f, angular = 0.5f)
        repository.sendVelocity(command)
        assertEquals(listOf(Constants.TOPIC_CMD_VEL to command.toROSTwist()), published)
        assertTrue(!repository.humanoidTeleopRefused.value)
        assertTrue(!repository.droneTeleopRefused.value)
    }

    @Test
    fun `arm profile teleop is unchanged`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published)
        repository.selectRobot(
            profileId = "floor-arm",
            transportUri = "feetech://arm",
            capabilities = setOf("manipulation"),
            robotClass = "arm",
        )
        val command = VelocityCommand(linearX = 0.2f, angular = 0.1f)
        repository.sendVelocity(command)
        repository.sendArmJointCommand(doubleArrayOf(0.1, 0.0))
        repository.sendMode(RobotMode.MANUAL)
        assertEquals(Constants.TOPIC_CMD_VEL, published[0].first)
        assertEquals(command.toROSTwist(), published[0].second)
        assertEquals(Constants.TOPIC_ARM_JOINT_COMMANDS, published[1].first)
        assertEquals(Constants.TOPIC_ROBOT_MODE, published[2].first)
        assertEquals(Constants.TOPIC_CONTROL_MODE, published[3].first)
        assertEquals(4, published.size)
        assertTrue(!repository.humanoidTeleopRefused.value)
        assertTrue(!repository.droneTeleopRefused.value)
    }

    @Test
    fun `aerial refusal still publishes nothing on cmd_vel and does not mark humanoid`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published)
        repository.selectRobot(
            profileId = "px4-sitl",
            transportUri = "px4+dds://127.0.0.1:8888",
            capabilities = setOf("aerial"),
        )
        repository.sendVelocity(VelocityCommand(linearX = 1f))
        repository.sendControlMode("teleop")
        assertEquals(listOf(Constants.TOPIC_CONTROL_MODE), published.map { it.first })
        assertTrue(repository.droneTeleopRefused.value)
        assertTrue(!repository.humanoidTeleopRefused.value)
    }

    @Test
    fun `ground teleop works again after a humanoid selection is cleared`() {
        val published = mutableListOf<Pair<String, Map<String, Any>>>()
        val repository = recordingRepository(published)
        repository.selectRobot(robotClass = "humanoid", capabilities = setOf("humanoid.posture"))
        repository.sendVelocity(VelocityCommand(linearX = 1f))
        assertTrue(published.isEmpty())
        repository.selectRobot(
            profileId = "omnibot",
            transportUri = "ws://192.168.1.50:9090",
            capabilities = setOf("base.drive"),
            robotClass = "mobile_manipulator",
        )
        val command = VelocityCommand(linearX = 0.2f, angular = 0.1f)
        repository.sendVelocity(command)
        assertEquals(listOf(Constants.TOPIC_CMD_VEL to command.toROSTwist()), published)
        assertTrue(!repository.humanoidTeleopRefused.value)
        assertTrue(!repository.droneTeleopRefused.value)
    }

    private fun sendEveryTeleopCommand(repository: RobotRepository) {
        repository.sendVelocity(VelocityCommand(linearX = 0.4f, linearY = 0.2f, angular = 0.3f))
        repository.sendVelocity(VelocityCommand())
        repository.sendMode(RobotMode.TELEOP)
        repository.sendControlMode("teleop")
        repository.sendArmJointCommand(doubleArrayOf(0.2, -0.1))
        repository.setArmEnabled(true)
        repository.sendMissionCommand("walk forward")
        repository.sendAICommand("walk forward")
    }

    private fun recordingRepository(
        published: MutableList<Pair<String, Map<String, Any>>>,
        safety: MutableList<Pair<String, Map<String, Any>>> = mutableListOf(),
    ): RobotRepository {
        val repository = RobotRepository()
        val sink = { topic: String, message: Map<String, Any> ->
            published += topic to message
        }
        repository.cmdVelPublisher = sink
        repository.commandPublisher = sink
        repository.safetyPublisher = { topic, message -> safety += topic to message }
        return repository
    }
}

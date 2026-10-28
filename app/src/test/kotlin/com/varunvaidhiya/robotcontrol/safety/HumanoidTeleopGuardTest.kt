package com.varunvaidhiya.robotcontrol.safety

import com.varunvaidhiya.robotcontrol.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * OHH-339: a humanoid profile has no deadman and no mode controls.
 * The aerial guard is the same gate. Selecting a humanoid publishes nothing.
 */
class HumanoidTeleopGuardTest {

    private val guard = DroneTeleopGuard()
    private val twist = mapOf(
        "linear" to mapOf("x" to 0.4, "y" to -0.1, "z" to 0.0),
        "angular" to mapOf("x" to 0.0, "y" to 0.0, "z" to 0.25),
    )

    @Test
    fun `robot class humanoid publishes nothing on cmd_vel or any other teleop topic`() {
        val target = RobotTarget(
            profileId = "lab-biped",
            transportUri = "ws://10.0.0.8:9090",
            capabilities = setOf("base.drive", "manipulation"),
            robotClass = "humanoid",
        )
        assertRefusesHumanoid(target)
        listOf(
            Constants.TOPIC_CMD_VEL,
            Constants.TOPIC_CONTROL_MODE,
            Constants.TOPIC_ROBOT_MODE,
            Constants.TOPIC_ARM_JOINT_COMMANDS,
            Constants.TOPIC_ARM_ENABLE,
            Constants.TOPIC_MISSION_COMMAND,
            "/humanoid/cmd",
        ).forEach { topic ->
            val published = mutableListOf<String>()
            assertFalse(
                guard.publishTeleopIfAllowed(target, topic, twist) { name, _ -> published += name },
            )
            assertTrue("published $topic", published.isEmpty())
        }
    }

    @Test
    fun `humanoid posture capability publishes nothing without a class`() {
        val samples = listOf(
            setOf("humanoid.posture"),
            setOf("base.drive", "HUMANOID.POSTURE"),
            setOf("base.drive,humanoid.posture"),
            setOf("  humanoid.posture  "),
        )
        samples.forEach { capabilities ->
            val published = mutableListOf<String>()
            val target = RobotTarget(
                profileId = "lab-biped",
                transportUri = "ros2://lab-biped",
                capabilities = capabilities,
            )
            assertFalse(
                guard.publishIfAllowed(target, twist) { topic, _ -> published += topic },
            )
            assertTrue("$capabilities published $published", published.isEmpty())
            val decision = guard.decide(target)
            assertEquals(
                DroneTeleopGuard.HUMANOID_REFUSAL_STRING_RES_NAME,
                (decision as TeleopDecision.Refuse).messageResName,
            )
        }
    }

    @Test
    fun `descriptor robot class or nested posture capability publishes nothing`() {
        val byClass = RobotTarget.fromDescriptor(
            mapOf(
                "id" to "lab-biped",
                "robot_class" to " Humanoid ",
                "transport_uri" to "ws://10.0.0.8:9090",
                "capabilities" to listOf("base.drive"),
            ),
        )
        val bySpec = RobotTarget.fromDescriptor(
            mapOf(
                "id" to "lab-biped",
                "transport" to "sim://lab-biped",
                "spec" to mapOf(
                    "robot_class" to "humanoid",
                    "capabilities" to listOf(
                        mapOf("name" to "humanoid.posture", "params" to listOf("walk")),
                        "base.drive",
                    ),
                ),
            ),
        )
        val byCapabilityMap = RobotTarget.fromDescriptor(
            mapOf(
                "robotClass" to "mobile_base",
                "capabilities" to mapOf("humanoid.posture" to mapOf("modes" to listOf("walk"))),
            ),
        )
        listOf(byClass, bySpec, byCapabilityMap).forEach { target ->
            assertRefusesHumanoid(target)
        }
    }

    @Test
    fun `zero twist on a humanoid is still not published`() {
        val published = mutableListOf<String>()
        assertFalse(
            guard.publishIfAllowed(
                RobotTarget(robotClass = "HUMANOID"),
                mapOf("linear" to mapOf("x" to 0.0, "y" to 0.0, "z" to 0.0)),
            ) { topic, _ -> published += topic },
        )
        assertTrue(published.isEmpty())
    }

    @Test
    fun `ground arm and quadruped profiles still publish`() {
        val targets = listOf(
            RobotTarget(
                profileId = "omnibot",
                transportUri = "ros2://omnibot",
                capabilities = setOf("base.drive", "manipulation"),
                robotClass = "mobile_manipulator",
            ),
            RobotTarget(
                profileId = "floor-arm",
                transportUri = "feetech://arm",
                capabilities = setOf("manipulation"),
                robotClass = "arm",
            ),
            RobotTarget(
                profileId = "lab-quad",
                transportUri = "dds://eth0",
                capabilities = setOf("legged.pose", "base.drive"),
                robotClass = "quadruped",
            ),
            RobotTarget(profileId = "lab-biped", robotClass = "humanoids"),
            RobotTarget(capabilities = setOf("humanoid.postural", "base.drive")),
            RobotTarget(capabilities = setOf("posture")),
            RobotTarget(profileId = "lab-biped"),
            RobotTarget.fromDescriptor(
                mapOf(
                    "id" to "lab-biped",
                    "robot_class" to "mobile_base",
                    "capabilities" to listOf("base.drive"),
                ),
            ),
        )
        targets.forEach { target ->
            val published = mutableListOf<Pair<String, Map<String, Any>>>()
            assertTrue(
                "refused $target",
                guard.publishIfAllowed(target, twist) { topic, message ->
                    published += topic to message
                },
            )
            assertEquals(TeleopDecision.Allow, guard.decide(target))
            assertEquals(listOf("/cmd_vel/teleop" to twist), published)
            val mode = mutableListOf<String>()
            assertTrue(
                guard.publishTeleopIfAllowed(target, Constants.TOPIC_CONTROL_MODE, mapOf("data" to "teleop")) { topic, _ ->
                    mode += topic
                },
            )
            assertEquals(listOf(Constants.TOPIC_CONTROL_MODE), mode)
        }
    }

    @Test
    fun `aerial refusal is unchanged when the profile is not humanoid`() {
        val target = RobotTarget(
            profileId = "px4-sitl",
            transportUri = "px4+dds://127.0.0.1:8888",
            capabilities = setOf("aerial"),
            robotClass = "aerial",
        )
        val decision = guard.decide(target)
        assertEquals(
            DroneTeleopGuard.REFUSAL_STRING_RES_NAME,
            (decision as TeleopDecision.Refuse).messageResName,
        )
        val velocity = mutableListOf<String>()
        assertFalse(guard.publishIfAllowed(target, twist) { topic, _ -> velocity += topic })
        assertTrue(velocity.isEmpty())
        val mode = mutableListOf<String>()
        assertTrue(
            guard.publishTeleopIfAllowed(target, Constants.TOPIC_CONTROL_MODE, mapOf("data" to "teleop")) { topic, _ ->
                mode += topic
            },
        )
        assertEquals(listOf(Constants.TOPIC_CONTROL_MODE), mode)
        assertFalse(guard.refuses(RobotTarget(profileId = "floor-arm", robotClass = "arm")))
    }

    @Test
    fun `refusal string tells the operator humanoid teleop is not supported yet`() {
        val xml = stringsXml()
        val name = DroneTeleopGuard.HUMANOID_REFUSAL_STRING_RES_NAME
        assertTrue(xml.contains("<string name=\"$name\">"))
        val body = xml.substringAfter("<string name=\"$name\">").substringBefore("</string>")
        assertTrue(body.contains("Not supported yet"))
        assertTrue(body.contains("deadman"))
        assertTrue(body.contains("mode"))
        assertFalse(body.contains("climb"))
    }

    private fun assertRefusesHumanoid(target: RobotTarget) {
        val published = mutableListOf<String>()
        val decision = guard.decide(target)
        assertEquals(
            DroneTeleopGuard.HUMANOID_REFUSAL_STRING_RES_NAME,
            (decision as TeleopDecision.Refuse).messageResName,
        )
        assertFalse(guard.publishIfAllowed(target, twist) { topic, _ -> published += topic })
        assertTrue(published.isEmpty())
    }

    private fun stringsXml(): String {
        val candidates = listOf(
            File("src/main/res/values/strings.xml"),
            File("app/src/main/res/values/strings.xml"),
        )
        val file = candidates.firstOrNull { it.isFile }
            ?: error("strings.xml not found from ${File("").absolutePath}")
        return file.readText()
    }
}

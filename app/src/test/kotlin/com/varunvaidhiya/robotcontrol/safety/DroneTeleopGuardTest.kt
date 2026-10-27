package com.varunvaidhiya.robotcontrol.safety

import com.varunvaidhiya.robotcontrol.utils.Constants
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DroneTeleopGuardTest {

    private val guard = DroneTeleopGuard()
    private val twist = mapOf(
        "linear" to mapOf("x" to 0.4, "y" to -0.1, "z" to 0.0),
        "angular" to mapOf("x" to 0.0, "y" to 0.0, "z" to 0.25),
    )

    @Test
    fun `drone transport URIs publish nothing on cmd_vel teleop`() {
        val uris = listOf(
            "px4+dds://127.0.0.1:8888?ns=px4",
            "mavlink+udp://127.0.0.1:14550",
            "mavlink+serial:///dev/ttyACM0?baud=921600",
            "mavlink+serial://COM3?baud=921600",
            "crazyflie://radio://0/80/2M/E7E7E7E7E7",
            "crazyflie+ros2://cf21",
            "  PX4+DDS://10.0.0.8:8888  ",
            "Crazyflie+ROS2://cf21",
        )
        uris.forEach { uri ->
            val published = mutableListOf<Pair<String, Map<String, Any>>>()
            val decision = guard.decide(RobotTarget(transportUri = uri))
            val allowed = guard.publishIfAllowed(RobotTarget(transportUri = uri), twist) { topic, message ->
                published += topic to message
            }
            assertTrue(uri, decision is TeleopDecision.Refuse)
            assertEquals(
                DroneTeleopGuard.REFUSAL_STRING_RES_NAME,
                (decision as TeleopDecision.Refuse).messageResName,
            )
            assertFalse(uri, allowed)
            assertTrue("published for $uri", published.isEmpty())
        }
    }

    @Test
    fun `aerial capability flag publishes nothing even on a ground URI`() {
        val target = RobotTarget(
            profileId = "lab-robot",
            transportUri = "ws://10.0.0.8:9090",
            capabilities = setOf("base.drive", "AERIAL"),
        )
        val published = mutableListOf<String>()
        assertFalse(guard.publishIfAllowed(target, twist) { topic, _ -> published += topic })
        assertTrue(published.isEmpty())
        assertTrue(guard.refuses(target.copy(capabilities = setOf("base.drive,aerial"))))
        assertTrue(guard.refuses(target.copy(capabilities = setOf(" aerial "))))
    }

    @Test
    fun `known aerial profile ids publish nothing without a URI`() {
        listOf("px4-sitl", "px4-x500", "crazyflie-cf21", "crazyflie-sim", "PX4-SITL").forEach { id ->
            val published = mutableListOf<String>()
            val target = RobotTarget(profileId = id)
            assertFalse(guard.publishIfAllowed(target, emptyMap()) { topic, _ -> published += topic })
            assertTrue(published.isEmpty())
        }
    }

    @Test
    fun `sim URI for an aerial profile publishes nothing`() {
        listOf("sim://px4-sitl", "sim://crazyflie-sim", "sim://px4-x500:9090").forEach { uri ->
            val published = mutableListOf<String>()
            assertFalse(
                guard.publishIfAllowed(RobotTarget(transportUri = uri), twist) { topic, _ ->
                    published += topic
                },
            )
            assertTrue(published.isEmpty())
        }
    }

    @Test
    fun `ground robot teleop is unchanged`() {
        val targets = listOf(
            RobotTarget(),
            RobotTarget(profileId = "omnibot", transportUri = "ros2://omnibot", capabilities = setOf("base.drive")),
            RobotTarget(profileId = "go2", transportUri = "dds://eth0", capabilities = setOf("legged.pose")),
            RobotTarget(transportUri = "ws://192.168.1.100:9090"),
            RobotTarget(transportUri = "192.168.1.100"),
            RobotTarget(transportUri = "sim://omnibot"),
            RobotTarget(transportUri = "sim://"),
            RobotTarget(transportUri = "serial:///dev/ttyUSB0"),
            RobotTarget(transportUri = "feetech://arm"),
            RobotTarget(profileId = "px4-sitl-spare"),
            RobotTarget(capabilities = setOf("base.drive", "manipulation", "notaerial")),
            RobotTarget(transportUri = "ros2://bot?note=px4+dds://ignored"),
            RobotTarget(transportUri = "px4+dds-extra://host"),
            RobotTarget(transportUri = "mavlink://127.0.0.1:14550"),
            RobotTarget(transportUri = "crazyflie2://cf"),
        )
        targets.forEach { target ->
            val published = mutableListOf<Pair<String, Map<String, Any>>>()
            val allowed = guard.publishIfAllowed(target, twist) { topic, message ->
                published += topic to message
            }
            assertTrue("refused $target", allowed)
            assertEquals(TeleopDecision.Allow, guard.decide(target))
            assertEquals(listOf("/cmd_vel/teleop" to twist), published)
        }
        assertEquals("/cmd_vel/teleop", Constants.TOPIC_CMD_VEL)
    }

    @Test
    fun `zero twist on a drone is still not published`() {
        val published = mutableListOf<String>()
        assertFalse(
            guard.publishIfAllowed(
                RobotTarget(transportUri = "px4+dds://127.0.0.1:8888"),
                mapOf("linear" to mapOf("x" to 0.0, "y" to 0.0, "z" to 0.0)),
            ) { topic, _ -> published += topic },
        )
        assertTrue(published.isEmpty())
    }

    @Test
    fun `refusal string resource tells the operator why teleop is off`() {
        val xml = stringsXml()
        val name = DroneTeleopGuard.REFUSAL_STRING_RES_NAME
        assertTrue(xml.contains("<string name=\"$name\">"))
        val body = xml.substringAfter("<string name=\"$name\">").substringBefore("</string>")
        assertTrue(body.contains("/cmd_vel/teleop"))
        assertTrue(body.contains("climb"))
        assertTrue(body.contains("altitude-hold"))
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

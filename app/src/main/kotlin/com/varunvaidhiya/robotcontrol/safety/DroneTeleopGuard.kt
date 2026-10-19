package com.varunvaidhiya.robotcontrol.safety

import com.varunvaidhiya.robotcontrol.utils.Constants

/**
 * Decides whether planar teleop may be published on [Constants.TOPIC_CMD_VEL].
 *
 * OHH-297: the controls screen has one ground stick and no climb axis. An aerial
 * profile or drone transport must not receive `/cmd_vel/teleop`. A zero Twist is
 * not a substitute: in offboard that means hover, not "do nothing". Flight teleop
 * (a climb stick plus deadman, or an explicit altitude-hold mode against ohho
 * sim-serve) is a later issue. This class does not build a flight setpoint.
 *
 * OHH-339: that same stick has no deadman and no humanoid mode controls. A
 * descriptor whose `robot_class` is humanoid, or whose capabilities include
 * `humanoid.posture`, is not supported yet. Nothing is published on
 * `/cmd_vel/teleop` or on any other teleop topic. The check is the class and
 * the capability, not a robot id.
 */
class DroneTeleopGuard {

    fun decide(target: RobotTarget): TeleopDecision {
        if (isHumanoid(target)) {
            return TeleopDecision.Refuse(HUMANOID_REFUSAL_STRING_RES_NAME)
        }
        if (isAerial(target)) {
            return TeleopDecision.Refuse(REFUSAL_STRING_RES_NAME)
        }
        return TeleopDecision.Allow
    }

    fun refuses(target: RobotTarget): Boolean = decide(target) is TeleopDecision.Refuse

    /**
     * Publishes [twist] on `/cmd_vel/teleop` only when [decide] allows [target].
     * The published map is [twist] unchanged.
     *
     * @return true when a message was handed to [publish]
     */
    fun publishIfAllowed(
        target: RobotTarget,
        twist: Map<String, Any>,
        publish: (topic: String, message: Map<String, Any>) -> Unit,
    ): Boolean {
        if (refuses(target)) return false
        publish(Constants.TOPIC_CMD_VEL, twist)
        return true
    }

    /**
     * Publishes [message] on [topic] unless [target] is a humanoid profile.
     * Until a deadman and mode controls exist, a humanoid gets no teleop topic
     * at all, including a topic other than `/cmd_vel/teleop`.
     * Aerial targets are unchanged: their climb refusal stays in [publishIfAllowed].
     *
     * @return true when a message was handed to [publish]
     */
    fun publishTeleopIfAllowed(
        target: RobotTarget,
        topic: String,
        message: Map<String, Any>,
        publish: (topic: String, message: Map<String, Any>) -> Unit,
    ): Boolean {
        if (isHumanoid(target)) return false
        publish(topic, message)
        return true
    }

    /** True for the drone transport schemes this app must not drive. */
    fun isDroneTransportUri(uri: String?): Boolean {
        val scheme = schemeOf(uri) ?: return false
        return scheme in DRONE_URI_SCHEMES
    }

    private fun isHumanoid(target: RobotTarget): Boolean =
        isHumanoidClass(target.robotClass) ||
            hasCapability(target.capabilities, HUMANOID_POSTURE_CAPABILITY)

    private fun isHumanoidClass(robotClass: String?): Boolean {
        val value = robotClass?.trim() ?: return false
        if (value.isEmpty()) return false
        return value.equals(HUMANOID_CLASS, ignoreCase = true)
    }

    private fun hasCapability(capabilities: Set<String>, capability: String): Boolean =
        capabilities.asSequence()
            .flatMap { token -> token.split(',') }
            .map { it.trim() }
            .any { it.equals(capability, ignoreCase = true) }

    private fun isAerial(target: RobotTarget): Boolean =
        isDroneTransportUri(target.transportUri) ||
            isSimAliasForAerialProfile(target.transportUri) ||
            hasAerialCapability(target.capabilities) ||
            isAerialProfileId(target.profileId)

    private fun hasAerialCapability(capabilities: Set<String>): Boolean =
        capabilities.asSequence()
            .flatMap { token -> token.split(',') }
            .map { it.trim() }
            .any { it.equals(AERIAL_CAPABILITY, ignoreCase = true) }

    private fun isAerialProfileId(profileId: String?): Boolean {
        val id = profileId?.trim()?.lowercase() ?: return false
        if (id.isEmpty()) return false
        return id in AERIAL_PROFILE_IDS
    }

    /**
     * `sim://` stays a ground transport unless the host is a known aerial profile
     * (`sim://px4-sitl`, `sim://crazyflie-sim`).
     */
    private fun isSimAliasForAerialProfile(uri: String?): Boolean {
        val trimmed = uri?.trim().orEmpty()
        if (schemeOf(trimmed) != "sim") return false
        val rest = trimmed.substringAfter("://", "")
        val host = rest.substringBefore('/').substringBefore('?').substringBefore(':').trim()
        if (host.isEmpty()) return false
        return host.lowercase() in AERIAL_PROFILE_IDS
    }

    private fun schemeOf(uri: String?): String? {
        val trimmed = uri?.trim().orEmpty()
        val separator = trimmed.indexOf("://")
        if (separator <= 0) return null
        val scheme = trimmed.substring(0, separator).trim().lowercase()
        if (scheme.isEmpty()) return null
        return scheme
    }

    companion object {
        const val AERIAL_CAPABILITY = "aerial"

        /** `res/values/strings.xml` name shown when aerial teleop is refused. */
        const val REFUSAL_STRING_RES_NAME = "drone_teleop_refused"

        /** Descriptor `robot_class` that this app must not teleop yet. */
        const val HUMANOID_CLASS = "humanoid"

        /** Capability that means the profile needs humanoid mode controls. */
        const val HUMANOID_POSTURE_CAPABILITY = "humanoid.posture"

        /** `res/values/strings.xml` name shown when a humanoid profile is selected. */
        const val HUMANOID_REFUSAL_STRING_RES_NAME = "humanoid_teleop_refused"

        /** Registry ids from the drone plan that are aerial profiles. */
        val AERIAL_PROFILE_IDS: Set<String> = setOf(
            "px4-sitl",
            "px4-x500",
            "crazyflie-cf21",
            "crazyflie-sim",
        )

        val DRONE_URI_SCHEMES: Set<String> = setOf(
            "px4+dds",
            "mavlink+udp",
            "mavlink+serial",
            "crazyflie",
            "crazyflie+ros2",
        )
    }
}

/**
 * What the operator selected. Blank fields are ignored.
 * A target is aerial when any one of the URI, the `aerial` capability, or a
 * known aerial profile id says so.
 * A target is humanoid when [robotClass] is `humanoid` or [capabilities]
 * include `humanoid.posture`.
 */
data class RobotTarget(
    val profileId: String? = null,
    val transportUri: String? = null,
    val capabilities: Set<String> = emptySet(),
    val robotClass: String? = null,
) {
    companion object {
        /**
         * Reads `robot_class` and capabilities from a robot descriptor or profile
         * map. Vendor ids are ignored. A missing class is not humanoid.
         */
        fun fromDescriptor(descriptor: Map<String, *>): RobotTarget {
            val nested = asMap(descriptor["spec"]) ?: asMap(descriptor["robot"])
            val spec = if (nested.isNullOrEmpty()) descriptor else nested
            val capabilities = linkedSetOf<String>()
            collectCapabilities(spec["capabilities"], capabilities)
            if (spec !== descriptor) {
                collectCapabilities(descriptor["capabilities"], capabilities)
            }
            return RobotTarget(
                profileId = firstText(descriptor, ID_KEYS) ?: firstText(spec, ID_KEYS),
                transportUri = firstText(descriptor, URI_KEYS) ?: firstText(spec, URI_KEYS),
                capabilities = capabilities,
                robotClass = firstText(spec, CLASS_KEYS) ?: firstText(descriptor, CLASS_KEYS),
            )
        }

        private val ID_KEYS = listOf("id", "profile_id", "profileId")
        private val CLASS_KEYS = listOf("robot_class", "robotClass")
        private val URI_KEYS = listOf("transport_uri", "transportUri", "transport", "uri")

        private fun asMap(value: Any?): Map<String, *>? {
            val raw = value as? Map<*, *> ?: return null
            val out = LinkedHashMap<String, Any?>()
            for ((key, item) in raw) {
                val name = key as? String ?: continue
                out[name] = item
            }
            return out
        }

        private fun firstText(map: Map<String, *>, keys: List<String>): String? {
            for (key in keys) {
                val text = scalarText(map[key]) ?: continue
                return text
            }
            return null
        }

        private fun scalarText(value: Any?): String? {
            val text = when (value) {
                is String -> value.trim()
                null, is Number, is Boolean, is Map<*, *>, is Iterable<*> -> return null
                else -> value.toString().trim()
            }
            if (text.isEmpty() || text.equals("null", ignoreCase = true)) return null
            return text
        }

        private fun collectCapabilities(value: Any?, into: MutableSet<String>) {
            when (value) {
                null -> Unit
                is String -> if (value.isNotBlank()) into += value.trim()
                is Map<*, *> -> {
                    val named = value["name"] ?: value["id"] ?: value["capability"]
                    if (named != null) {
                        collectCapabilities(named, into)
                    } else {
                        for (key in value.keys) {
                            val token = key as? String ?: continue
                            if (token.isNotBlank()) into += token.trim()
                        }
                    }
                }
                is Iterable<*> -> value.forEach { collectCapabilities(it, into) }
                is Array<*> -> value.forEach { collectCapabilities(it, into) }
                else -> scalarText(value)?.let { into += it }
            }
        }
    }
}

sealed class TeleopDecision {
    data object Allow : TeleopDecision()
    data class Refuse(val messageResName: String) : TeleopDecision()
}

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
 */
class DroneTeleopGuard {

    fun decide(target: RobotTarget): TeleopDecision {
        if (isAerial(target)) {
            return TeleopDecision.Refuse(REFUSAL_STRING_RES_NAME)
        }
        return TeleopDecision.Allow
    }

    fun refuses(target: RobotTarget): Boolean = decide(target) is TeleopDecision.Refuse

    /**
     * Publishes [twist] on `/cmd_vel/teleop` only when [target] is a ground robot.
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

    /** True for the drone transport schemes this app must not drive. */
    fun isDroneTransportUri(uri: String?): Boolean {
        val scheme = schemeOf(uri) ?: return false
        return scheme in DRONE_URI_SCHEMES
    }

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

        /** `res/values/strings.xml` name shown when teleop is refused. */
        const val REFUSAL_STRING_RES_NAME = "drone_teleop_refused"

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
 */
data class RobotTarget(
    val profileId: String? = null,
    val transportUri: String? = null,
    val capabilities: Set<String> = emptySet(),
)

sealed class TeleopDecision {
    data object Allow : TeleopDecision()
    data class Refuse(val messageResName: String) : TeleopDecision()
}

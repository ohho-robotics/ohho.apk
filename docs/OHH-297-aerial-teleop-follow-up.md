# OHH-297 follow-up: climb control or altitude hold

Status: later issue. Not implemented in this change. This note is not a flight procedure and does not authorise arming an aircraft.

## What this app does today

The controls screen has one planar stick. `RobotRepository.sendVelocity` is the only publisher of `/cmd_vel/teleop`. That topic cannot climb. `DroneTeleopGuard` refuses the publish when the selected target is aerial:

- transport schemes `px4+dds://`, `mavlink+udp://`, `mavlink+serial://`, `crazyflie://`, `crazyflie+ros2://`
- a profile whose capabilities include `aerial`
- registry profile ids `px4-sitl`, `px4-x500`, `crazyflie-cf21`, `crazyflie-sim` (including `sim://` when the host is one of those ids)

The controls screen shows the string resource `drone_teleop_refused`. Nothing is written to `/cmd_vel/teleop`, including a zero Twist. Ground-robot teleop is unchanged. Emergency stop on `/emergency_stop` is unchanged and is not a flight-controller command.

`RobotRepository.selectRobot(profileId, transportUri, capabilities)` is the selection hook. A drone URI passed to `connect` is also refused and does not open a ROSBridge socket.

## Later issue

Do not remove the guard until one of these exists and is tested against **ohho sim-serve** (simulation only, no aircraft):

1. A second stick for climb, plus a deadman. Mode 2: left Y climb, left X yaw, right stick planar. Publish sim-serve's aerial velocity message (`frame` `BODY_FRD`, `deadman`) instead of planar `/cmd_vel/teleop`. No turbo on an aircraft.
2. Or an explicit **altitude hold, no climb** mode the operator turns on, still talking to sim-serve, and still not treating `/cmd_vel/teleop` as a flight setpoint.

Until that issue lands, keep the refusal.

Human TODO: file this follow-up under epic OHH-284. Do not treat this note as permission to connect a flight controller or to fly.

# OHH-38 — human follow-ups

These items are intentionally left for a human (not automated in this branch).

## Emulator screenshots / recording

- [ ] Start `python tools/rosbridge_stub.py` on the host (port 9090).
- [ ] Run the app on an API 35 emulator with robot IP `10.0.2.2`.
- [ ] Capture updated Home screenshots showing **LIVE** banner and `/battery_state` / `/diagnostics` labels.
- [ ] Re-record any demo video the team uses; this repo does not commit screen recordings.

## Real hardware

- [ ] Validate against a real `rosbridge` on the robot LAN (this issue does **not** claim that was done here).
- [ ] Confirm `/battery_state` and `/diagnostics` field names match your robot stack; adjust parsers if your diagnostic keys differ.

## Dataset recording (AI tab)

- [ ] Wire rosbag recorder topics on the robot and verify `startRecording` / `stopRecording` against hardware.

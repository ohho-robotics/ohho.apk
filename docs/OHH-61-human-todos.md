# OHH-61 human TODOs

Work tracked in Linear **OHH-61** (camera, map, agent goals). Automated coverage is in unit tests and ignored instrumented tests; the items below need a human on hardware or an emulator.

## Emulator + `rosbridge_stub.py`

- [ ] `pip install -r tools/requirements-stub.txt`
- [ ] `python tools/rosbridge_stub.py` (WS **9090**, MJPEG **8080**)
- [ ] Emulator Settings: robot IP **10.0.2.2**, ROSBridge port **9090**
- [ ] **Dashboard**: camera badge **● LIVE** after MJPEG frames; **CAMERA OFFLINE** when disconnected
- [ ] **Map → 2D SLAM**: occupancy grid from stub `/map`; robot triangle tracks `/odom`
- [ ] **AI** tab: send a goal; host console shows `[agent/goal] '…'`
- [ ] Remove `@Ignore` on `StubRosBridgeInstrumentedTest` and run `./gradlew connectedDebugAndroidTest`

## Real robot (Varun)

- [ ] Confirm MJPEG from robot `web_video_server` on port 8080 (not validated in this issue)
- [ ] Confirm `/map` and `/odom` on robot LAN match Map tab behavior
- [ ] Confirm `/agent/goal` is consumed by the on-robot agent stack
- [ ] Record a short screen capture (dashboard LIVE + map + AI goal) for the PR — **do not** commit fabricated recordings

## Screenshots

- [ ] Refresh `docs/screenshots/` after stub smoke test (optional; see OHH-38 list)

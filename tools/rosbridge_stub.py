#!/usr/bin/env python3
"""
Minimal ROSBridge WebSocket stub for emulator / UI dev (OHH-38).

This is NOT a robot. It answers subscribe/ping and publishes synthetic
/battery_state, /diagnostics, and /odom JSON for the Android app.

Usage:
  pip install -r tools/requirements-stub.txt
  python tools/rosbridge_stub.py --host 0.0.0.0 --port 9090

From the Android emulator, point Settings at 10.0.2.2:9090.
"""

from __future__ import annotations

import argparse
import asyncio
import json
import math
import time
from typing import Any

try:
    import websockets
    from websockets.server import WebSocketServerProtocol
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "Install dependencies: pip install -r tools/requirements-stub.txt"
    ) from exc

SUBSCRIBED: dict[WebSocketServerProtocol, set[str]] = {}


def battery_msg(t: float) -> dict[str, Any]:
    pct = 0.55 + 0.15 * math.sin(t * 0.2)
    return {
        "voltage": 12.4,
        "percentage": round(pct, 4),
        "power_supply_status": 2,
        "present": True,
    }


def diagnostics_msg(t: float) -> dict[str, Any]:
    base = 40 + 10 * math.sin(t * 0.3)
    return {
        "header": {"stamp": {"sec": int(t), "nanosec": 0}, "frame_id": ""},
        "status": [
            {
                "level": 0,
                "name": "omnibot: arm_battery",
                "message": "stub",
                "hardware_id": "stub",
                "values": [{"key": "percentage", "value": f"{72 + 5 * math.sin(t):.1f}"}],
            },
            {
                "level": 0,
                "name": "omnibot: rpi",
                "message": "stub",
                "hardware_id": "stub",
                "values": [
                    {"key": "temp_c", "value": f"{52 + 3 * math.sin(t * 0.1):.1f}"},
                    {"key": "ram_gb", "value": f"{1.8 + 0.2 * math.sin(t * 0.05):.2f}"},
                    {"key": "core1", "value": f"{base:.0f}"},
                    {"key": "core2", "value": f"{base + 8:.0f}"},
                    {"key": "core3", "value": f"{base - 6:.0f}"},
                    {"key": "core4", "value": f"{base + 2:.0f}"},
                ],
            },
        ],
    }


def odom_msg(t: float) -> dict[str, Any]:
    x = 0.5 * math.cos(t * 0.15)
    y = 0.5 * math.sin(t * 0.15)
    yaw = t * 0.1
    qz = math.sin(yaw / 2)
    qw = math.cos(yaw / 2)
    return {
        "pose": {
            "pose": {
                "position": {"x": x, "y": y, "z": 0.0},
                "orientation": {"x": 0.0, "y": 0.0, "z": qz, "w": qw},
            }
        },
        "twist": {
            "twist": {
                "linear": {"x": 0.0, "y": 0.0, "z": 0.0},
                "angular": {"x": 0.0, "y": 0.0, "z": 0.05},
            }
        },
    }


async def publish_loop(ws: WebSocketServerProtocol) -> None:
    while True:
        await asyncio.sleep(0.5)
        topics = SUBSCRIBED.get(ws)
        if not topics:
            continue
        now = time.time()
        for topic in list(topics):
            if topic == "/battery_state":
                payload = battery_msg(now)
            elif topic == "/diagnostics":
                payload = diagnostics_msg(now)
            elif topic == "/odom":
                payload = odom_msg(now)
            else:
                continue
            await ws.send(
                json.dumps({"op": "publish", "topic": topic, "msg": payload})
            )


async def handler(ws: WebSocketServerProtocol) -> None:
    SUBSCRIBED[ws] = set()
    pub_task = asyncio.create_task(publish_loop(ws))
    try:
        async for raw in ws:
            if raw.strip() == "":
                continue
            try:
                msg = json.loads(raw)
            except json.JSONDecodeError:
                continue
            op = msg.get("op")
            if op == "ping":
                await ws.send(json.dumps({"op": "ping", "time": time.time()}))
            elif op == "subscribe":
                topic = msg.get("topic")
                if isinstance(topic, str):
                    SUBSCRIBED[ws].add(topic)
            elif op == "unsubscribe":
                topic = msg.get("topic")
                if isinstance(topic, str):
                    SUBSCRIBED[ws].discard(topic)
    finally:
        pub_task.cancel()
        SUBSCRIBED.pop(ws, None)


async def main(host: str, port: int) -> None:
    print(f"ROSBridge stub listening on ws://{host}:{port} (not a real robot)")
    async with websockets.serve(handler, host, port, ping_interval=None):
        await asyncio.Future()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="ROSBridge WebSocket stub for ohho.apk")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=9090)
    args = parser.parse_args()
    asyncio.run(main(args.host, args.port))

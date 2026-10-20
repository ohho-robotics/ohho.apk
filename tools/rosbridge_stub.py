#!/usr/bin/env python3
"""
Minimal ROSBridge WebSocket stub for emulator / UI dev (OHH-38, OHH-61).

This is NOT a robot. It answers subscribe/ping, publishes synthetic
/battery_state, /diagnostics, /odom, and /map JSON for the Android app,
logs inbound /agent/goal publishes, and serves a tiny MJPEG stream on HTTP
8080 (web_video_server-style path).

Usage:
  pip install -r tools/requirements-stub.txt
  python tools/rosbridge_stub.py --host 0.0.0.0 --port 9090 --mjpeg-port 8080

From the Android emulator, point Settings at 10.0.2.2:9090 (ROSBridge) and
open the Dashboard camera against http://10.0.2.2:8080/stream?topic=...
"""

from __future__ import annotations

import argparse
import asyncio
import io
import json
import math
import threading
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from typing import Any

try:
    import websockets
    from websockets.server import WebSocketServerProtocol
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "Install dependencies: pip install -r tools/requirements-stub.txt"
    ) from exc

try:
    from PIL import Image, ImageDraw
except ImportError as exc:  # pragma: no cover
    raise SystemExit(
        "Install dependencies: pip install -r tools/requirements-stub.txt"
    ) from exc

SUBSCRIBED: dict[WebSocketServerProtocol, set[str]] = {}
MJPEG_BOUNDARY = "ohho_stub_frame"


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


def map_msg() -> dict[str, Any]:
    """Small synthetic occupancy grid with walls and a free corridor."""
    width, height = 48, 48
    data: list[int] = [-1] * (width * height)
    for y in range(height):
        for x in range(width):
            if x == 0 or y == 0 or x == width - 1 or y == height - 1:
                data[y * width + x] = 100
            elif 10 <= x <= 12:
                data[y * width + x] = 100
            else:
                data[y * width + x] = 0
    return {
        "header": {"stamp": {"sec": 0, "nanosec": 0}, "frame_id": "map"},
        "info": {
            "map_load_time": {"sec": 0, "nanosec": 0},
            "resolution": 0.05,
            "width": width,
            "height": height,
            "origin": {
                "position": {"x": -1.2, "y": -1.2, "z": 0.0},
                "orientation": {"x": 0.0, "y": 0.0, "z": 0.0, "w": 1.0},
            },
        },
        "data": data,
    }


def stub_jpeg_frame(t: float) -> bytes:
    """160×120 synthetic camera frame (changes slowly over time)."""
    phase = int(t * 4) % 256
    img = Image.new("RGB", (160, 120), color=(phase, 40, 120))
    draw = ImageDraw.Draw(img)
    draw.rectangle((20, 20, 140, 100), outline=(0, 255, 200), width=2)
    draw.text((24, 48), "OHHO STUB CAM", fill=(255, 255, 255))
    buf = io.BytesIO()
    img.save(buf, format="JPEG", quality=70)
    return buf.getvalue()


class MjpegHandler(BaseHTTPRequestHandler):
    """web_video_server-style multipart MJPEG (single repeating frame)."""

    def log_message(self, format: str, *args: Any) -> None:  # noqa: A003
        return  # quiet

    def do_GET(self) -> None:  # noqa: N802
        if not self.path.startswith("/stream"):
            self.send_error(404)
            return
        self.send_response(200)
        self.send_header(
            "Content-Type",
            f"multipart/x-mixed-replace; boundary={MJPEG_BOUNDARY}",
        )
        self.send_header("Cache-Control", "no-cache")
        self.send_header("Connection", "close")
        self.end_headers()
        try:
            while True:
                frame = stub_jpeg_frame(time.time())
                self.wfile.write(f"--{MJPEG_BOUNDARY}\r\n".encode())
                self.wfile.write(b"Content-Type: image/jpeg\r\n")
                self.wfile.write(f"Content-Length: {len(frame)}\r\n\r\n".encode())
                self.wfile.write(frame)
                self.wfile.write(b"\r\n")
                self.wfile.flush()
                time.sleep(0.2)
        except (BrokenPipeError, ConnectionResetError):
            pass


def start_mjpeg_server(host: str, port: int) -> ThreadingHTTPServer:
    server = ThreadingHTTPServer((host, port), MjpegHandler)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    return server


async def publish_loop(ws: WebSocketServerProtocol) -> None:
    last_map = 0.0
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
            elif topic == "/map":
                if now - last_map < 2.0:
                    continue
                last_map = now
                payload = map_msg()
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
                    if topic == "/map":
                        await ws.send(
                            json.dumps(
                                {"op": "publish", "topic": "/map", "msg": map_msg()}
                            )
                        )
            elif op == "unsubscribe":
                topic = msg.get("topic")
                if isinstance(topic, str):
                    SUBSCRIBED[ws].discard(topic)
            elif op == "publish":
                topic = msg.get("topic")
                if topic == "/agent/goal":
                    goal = (msg.get("msg") or {}).get("data")
                    print(f"[agent/goal] {goal!r}", flush=True)
    finally:
        pub_task.cancel()
        SUBSCRIBED.pop(ws, None)


async def main(host: str, port: int, mjpeg_port: int) -> None:
    mjpeg = start_mjpeg_server(host, mjpeg_port)
    print(
        f"ROSBridge stub ws://{host}:{port} · MJPEG http://{host}:{mjpeg_port}/stream "
        f"(not a real robot)",
        flush=True,
    )
    try:
        async with websockets.serve(handler, host, port, ping_interval=None):
            await asyncio.Future()
    finally:
        mjpeg.shutdown()


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="ROSBridge WebSocket stub for ohho.apk")
    parser.add_argument("--host", default="0.0.0.0")
    parser.add_argument("--port", type=int, default=9090)
    parser.add_argument("--mjpeg-port", type=int, default=8080)
    args = parser.parse_args()
    asyncio.run(main(args.host, args.port, args.mjpeg_port))

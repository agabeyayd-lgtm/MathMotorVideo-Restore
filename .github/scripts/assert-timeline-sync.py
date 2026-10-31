#!/usr/bin/env python3
"""Validate live playback/timeline synchronization samples emitted by the debug editor."""
from __future__ import annotations

import math
import re
import sys
from pathlib import Path

if len(sys.argv) != 2:
    raise SystemExit("Usage: assert-timeline-sync.py <logcat-file>")

log_path = Path(sys.argv[1])
if not log_path.is_file():
    raise SystemExit(f"Missing timeline sync log: {log_path}")

log = log_path.read_text(encoding="utf-8", errors="replace")
if "TIMELINE_SYNC_TEST_ENABLED" not in log:
    raise SystemExit("The editor did not enable the requested timeline sync test flag.")

sample_pattern = re.compile(
    r"TIMELINE_SYNC_SAMPLE "
    r"playing=(true|false) "
    r"playerMs=(\d+) "
    r"playheadMs=(\d+) "
    r"scrollXs=([0-9,]*) "
    r"originRightXs=([0-9,-]*) "
    r"pxPerSecond=([0-9.]+) "
    r"expectedScrollPx=(\d+) "
    r"playheadCenterPx=(-?\d+) "
    r"playButtonCenterPx=(-?\d+) "
    r"expectedCenterPx=(-?\d+)"
)

samples: list[dict[str, object]] = []
for match in sample_pattern.finditer(log):
    (playing, player_ms, playhead_ms, raw_scrolls, raw_origins, raw_pps,
     expected_scroll, center, play_button_center, expected_center) = match.groups()
    if playing != "true":
        continue
    scrolls = [int(value) for value in raw_scrolls.split(",") if value]
    origins = [int(value) for value in raw_origins.split(",") if value]
    if not scrolls or not origins:
        continue
    samples.append({
        "player_ms": int(player_ms),
        "playhead_ms": int(playhead_ms),
        "scrolls": scrolls,
        "origins": origins,
        "pps": float(raw_pps),
        "expected_scroll": int(expected_scroll),
        "center": int(center),
        "play_button_center": int(play_button_center),
        "expected_center": int(expected_center),
    })

if len(samples) < 5:
    raise SystemExit(
        f"FAIL: expected at least 5 live playback samples; found {len(samples)}. "
        "Playback may not have started or the editor did not report its timeline state."
    )

first_player = int(samples[0]["player_ms"])
last_player = int(samples[-1]["player_ms"])
if last_player - first_player < 1200:
    raise SystemExit(
        f"FAIL: player position advanced only {last_player - first_player} ms "
        f"({first_player} -> {last_player}); playback movement was not demonstrated."
    )

baseline_center = int(samples[0]["center"])
lane_count = len(samples[0]["scrolls"])
if lane_count < 2:
    raise SystemExit(
        f"FAIL: only {lane_count} timeline lane(s) reported; expected at least ruler + video filmstrip."
    )

for index, sample in enumerate(samples):
    player_ms = int(sample["player_ms"])
    playhead_ms = int(sample["playhead_ms"])
    scrolls = sample["scrolls"]
    pps = float(sample["pps"])
    expected_scroll = int(sample["expected_scroll"])
    center = int(sample["center"])
    expected_center = int(sample["expected_center"])

    origins = sample["origins"]
    if len(scrolls) != lane_count or len(origins) != lane_count:
        raise SystemExit(
            f"FAIL: timeline lane count changed at sample {index}: "
            f"expected {lane_count}, scrolls={len(scrolls)}, origins={len(origins)}."
        )
    if any(value < 0 for value in origins):
        raise SystemExit(f"FAIL: a lane's time-zero origin could not be measured: {origins}.")
    if len(set(origins)) != 1:
        raise SystemExit(f"FAIL: ruler/video time-zero edges do not align: {origins}.")
    if abs(int(origins[0]) + int(scrolls[0]) - center) > 1:
        raise SystemExit(
            f"FAIL: shared time-zero edge does not map to the fixed playhead: "
            f"originRightX={origins[0]}px + scroll={scrolls[0]}px, playhead={center}px."
        )
    if any(value < 0 for value in scrolls):
        raise SystemExit(f"FAIL: negative lane scroll at sample {index}: {scrolls}.")
    if len(set(scrolls)) != 1:
        raise SystemExit(f"FAIL: timeline lanes drifted apart at sample {index}: {scrolls}.")
    if abs(player_ms - playhead_ms) > 750:
        raise SystemExit(
            f"FAIL: player/playhead time mismatch at sample {index}: "
            f"player={player_ms} ms, playhead={playhead_ms} ms."
        )
    if abs(center - baseline_center) > 1:
        raise SystemExit(
            f"FAIL: playhead moved horizontally during playback: "
            f"baseline={baseline_center}px, sample={center}px."
        )
    if abs(center - expected_center) > 1:
        raise SystemExit(
            f"FAIL: playhead is not at the calculated timeline center: "
            f"actual={center}px, expected={expected_center}px."
        )
    if abs(center - int(sample["play_button_center"])) > 1:
        raise SystemExit(
            f"FAIL: fixed playhead is not vertically aligned with the Play button: "
            f"playhead={center}px, Play={sample['play_button_center']}px."
        )

    # Kotlin roundToInt() for non-negative values is floor(value + 0.5).
    math_expected_scroll = math.floor((playhead_ms / 1000.0) * pps + 0.5)
    if abs(expected_scroll - math_expected_scroll) > 1:
        raise SystemExit(
            f"FAIL: app scroll expectation does not match its shared time scale "
            f"at sample {index}: app={expected_scroll}px, math={math_expected_scroll}px."
        )
    if abs(scrolls[0] - expected_scroll) > 1:
        raise SystemExit(
            f"FAIL: lane scroll does not match playhead time at sample {index}: "
            f"scroll={scrolls[0]}px, expected={expected_scroll}px, time={playhead_ms}ms."
        )

if max(int(sample["playhead_ms"]) for sample in samples) <= 0:
    raise SystemExit("FAIL: playhead remained at time zero.")

print("PASS: live timeline playback synchronization")
print(f"  Samples while playing: {len(samples)}")
print(f"  Player position: {first_player} -> {last_player} ms")
print(f"  Timeline lanes synchronized: {lane_count}")
print(f"  Shared ruler/filmstrip scroll follows playhead time; no negative scroll")
print(f"  Fixed playhead/Play center: {baseline_center}px")
print("  Ruler and first-video-frame zero edges share the same screen coordinate")

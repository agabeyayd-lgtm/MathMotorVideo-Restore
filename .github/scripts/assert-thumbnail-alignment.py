#!/usr/bin/env python3
"""Assert filmstrip/playhead alignment using measured Android View bounds."""
from __future__ import annotations

import re
import statistics
import sys
from pathlib import Path

SNAPSHOT_RE = re.compile(
    r"TIMELINE_ALIGNMENT_SNAPSHOT "
    r"playheadCenterPx=(\d+) playheadCenterYpx=(\d+) lineCenterPx=(\d+) "
    r"previewBounds=(\d+,\d+,\d+,\d+) "
    r"clipBounds=(?:none|\d+,\d+,\d+,\d+) "
    r"cellCandidates=(\d+) visibleCells=(\d+) cells=(.*)"
)

def parse_bounds(value: str):
    numbers = tuple(map(int, value.split(",")))
    if len(numbers) != 4:
        return None
    left, top, right, bottom = numbers
    if right <= left or bottom <= top:
        return None
    return left, top, right, bottom

def latest_snapshot(log_path: str):
    latest = None
    latest_with_cells = None
    for line in Path(log_path).read_text(encoding="utf-8", errors="replace").splitlines():
        match = SNAPSHOT_RE.search(line)
        if not match:
            continue
        playhead_x, playhead_y, line_x = map(int, match.group(1, 2, 3))
        preview = parse_bounds(match.group(4))
        if preview is None:
            continue
        clip_match = re.search(r"clipBounds=(none|\d+,\d+,\d+,\d+)", line)
        clip = None if not clip_match or clip_match.group(1) == "none" else parse_bounds(clip_match.group(1))
        cells = []
        for item in match.group(7).split(";"):
            if not item or ":" not in item:
                continue
            timestamp_text, bounds_text = item.split(":", 1)
            bounds = parse_bounds(bounds_text)
            try:
                timestamp = int(timestamp_text)
            except ValueError:
                continue
            if bounds is not None:
                cells.append((timestamp, bounds))
        latest = {
            "playhead_x": playhead_x,
            "playhead_y": playhead_y,
            "line_x": line_x,
            "preview": preview,
            "clip": clip,
            "cells": cells,
            "cell_candidates": int(match.group(5)),
            "visible_cells": int(match.group(6)),
        }
        if cells:
            latest_with_cells = latest
    if latest_with_cells is not None:
        latest = latest_with_cells
    if latest is None:
        raise SystemExit(
            "FAIL: no app-measured timeline snapshot found; "
            "the debug editor did not report actual View bounds."
        )
    if not latest["cells"]:
        raise SystemExit(
            "FAIL: app snapshot contains no visible timestamped thumbnail cells "
            f"(candidates={latest['cell_candidates']}, "
            f"visible intersections={latest['visible_cells']})."
        )
    if abs(latest["line_x"] - latest["playhead_x"]) > 5:
        raise SystemExit(
            f"FAIL: fixed playhead line and Play button are misaligned: "
            f"line={latest['line_x']}px button={latest['playhead_x']}px."
        )
    return latest

def prepare(log_path: str):
    snapshot = latest_snapshot(log_path)
    cells = [
        (abs((box[0] + box[2]) // 2 - snapshot["playhead_x"]),
         (box[0] + box[2]) // 2,
         (box[1] + box[3]) // 2,
         timestamp)
        for timestamp, box in snapshot["cells"]
    ]
    _, x, y, sample_ms = min(cells)
    print(x, y, snapshot["playhead_x"], sample_ms)

def pixel_mean(image, x, y, radius=2):
    image = image.convert("RGB")
    values = []
    for py in range(max(0, y-radius), min(image.height, y+radius+1)):
        for px in range(max(0, x-radius), min(image.width, x+radius+1)):
            values.append(image.getpixel((px, py)))
    return tuple(round(statistics.mean(pixel[channel] for pixel in values)) for channel in range(3))

def verify(log_path: str, screenshot_path: str):
    try:
        from PIL import Image
    except ImportError as exc:
        raise SystemExit("FAIL: Pillow is required for the preview/thumbnail pixel check.") from exc

    log = Path(log_path).read_text(encoding="utf-8", errors="replace")
    snapshot = latest_snapshot(log_path)
    cell = None
    sample_ms = None
    for timestamp, box in snapshot["cells"]:
        x = (box[0] + box[2]) // 2
        if abs(x - snapshot["playhead_x"]) <= 5:
            cell = box
            sample_ms = timestamp
            break
    if cell is None or sample_ms is None:
        nearest = min(
            (abs((box[0] + box[2]) // 2 - snapshot["playhead_x"]), timestamp)
            for timestamp, box in snapshot["cells"]
        )
        raise SystemExit(
            f"FAIL: no fully visible thumbnail cell is centered under the fixed playhead "
            f"(nearest delta={nearest[0]}px, timestamp={nearest[1]}ms)."
        )

    ready = [
        (int(project), int(source))
        for project, source in re.findall(
            r"TIMELINE_PREVIEW_FRAME_READY projectMs=(\d+) sourceMs=(\d+)", log
        )
    ]
    if not any(abs(project - sample_ms) <= 35 and abs(source - sample_ms) <= 35 for project, source in ready):
        raise SystemExit(
            f"FAIL: preview did not decode the timestamp represented by the centered thumbnail "
            f"(thumbnail={sample_ms}ms, ready preview frames={ready[-5:]})."
        )
    scrub_ready = [
        (int(project), int(source))
        for project, source in re.findall(
            r"TIMELINE_SCRUB_PREVIEW_FRAME_READY projectMs=(\d+) sourceMs=(\d+)", log
        )
    ]
    if not any(abs(project - sample_ms) <= 35 and abs(source - sample_ms) <= 35 for project, source in scrub_ready):
        raise SystemExit(
            f"FAIL: dragging the filmstrip did not update the preview to the centered playhead time "
            f"(thumbnail={sample_ms}ms, scrub preview frames={scrub_ready[-5:]})."
        )
    if not re.search(rf"TIMELINE_THUMBNAIL_READY sourceMs={sample_ms} ", log):
        raise SystemExit(f"FAIL: centered thumbnail sample {sample_ms}ms was not confirmed decoded.")

    timing_rows = [
        tuple(map(int, row))
        for row in re.findall(
            r"TIMELINE_THUMBNAIL_TIMING sourceMs=(\\d+) success=true "
            r"queueWaitMs=(\\d+) frameDecodeMs=(\\d+) bitmapScaleMs=(\\d+) "
            r"uiDispatchWaitMs=(\\d+) uiApplyMs=(\\d+)",
            log,
        )
    ]
    if timing_rows:
        queue_waits = [row[1] for row in timing_rows]
        decode_times = [row[2] for row in timing_rows]
        total_times = [row[1] + row[2] + row[3] + row[4] + row[5] for row in timing_rows]
        print(
            "  Thumbnail timing (n={}) queue median/max={}ms/{}ms; "
            "decode median/max={}ms/{}ms; enqueue-to-display median/max={}ms/{}ms".format(
                len(timing_rows),
                int(statistics.median(queue_waits)), max(queue_waits),
                int(statistics.median(decode_times)), max(decode_times),
                int(statistics.median(total_times)), max(total_times),
            )
        )
    else:
        print("  Thumbnail timing: no successful decode timing records in this log capture.")

    image = Image.open(screenshot_path).convert("RGB")
    thumbnail_nodes = [
        (timestamp, box)
        for timestamp, box in snapshot["cells"]
        if box[0] >= 0 and box[1] >= 0
        and box[2] <= image.width and box[3] <= image.height
        and box[2] - box[0] >= 8 and box[3] - box[1] >= 8
    ]
    if len(thumbnail_nodes) < 3:
        raise SystemExit(
            f"FAIL: only {len(thumbnail_nodes)} fully visible timestamped thumbnails; "
            "at least three are required to verify frame diversity."
        )

    fingerprints = []
    for timestamp, box in thumbnail_nodes:
        fingerprint = []
        for fy in (0.2, 0.5, 0.8):
            for fx in (0.2, 0.5, 0.8):
                x = round(box[0] + (box[2] - box[0]) * fx)
                y = round(box[1] + (box[3] - box[1]) * fy)
                fingerprint.extend(pixel_mean(image, x, y, radius=1))
        fingerprints.append((timestamp, tuple(fingerprint)))
    unique_fingerprints = len({fingerprint for _, fingerprint in fingerprints})
    required_unique = max(3, (len(fingerprints) * 3 + 3) // 4)
    if unique_fingerprints < required_unique:
        raise SystemExit(
            f"FAIL: filmstrip frames are not visually distinct enough: "
            f"{unique_fingerprints}/{len(fingerprints)} unique thumbnails "
            f"for timestamps {[timestamp for timestamp, _ in fingerprints]}."
        )

    preview = snapshot["preview"]
    preview_x = (preview[0] + preview[2]) // 2
    preview_y = (preview[1] + preview[3]) // 2
    thumb_x = (cell[0] + cell[2]) // 2
    thumb_y = (cell[1] + cell[3]) // 2
    cell_width = cell[2] - cell[0]
    cell_height = cell[3] - cell[1]
    source_aspect = cell_width / max(1, cell_height)
    preview_frame_width = min(preview[2] - preview[0], round((preview[3] - preview[1]) * source_aspect))
    thumbnail_frame_width = min(cell_width, round(cell_height * source_aspect))
    sample_fraction = 0.15
    preview_sample_offset = max(2, round(preview_frame_width * sample_fraction))
    thumbnail_sample_offset = max(2, round(thumbnail_frame_width * sample_fraction))
    preview_rgb = pixel_mean(image, preview_x + preview_sample_offset, preview_y)
    thumb_rgb = pixel_mean(image, thumb_x + thumbnail_sample_offset, thumb_y)
    channel_delta = max(abs(preview_rgb[i] - thumb_rgb[i]) for i in range(3))
    if channel_delta > 55:
        raise SystemExit(
            f"FAIL: preview and centered filmstrip thumbnail pixels disagree: "
            f"preview={preview_rgb}, thumbnail={thumb_rgb}, max-channel-delta={channel_delta}, "
            f"sample={sample_ms}ms."
        )

    print("PASS: preview frame matches the filmstrip cell centered under the playhead")
    print(f"  Visually distinct thumbnails: {unique_fingerprints}/{len(fingerprints)}")
    print(f"  Sample timestamp: {sample_ms}ms")
    print(f"  Preview comparison RGB: {preview_rgb}")
    print(f"  Thumbnail comparison RGB: {thumb_rgb}")
    print(f"  Maximum channel delta: {channel_delta}")

if __name__ == "__main__":
    if len(sys.argv) == 3 and sys.argv[1] == "prepare":
        prepare(sys.argv[2])
    elif len(sys.argv) == 4 and sys.argv[1] == "verify":
        verify(sys.argv[2], sys.argv[3])
    else:
        raise SystemExit(
            "Usage: assert-thumbnail-alignment.py prepare <logcat.txt> | "
            "verify <logcat.txt> <screenshot.png>"
        )

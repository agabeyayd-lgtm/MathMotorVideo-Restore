#!/usr/bin/env python3
"""Assert filmstrip/playhead alignment using measured Android View bounds."""
from __future__ import annotations

import re
import statistics
import sys
from pathlib import Path

from ci_image import Image

SNAPSHOT_RE = re.compile(
    r"TIMELINE_ALIGNMENT_SNAPSHOT "
    r"playheadCenterPx=(\d+) playheadCenterYpx=(\d+) lineCenterPx=(\d+) "
    r"previewBounds=(\d+,\d+,\d+,\d+) "
    r"clipBounds=(?:none|\d+,\d+,\d+,\d+) "
    r"(?:clipBoundsByIndex=\S+ )?"
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
    # Screenshot decoding is provided by ci_image.py through the already-installed FFmpeg binary.

    log = Path(log_path).read_text(encoding="utf-8", errors="replace")
    snapshot = latest_snapshot(log_path)
    cell = None
    sample_ms = None
    for timestamp, box in snapshot["cells"]:
        x = (box[0] + box[2]) // 2
        if abs(x - snapshot["playhead_x"]) <= 2:
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
    time_tolerance_ms = 35
    if not any(
        abs(project - sample_ms) <= time_tolerance_ms
        and abs(source - sample_ms) <= time_tolerance_ms
        for project, source in ready
    ):
        raise SystemExit(
            f"FAIL: preview did not decode the timestamp represented by the centered thumbnail "
            f"within the fixed {time_tolerance_ms}ms limit "
            f"(thumbnail={sample_ms}ms, ready preview frames={ready[-5:]})."
        )
    scrub_ready = [
        (int(project), int(source))
        for project, source in re.findall(
            r"TIMELINE_SCRUB_PREVIEW_FRAME_READY projectMs=(\d+) sourceMs=(\d+)", log
        )
    ]
    if not any(
        abs(project - sample_ms) <= time_tolerance_ms
        and abs(source - sample_ms) <= time_tolerance_ms
        for project, source in scrub_ready
    ):
        raise SystemExit(
            f"FAIL: dragging the filmstrip did not update the preview to the centered playhead time "
            f"within the fixed {time_tolerance_ms}ms limit "
            f"(thumbnail={sample_ms}ms, scrub preview frames={scrub_ready[-5:]})."
        )
    if not re.search(rf"TIMELINE_THUMBNAIL_READY sourceMs={sample_ms} ", log):
        raise SystemExit(f"FAIL: centered thumbnail sample {sample_ms}ms was not confirmed decoded.")

    timing_rows = [
        tuple(map(int, row))
        for row in re.findall(
            r"TIMELINE_THUMBNAIL_TIMING sourceMs=(\d+) success=true "
            r"queueWaitMs=(\d+) frameDecodeMs=(\d+) bitmapScaleMs=(\d+) "
            r"uiDispatchWaitMs=(\d+) uiApplyMs=(\d+) "
            r"enqueuedUptimeMs=(\d+) uiAppliedUptimeMs=(\d+)",
            log,
        )
    ]
    decoder_rows = re.findall(
        r"TIMELINE_THUMBNAIL_DECODED requestedMs=(\d+)(?: actualFrameMs=(\d+))? "
        r"decoder=([a-z0-9-]+) worker=(\d+) decodeMs=(\d+)",
        log,
    )
    mmr_rows = [row for row in decoder_rows if row[2] == "platform-mmr-primary"]
    media3_rows = [row for row in decoder_rows if row[2] == "media3-fallback"]
    if not decoder_rows or len(timing_rows) < 3:
        raise SystemExit(
            "FAIL: thumbnail decode diagnostics are incomplete; "
            f"decoder records={decoder_rows[-8:]}, successful timing rows={len(timing_rows)}."
        )
    decoder_times = [int(row[4]) for row in decoder_rows]
    print(
        "  Decoder path: native MMR primary={} (median={}ms); "
        "Media3 fallback={}".format(
            len(mmr_rows),
            int(statistics.median([int(row[4]) for row in mmr_rows])) if mmr_rows else 0,
            len(media3_rows),
        )
    )

    preview_timing_matches = re.findall(
        r"TIMELINE_PREVIEW_FRAME_READY projectMs=(\d+) sourceMs=(\d+).*?"
        r"requestId=(\d+) queueWaitMs=(\d+) decodeMs=(\d+) "
        r"uiDispatchWaitMs=(\d+) uiApplyMs=(\d+) totalMs=(\d+) "
        r"requestKind=(bootstrap|interactive)",
        log,
    )
    preview_timing_rows = [
        (row[8], *(int(value) for value in row[:8]))
        for row in preview_timing_matches
    ]
    bootstrap_rows = [row for row in preview_timing_rows if row[0] == "bootstrap"]
    interactive_rows = [row for row in preview_timing_rows if row[0] == "interactive"]
    state_ready = re.search(r"TIMELINE_PLAYER_PLAYBACK_STATE state=3", log)
    bootstrap_executed = re.search(r"TIMELINE_INITIAL_BASE_FRAME_BOOTSTRAP executed=true", log)
    if not bootstrap_rows or not bootstrap_executed or not state_ready:
        raise SystemExit(
            "FAIL: cold-source preview bootstrap was not confirmed after CompositionPlayer STATE_READY."
        )
    if state_ready.start() > bootstrap_executed.start():
        raise SystemExit(
            "FAIL: cold-source still decoding started before the composed video player reached STATE_READY."
        )
    if len(interactive_rows) < 2:
        raise SystemExit(
            "FAIL: fewer than two warmed interactive preview frames were measured; "
            f"only {len(interactive_rows)} interactive timings were captured."
        )

    bootstrap_decode = [row[5] for row in bootstrap_rows]
    print(
        "  Cold-source bootstrap (n={}) decode median/max={}ms/{}ms; "
        "CompositionPlayer was READY before this one-time decoder open.".format(
            len(bootstrap_rows),
            int(statistics.median(bootstrap_decode)), max(bootstrap_decode),
        )
    )
    preview_queue = [row[4] for row in interactive_rows]
    preview_decode = [row[5] for row in interactive_rows]
    preview_ui_dispatch = [row[6] for row in interactive_rows]
    preview_total = [row[8] for row in interactive_rows]
    print(
        "  Warm interactive preview timing (n={}) queue median/max={}ms/{}ms; "
        "decode median/max={}ms/{}ms; UI dispatch median/max={}ms/{}ms; "
        "request-to-visible median/max={}ms/{}ms".format(
            len(interactive_rows),
            int(statistics.median(preview_queue)), max(preview_queue),
            int(statistics.median(preview_decode)), max(preview_decode),
            int(statistics.median(preview_ui_dispatch)), max(preview_ui_dispatch),
            int(statistics.median(preview_total)), max(preview_total),
        )
    )
    if max(preview_queue) > 250:
        raise SystemExit(
            f"FAIL: warmed latest-frame preview waited {max(preview_queue)}ms in its queue; limit is 250ms."
        )
    if max(preview_decode) > 800:
        raise SystemExit(
            f"FAIL: warmed exact preview frame decode reached {max(preview_decode)}ms; limit is 800ms."
        )
    if max(preview_total) > 1_000:
        raise SystemExit(
            f"FAIL: warmed preview request-to-visible reached {max(preview_total)}ms; limit is 1000ms."
        )
    print(
        "  PASS: warmed interactive preview stayed within 250ms queue, 800ms decode, "
        "and 1000ms request-to-visible limits."
    )

    discarded_preview = re.findall(
        r"TIMELINE_PREVIEW_FRAME_DISCARDED requestId=(\d+) stage=([a-z-]+)"
        r"(?: queueWaitMs=(\d+) decodeMs=(\d+))?",
        log,
    )
    if discarded_preview:
        decoded_discarded = [row for row in discarded_preview if row[2] and row[3]]
        print(
            "  Superseded playhead frames: {} total; {} discarded after decode.".format(
                len(discarded_preview), len(decoded_discarded)
            )
        )

    visible_sources = {timestamp for timestamp, _ in snapshot["cells"]}
    visible_timing_rows = [row for row in timing_rows if row[0] in visible_sources]
    if len(visible_timing_rows) < 3:
        raise SystemExit(
            "FAIL: fewer than three visible timeline cells have measured decode/display timing; "
            f"visible timestamps={sorted(visible_sources)}, timing timestamps={[row[0] for row in timing_rows]}."
        )

    queue_waits = [row[1] for row in visible_timing_rows]
    decode_times = [row[2] for row in visible_timing_rows]
    total_times = [row[1] + row[2] + row[3] + row[4] + row[5] for row in visible_timing_rows]
    visible_fill_ms = max(row[7] for row in visible_timing_rows) - min(row[6] for row in visible_timing_rows)
    print(
        "  Visible thumbnail timing (n={}) queue median/max={}ms/{}ms; "
        "decode median/max={}ms/{}ms; enqueue-to-display median/max={}ms/{}ms; "
        "visible-strip fill={}ms".format(
            len(visible_timing_rows),
            int(statistics.median(queue_waits)), max(queue_waits),
            int(statistics.median(decode_times)), max(decode_times),
            int(statistics.median(total_times)), max(total_times),
            visible_fill_ms,
        )
    )
    # With eight visible cells and three independent workers, the final queued
    # cell can legitimately wait across multiple decode waves. The user-visible
    # responsiveness gate is the complete strip-fill time below, not a 1s
    # per-task queue cap that ignores concurrent batch size.
    if max(queue_waits) > 2_000:
        raise SystemExit(
            f"FAIL: visible thumbnail queue wait reached {max(queue_waits)}ms; limit is 2000ms."
        )
    if max(decode_times) > 1_500:
        raise SystemExit(
            f"FAIL: visible thumbnail decode reached {max(decode_times)}ms; limit is 1500ms."
        )
    if visible_fill_ms > 2_500:
        raise SystemExit(
            f"FAIL: visible filmstrip took {visible_fill_ms}ms to fill; limit is 2500ms."
        )

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
    resampling = getattr(Image, "Resampling", Image).BICUBIC
    for timestamp, box in thumbnail_nodes:
        # Compare the whole rendered cell rather than a sparse 3x3 probe grid.
        # Small moving details can fall between probe points even when the frames
        # are visibly different. Normalizing every full tile to the same grid
        # keeps the comparison independent of device density and cell dimensions.
        crop = image.crop(box)
        normalized = crop.resize((24, 44), resampling).convert("RGB")
        # Quantize low-order compression/interpolation noise, but retain spatial
        # changes such as a moving object or a changing scene.
        fingerprint = bytes(channel // 8 for channel in normalized.tobytes())
        fingerprints.append((timestamp, fingerprint))
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
    print(f"  Fixed time tolerance: {time_tolerance_ms}ms")
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

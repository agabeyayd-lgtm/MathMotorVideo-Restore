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
    # Android bounds are integer edges, so keep the geometric center exact
    # by storing it doubled. This preserves .5px centers instead of flooring
    # them to a whole pixel before the alignment decision.
    cells = [
        (abs((box[0] + box[2]) - 2 * snapshot["playhead_x"]),
         box[0] + box[2],
         (box[1] + box[3]) // 2,
         timestamp)
        for timestamp, box in snapshot["cells"]
    ]
    _, center_x_twice, y, sample_ms = min(cells)
    print(center_x_twice, y, 2 * snapshot["playhead_x"], sample_ms)

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
        # Acceptance contract: compare geometric centres at half-pixel precision;
        # never round away a full-pixel offset or relax this threshold to pass CI.
        # Keep this in doubled-pixel units: integer Android bounds can produce
        # half-pixel geometric centers, but a full 1px offset must never pass.
        # Compare exact geometric centers in half-pixel units. The sole
        # representational allowance is <=0.5px; a full 1px offset fails.
        center_x_twice = box[0] + box[2]
        if abs(center_x_twice - 2 * snapshot["playhead_x"]) <= 1:
            cell = box
            sample_ms = timestamp
            break
    if cell is None or sample_ms is None:
        nearest = min(
            (abs((box[0] + box[2]) - 2 * snapshot["playhead_x"]), timestamp)
            for timestamp, box in snapshot["cells"]
        )
        raise SystemExit(
            f"FAIL: no fully visible thumbnail cell is centered under the fixed playhead "
            f"(nearest geometric-center delta={nearest[0] / 2:g}px, timestamp={nearest[1]}ms; "
            "maximum representational difference is 0.5px)."
        )

    # A grid thumbnail may deliberately stand in for a nearby requested cell
    # (for example, targetMs=1708 uses gridMs=1500 within the 250ms grid limit).
    # Its accessibility timestamp is therefore not the exact playhead time.
    # The exact preview must match the latest app-measured playhead from this
    # same final alignment capture, with the existing 35ms frame tolerance.
    playhead_samples = [
        int(playhead)
        for playhead in re.findall(
            r"TIMELINE_SYNC_SAMPLE .*?playheadMs=(\d+)", log
        )
    ]
    if not playhead_samples:
        raise SystemExit(
            "FAIL: no app-measured playhead timestamp was recorded for the centered thumbnail."
        )
    preview_target_ms = playhead_samples[-1]
    ready = [
        (int(project), int(source))
        for project, source in re.findall(
            r"TIMELINE_PREVIEW_FRAME_READY projectMs=(\d+) sourceMs=(\d+)", log
        )
    ]
    time_tolerance_ms = 35
    if not any(
        abs(project - preview_target_ms) <= time_tolerance_ms
        and abs(source - preview_target_ms) <= time_tolerance_ms
        for project, source in ready
    ):
        raise SystemExit(
            f"FAIL: preview did not decode the exact playhead timestamp within the fixed "
            f"{time_tolerance_ms}ms limit (grid thumbnail={sample_ms}ms, "
            f"playhead={preview_target_ms}ms, ready preview frames={ready[-5:]})."
        )
    scrub_ready = [
        (int(project), int(source))
        for project, source in re.findall(
            r"TIMELINE_SCRUB_PREVIEW_FRAME_READY projectMs=(\d+) sourceMs=(\d+)", log
        )
    ]
    if not any(
        abs(project - preview_target_ms) <= time_tolerance_ms
        and abs(source - preview_target_ms) <= time_tolerance_ms
        for project, source in scrub_ready
    ):
        raise SystemExit(
            f"FAIL: dragging the filmstrip did not update the preview to the exact playhead "
            f"timestamp within the fixed {time_tolerance_ms}ms limit "
            f"(grid thumbnail={sample_ms}ms, playhead={preview_target_ms}ms, "
            f"scrub preview frames={scrub_ready[-5:]})."
        )
    grid_paint_events = []
    for line in log.splitlines():
        match = re.search(
            r"TIMELINE_GRID_(?:FIRST_PAINT|NEAREST_UPDATE) uri=(\S+) "
            r"targetMs=(\d+).*?gridMs=(\d+).*?elapsedMs=(\d+).*?"
            r"requestUptimeMs=(\d+).*?paintedUptimeMs=(\d+)",
            line,
        )
        if match:
            grid_paint_events.append((
                match.group(1), int(match.group(2)), int(match.group(3)),
                int(match.group(4)), int(match.group(5)), int(match.group(6))
            ))
    latest_grid_uri = grid_paint_events[-1][0] if grid_paint_events else None
    final_grid_by_target = {}
    if latest_grid_uri is not None:
        for uri, target_ms, grid_ms, elapsed_ms, request_uptime_ms, painted_uptime_ms in grid_paint_events:
            if uri == latest_grid_uri:
                final_grid_by_target[target_ms] = (
                    grid_ms, elapsed_ms, request_uptime_ms, painted_uptime_ms
                )
    # The accessibility timestamp on a filmstrip cell represents the actual
    # cached grid frame (gridMs), while the request that caused its paint may
    # target a nearby timeline slot (targetMs). Validate the frame actually
    # displayed under the playhead against gridMs, not the neighboring request.
    grid_sample_ready = any(
        uri == latest_grid_uri and abs(grid_ms - sample_ms) <= 250
        for uri, _target_ms, grid_ms, _elapsed_ms, _request_uptime_ms, _painted_uptime_ms
        in grid_paint_events
    )
    if not re.search(rf"TIMELINE_THUMBNAIL_READY sourceMs={sample_ms} ", log) and not grid_sample_ready:
        raise SystemExit(
            f"FAIL: centered filmstrip sample {sample_ms}ms has no grid frame within 250ms "
            "and no exact replacement confirmation."
        )

    timing_rows = [
        tuple(map(int, row))
        for row in re.findall(
            r"TIMELINE_THUMBNAIL_TIMING sourceMs=(\d+) success=true "
            r"queueWaitMs=(\d+) deferredWaitMs=(\d+) "
            r"diskLookupMs=(\d+) frameDecodeMs=(\d+) bitmapScaleMs=(\d+) "
            r"uiDispatchWaitMs=(\d+) uiApplyMs=(\d+) "
            r"enqueuedUptimeMs=(\d+) submittedUptimeMs=(\d+) "
            r"uiAppliedUptimeMs=(\d+)",
            log,
        )
    ]
    decoder_rows = re.findall(
        r"TIMELINE_THUMBNAIL_DECODED requestedMs=(\d+)(?: actualFrameMs=(\d+))? "
        r"decoder=([a-z0-9-]+) worker=(\d+) decodeMs=(\d+)",
        log,
    )
    mmr_rows = [row for row in decoder_rows if row[2].startswith("platform-mmr-")]
    media3_rows = [row for row in decoder_rows if row[2].startswith("media3-")]
    grid_frame_rows = {}
    for line in log.splitlines():
        match = re.search(
            r"TIMELINE_GRID_FRAME uri=(\S+) sourceMs=(\d+) stageMs=(\d+) "
            r"queueWaitMs=(\d+) diskLookupMs=(\d+) frameDecodeMs=(\d+) "
            r"frameElapsedMs=(\d+)",
            line,
        )
        if match:
            uri, source, stage, queue, disk, decode, elapsed = match.groups()
            grid_frame_rows[(uri, int(source))] = (
                int(queue), int(disk), int(decode), int(elapsed)
            )

    if len(timing_rows) < 3:
        visible_grid_rows = []
        for target_ms in sorted(visible_sources):
            match = final_grid_by_target.get(target_ms)
            if match is None or latest_grid_uri is None:
                continue
            grid_ms, elapsed_ms, request_uptime_ms, painted_uptime_ms = match
            queue_wait_ms, disk_lookup_ms, frame_decode_ms, _ = grid_frame_rows.get(
                (latest_grid_uri, grid_ms), (0, 0, 0, 0)
            )
            visible_grid_rows.append((
                target_ms, queue_wait_ms, 0, disk_lookup_ms, frame_decode_ms,
                0, 0, elapsed_ms, request_uptime_ms, request_uptime_ms,
                painted_uptime_ms
            ))
        latest_visible_timing_by_source = {row[0]: row for row in visible_grid_rows}
        visible_timing_rows = [
            latest_visible_timing_by_source[timestamp]
            for timestamp in sorted(latest_visible_timing_by_source)
        ]
    else:
        latest_visible_timing_by_source = {
            row[0]: row for row in timing_rows if row[0] in visible_sources
        }
        visible_timing_rows = [
            latest_visible_timing_by_source[timestamp]
            for timestamp in sorted(latest_visible_timing_by_source)
        ]

    if not decoder_rows or len(visible_timing_rows) < 3:
        raise SystemExit(
            "FAIL: thumbnail/grid decode diagnostics are incomplete; "
            f"decoder records={decoder_rows[-8:]}, "
            f"visible cells={sorted(visible_sources)}, "
            f"measured visible timings={[row[0] for row in visible_timing_rows]}."
        )
    print(
        "  Decoder path: Media3={} (median={}ms); native MMR fallback={}".format(
            len(media3_rows),
            int(statistics.median([int(row[4]) for row in media3_rows])) if media3_rows else 0,
            len(mmr_rows),
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

    # The one-clip preview may already have decoded and applied the exact paused
    # frame before CompositionPlayer reports READY. The production cache-hit path
    # marks that source bootstrap-complete and intentionally returns without
    # opening a second decoder. Accept that path only when an exact project/source
    # timestamp cache hit is immediately followed by this player's STATE_READY.
    cached_exact_bootstrap = None
    for cache_hit in re.finditer(
        r"TIMELINE_PREVIEW_EXACT_CACHE_HIT projectMs=(\d+) sourceMs=(\d+)", log
    ):
        project_ms, source_ms = map(int, cache_hit.groups())
        ready_after = re.search(
            r"TIMELINE_PLAYER_PLAYBACK_STATE state=3",
            log[cache_hit.end():cache_hit.end() + 1_024],
        )
        if abs(project_ms - source_ms) <= 35 and ready_after is not None:
            cached_exact_bootstrap = (project_ms, source_ms)
            break

    if state_ready is None:
        raise SystemExit(
            "FAIL: CompositionPlayer never reported STATE_READY for the preview bootstrap."
        )
    if bootstrap_rows and bootstrap_executed:
        if state_ready.start() > bootstrap_executed.start():
            raise SystemExit(
                "FAIL: cold-source still decoding started before the composed video player reached STATE_READY."
            )
    elif cached_exact_bootstrap is None:
        raise SystemExit(
            "FAIL: neither a post-READY cold preview decode nor an exact cached preview frame "
            "followed by CompositionPlayer STATE_READY was confirmed."
        )
    if len(interactive_rows) < 2:
        raise SystemExit(
            "FAIL: fewer than two warmed interactive preview frames were measured; "
            f"only {len(interactive_rows)} interactive timings were captured."
        )

    if bootstrap_rows:
        bootstrap_decode = [row[5] for row in bootstrap_rows]
        print(
            "  Cold-source bootstrap (n={}) decode median/max={}ms/{}ms; "
            "CompositionPlayer was READY before this one-time decoder open.".format(
                len(bootstrap_rows),
                int(statistics.median(bootstrap_decode)), max(bootstrap_decode),
            )
        )
    else:
        print(
            "  Cold-source preview used an exact frame already decoded and applied by the "
            "single-clip cache (projectMs={} sourceMs={}) before CompositionPlayer STATE_READY.".format(
                *cached_exact_bootstrap
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
    # The combined logcat contains multiple editor layouts from the same run.
    # A source timestamp can therefore have more than one successful timing row
    # (for example, an early partial strip and the later settled strip). Keep the
    # latest measurement for each timestamp in the final measured layout. Mixing
    # an early enqueue with a later layout's final display falsely inflates the
    # end-to-end fill interval even though every final-layout thumbnail is ready.
    latest_visible_timing_by_source = {
        row[0]: row for row in timing_rows if row[0] in visible_sources
    }
    visible_timing_rows = [
        latest_visible_timing_by_source[timestamp]
        for timestamp in sorted(latest_visible_timing_by_source)
    ]
    if len(visible_timing_rows) < 3:
        raise SystemExit(
            "FAIL: fewer than three visible timeline cells have measured decode/display timing; "
            f"visible timestamps={sorted(visible_sources)}, timing timestamps={[row[0] for row in timing_rows]}."
        )

    queue_waits = [row[1] for row in visible_timing_rows]
    deferred_waits = [row[2] for row in visible_timing_rows]
    disk_lookup_times = [row[3] for row in visible_timing_rows]
    decode_times = [row[4] for row in visible_timing_rows]
    total_times = [
        (row[7] if row[0] in final_grid_by_target else
         row[1] + row[2] + row[3] + row[4] + row[5] + row[6] + row[7])
        for row in visible_timing_rows
    ]
    # The end-to-end fill includes time held behind preview bootstrap and disk IO.
    # submittedUptimeMs is diagnostic only: visible work is owed from its request.
    visible_fill_ms = max(row[10] for row in visible_timing_rows) - min(row[8] for row in visible_timing_rows)
    print(
        "  Visible thumbnail timing (n={}) executor-queue median/max={}ms/{}ms; "
        "preview-deferred median/max={}ms/{}ms; disk lookup median/max={}ms/{}ms; "
        "decode median/max={}ms/{}ms; request-to-display median/max={}ms/{}ms; "
        "visible-strip fill={}ms".format(
            len(visible_timing_rows),
            int(statistics.median(queue_waits)), max(queue_waits),
            int(statistics.median(deferred_waits)), max(deferred_waits),
            int(statistics.median(disk_lookup_times)), max(disk_lookup_times),
            int(statistics.median(decode_times)), max(decode_times),
            int(statistics.median(total_times)), max(total_times),
            visible_fill_ms,
        )
    )
    if max(queue_waits) > 2_000:
        raise SystemExit(
            f"FAIL: visible thumbnail executor-queue wait reached {max(queue_waits)}ms; limit is 2000ms."
        )
    if max(deferred_waits) > 2_000:
        raise SystemExit(
            f"FAIL: visible thumbnail work was held behind preview bootstrap for {max(deferred_waits)}ms; limit is 2000ms."
        )
    if max(decode_times) > 1_500:
        # Keep the strict per-cell limit, but show which source timestamp and
        # worker produced every measured decode so a slow second-wave seek can
        # be traced without downloading/replaying the entire emulator session.
        latest_decoder_by_source = {}
        for requested, actual, decoder, worker, decode_ms in decoder_rows:
            latest_decoder_by_source[int(requested)] = (
                decoder, int(worker), int(decode_ms), actual
            )
        print("  Per-cell decode diagnostics (sourceMs, decodeMs, queueWaitMs, decoder, worker):")
        for row in visible_timing_rows:
            source_ms = row[0]
            decoder, worker, decode_ms, actual = latest_decoder_by_source.get(
                source_ms, ("unknown", -1, row[4], None)
            )
            print(
                f"    {source_ms}ms: {decode_ms}ms, queue={row[1]}ms, "
                f"decoder={decoder}, worker={worker}, actualFrameMs={actual or 'n/a'}"
            )
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

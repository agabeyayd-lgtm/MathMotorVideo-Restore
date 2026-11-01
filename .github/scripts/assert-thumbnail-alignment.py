#!/usr/bin/env python3
"""Align a filmstrip cell center with the fixed playhead and verify the rendered frame."""
from __future__ import annotations

import re
import statistics
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

def bounds(node):
    match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]", node.attrib.get("bounds", ""))
    return tuple(map(int, match.groups())) if match else None

def tree(path):
    return ET.parse(path).getroot()

def play_button_center(root):
    for node in root.iter("node"):
        if node.attrib.get("content-desc", "").startswith("Önizlemeyi oynat"):
            box = bounds(node)
            if box:
                return (box[0] + box[2]) // 2
    raise SystemExit("FAIL: accessible Play button not found for fixed-center reference.")

def prepare(xml_path):
    root = tree(xml_path)
    center = play_button_center(root)
    cells = []
    for node in root.iter("node"):
        match = re.fullmatch(r"Klip karesi @(\d+)ms(?: \(önbellek\))?", node.attrib.get("content-desc", ""))
        box = bounds(node)
        if match and box:
            x = (box[0] + box[2]) // 2
            y = (box[1] + box[3]) // 2
            cells.append((abs(x - center), x, y, int(match.group(1))))
    if not cells:
        raise SystemExit("FAIL: timestamped filmstrip thumbnail cells are not exposed in the editor hierarchy.")
    _, x, y, sample_ms = min(cells)
    print(x, y, center, sample_ms)

def pixel_mean(image, x, y, radius=2):
    image = image.convert("RGB")
    values = []
    for py in range(max(0, y-radius), min(image.height, y+radius+1)):
        for px in range(max(0, x-radius), min(image.width, x+radius+1)):
            values.append(image.getpixel((px, py)))
    return tuple(round(statistics.mean(pixel[channel] for pixel in values)) for channel in range(3))

def verify(xml_path, screenshot_path, log_path):
    try:
        from PIL import Image
    except ImportError as exc:
        raise SystemExit("FAIL: Pillow is required for the preview/thumbnail pixel check.") from exc

    root = tree(xml_path)
    center = play_button_center(root)
    cell = None
    sample_ms = None
    for node in root.iter("node"):
        match = re.fullmatch(r"Klip karesi @(\d+)ms(?: \(önbellek\))?", node.attrib.get("content-desc", ""))
        box = bounds(node)
        if match and box:
            x = (box[0] + box[2]) // 2
            if abs(x - center) <= 2:
                cell = (node, box)
                sample_ms = int(match.group(1))
                break
    if cell is None or sample_ms is None:
        raise SystemExit("FAIL: no filmstrip cell is centered under the fixed playhead after alignment.")

    preview = None
    for node in root.iter("node"):
        if node.attrib.get("content-desc") == "Oynatma noktasındaki tam video karesi":
            preview = bounds(node)
            break
    if preview is None:
        raise SystemExit("FAIL: exact paused preview-frame overlay is not present.")

    log = Path(log_path).read_text(encoding="utf-8", errors="replace")
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
    if not re.search(rf"TIMELINE_THUMBNAIL_READY sourceMs={sample_ms} ", log):
        raise SystemExit(f"FAIL: centered thumbnail sample {sample_ms}ms was not confirmed decoded.")

    from PIL import Image
    image = Image.open(screenshot_path).convert("RGB")

    # Sample a small normalized grid from every visible thumbnail. The emulator
    # fixture changes over time, so repeated fingerprints across distinct sample
    # timestamps expose a decoder that is returning one GOP/keyframe repeatedly.
    thumbnail_nodes = []
    for node in root.iter("node"):
        match = re.fullmatch(
            r"Klip karesi @(\d+)ms(?: \(önbellek\))?",
            node.attrib.get("content-desc", "")
        )
        box = bounds(node)
        if match and box and box[2] - box[0] >= 8 and box[3] - box[1] >= 8:
            thumbnail_nodes.append((int(match.group(1)), box))
    if len(thumbnail_nodes) < 3:
        raise SystemExit(
            f"FAIL: only {len(thumbnail_nodes)} visible timestamped thumbnails; "
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

    preview_x = (preview[0] + preview[2]) // 2
    preview_y = (preview[1] + preview[3]) // 2
    thumb_box = cell[1]
    thumb_x = (thumb_box[0] + thumb_box[2]) // 2
    thumb_y = (thumb_box[1] + thumb_box[3]) // 2

    # Derive the source aspect ratio from the fitted thumbnail cell instead of
    # assuming Shorts. This keeps the preview comparison valid for portrait,
    # landscape and square source videos.
    cell_width = thumb_box[2] - thumb_box[0]
    cell_height = thumb_box[3] - thumb_box[1]
    source_aspect = cell_width / max(1, cell_height)
    preview_frame_width = min(
        preview[2] - preview[0],
        round((preview[3] - preview[1]) * source_aspect)
    )
    thumbnail_frame_width = min(
        cell_width,
        round(cell_height * source_aspect)
    )
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
    elif len(sys.argv) == 5 and sys.argv[1] == "verify":
        verify(sys.argv[2], sys.argv[3], sys.argv[4])
    else:
        raise SystemExit(
            "Usage: assert-thumbnail-alignment.py prepare <editor.xml> | "
            "verify <aligned.xml> <screenshot.png> <logcat.txt>"
        )

#!/usr/bin/env python3
"""Small screenshot reader backed by the FFmpeg/ffprobe binaries used by CI.

This intentionally implements only the RGB operations used by the emulator
assertions. It avoids a Python wheel/system package download on CI runners.
"""
from __future__ import annotations

import subprocess
from pathlib import Path


class _Resampling:
    BICUBIC = "bicubic"


class _Image:
    Resampling = _Resampling

    def __init__(self, width: int, height: int, pixels: bytes):
        if width <= 0 or height <= 0 or len(pixels) != width * height * 3:
            raise ValueError(f"Invalid RGB image payload: {width}x{height}, {len(pixels)} bytes")
        self.width = width
        self.height = height
        self.size = (width, height)
        self._pixels = pixels

    @classmethod
    def open(cls, path: str | Path) -> "_Image":
        source = str(path)
        probe = subprocess.run(
            ["ffprobe", "-v", "error", "-select_streams", "v:0",
             "-show_entries", "stream=width,height", "-of", "csv=s=x:p=0", source],
            check=True, capture_output=True, text=True,
        ).stdout.strip().splitlines()
        if not probe or "x" not in probe[0]:
            raise ValueError(f"Could not read screenshot dimensions: {source}")
        width, height = map(int, probe[0].split("x", 1))
        decoded = subprocess.run(
            ["ffmpeg", "-v", "error", "-i", source, "-frames:v", "1",
             "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1"],
            check=True, capture_output=True,
        ).stdout
        return cls(width, height, decoded)

    def convert(self, mode: str) -> "_Image":
        if mode != "RGB":
            raise ValueError(f"ci_image supports RGB only, not {mode!r}")
        return self

    def getpixel(self, point: tuple[int, int]) -> tuple[int, int, int]:
        x, y = point
        if x < 0 or y < 0 or x >= self.width or y >= self.height:
            raise IndexError(f"Pixel outside image: {(x, y)} for {self.size}")
        offset = (y * self.width + x) * 3
        return tuple(self._pixels[offset:offset + 3])

    def getdata(self):
        for offset in range(0, len(self._pixels), 3):
            yield tuple(self._pixels[offset:offset + 3])

    def crop(self, box: tuple[int, int, int, int]) -> "_Image":
        left, top, right, bottom = box
        left, top = max(0, left), max(0, top)
        right, bottom = min(self.width, right), min(self.height, bottom)
        if right <= left or bottom <= top:
            raise ValueError(f"Empty image crop: {box} from {self.size}")
        rows = [
            self._pixels[(y * self.width + left) * 3:(y * self.width + right) * 3]
            for y in range(top, bottom)
        ]
        return _Image(right - left, bottom - top, b"".join(rows))

    def resize(self, size: tuple[int, int], resampling=None) -> "_Image":
        width, height = size
        if width <= 0 or height <= 0:
            raise ValueError(f"Invalid resize target: {size}")
        result = subprocess.run(
            ["ffmpeg", "-v", "error", "-f", "rawvideo", "-pix_fmt", "rgb24",
             "-s", f"{self.width}x{self.height}", "-i", "pipe:0", "-frames:v", "1",
             "-vf", f"scale={width}:{height}:flags=bicubic",
             "-f", "rawvideo", "-pix_fmt", "rgb24", "pipe:1"],
            input=self._pixels, check=True, capture_output=True,
        ).stdout
        return _Image(width, height, result)

    def tobytes(self) -> bytes:
        return self._pixels


class Image:
    Resampling = _Resampling
    open = _Image.open

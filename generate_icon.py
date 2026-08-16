#!/usr/bin/env python3
"""Generate Couch Controls' mod menu icon: a gamepad, drawn rather than cut.

Every other icon generator in the suite lifts its pixels out of the vanilla
Minecraft jar, because every other mod is about something vanilla already
draws. A controller is not: vanilla has no gamepad texture and nothing that
reads as one, so this one is drawn from shapes. Everything else follows the
house pattern -- pure stdlib PNG writer (zlib + struct), no Pillow, nearest
neighbour only, and deterministic: re-running produces identical bytes.

Usage: python3 generate_icon.py
"""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/client/resources/assets/couch-controls/icon.png")

SIZE = 128

CLEAR = (0, 0, 0, 0)

# Minecraft's UI greys, so the icon sits beside vanilla's own without
# announcing itself as from somewhere else.
BODY = (60, 62, 68, 255)
BODY_LIT = (86, 89, 97, 255)
BODY_DARK = (38, 39, 44, 255)
OUTLINE = (22, 23, 26, 255)

# The four face buttons keep Minecraft's own accent colours rather than any
# console's, since the layout is positional and deliberately not branded.
FACE = (150, 156, 168, 255)
STICK = (30, 31, 35, 255)
STICK_LIT = (72, 75, 82, 255)


def rounded_rect(px, x0, y0, x1, y1, radius, color):
    for y in range(max(0, y0), min(SIZE, y1 + 1)):
        for x in range(max(0, x0), min(SIZE, x1 + 1)):
            dx = 0
            dy = 0
            if x < x0 + radius:
                dx = x0 + radius - x
            elif x > x1 - radius:
                dx = x - (x1 - radius)
            if y < y0 + radius:
                dy = y0 + radius - y
            elif y > y1 - radius:
                dy = y - (y1 - radius)
            if dx * dx + dy * dy <= radius * radius:
                px[y][x] = color


def disc(px, cx, cy, radius, color):
    for y in range(max(0, cy - radius), min(SIZE, cy + radius + 1)):
        for x in range(max(0, cx - radius), min(SIZE, cx + radius + 1)):
            dx = x - cx
            dy = y - cy
            if dx * dx + dy * dy <= radius * radius:
                px[y][x] = color


def outline(px, color):
    """One-pixel border wherever an opaque pixel touches a transparent one.

    Drawn last and read from a copy, so the border traces the finished
    silhouette instead of growing into itself as it goes.
    """
    snapshot = [row[:] for row in px]
    for y in range(SIZE):
        for x in range(SIZE):
            if snapshot[y][x][3] != 0:
                continue
            for ny, nx in ((y - 1, x), (y + 1, x), (y, x - 1), (y, x + 1)):
                if 0 <= ny < SIZE and 0 <= nx < SIZE and snapshot[ny][nx][3] != 0:
                    px[y][x] = color
                    break


def build():
    px = [[CLEAR] * SIZE for _ in range(SIZE)]

    # Grips first, then the body over them, so the two read as one shell.
    disc(px, 34, 84, 22, BODY)
    disc(px, 94, 84, 22, BODY)
    rounded_rect(px, 20, 44, 108, 88, 16, BODY)

    # A lit top edge and a dark underside: enough to look moulded at 128px,
    # without a gradient that would band once the launcher scales it down.
    rounded_rect(px, 24, 46, 104, 54, 8, BODY_LIT)
    rounded_rect(px, 26, 84, 102, 92, 8, BODY_DARK)

    # Shoulders, peeking over the top edge.
    rounded_rect(px, 30, 36, 52, 48, 6, BODY_DARK)
    rounded_rect(px, 76, 36, 98, 48, 6, BODY_DARK)

    # D-pad: the cross, left, where a thumb rests.
    rounded_rect(px, 32, 62, 52, 70, 3, STICK)
    rounded_rect(px, 38, 56, 46, 76, 3, STICK)

    # Four face buttons, right, in the diamond every pad shares.
    disc(px, 92, 58, 6, FACE)
    disc(px, 104, 68, 6, FACE)
    disc(px, 92, 78, 6, FACE)
    disc(px, 80, 68, 6, FACE)

    # The sticks: the whole point of the mod, so they sit dead centre.
    disc(px, 58, 78, 11, STICK)
    disc(px, 58, 78, 7, STICK_LIT)
    disc(px, 78, 92, 11, STICK)
    disc(px, 78, 92, 7, STICK_LIT)

    outline(px, OUTLINE)
    return px


def write_png(path, pixels):
    """pixels: rows of RGBA tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))


if __name__ == "__main__":
    write_png(OUT, build())

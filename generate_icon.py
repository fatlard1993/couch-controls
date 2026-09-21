#!/usr/bin/env python3
"""Generate Couch Controls' mod menu icon: a gamepad as a vanilla item sprite.

Every other icon generator in the suite lifts its pixels out of the vanilla
Minecraft jar, because every other mod is about something vanilla already
draws. A controller is not, so this one is hand-placed on the same 16x16 grid
an item texture uses and scaled 8x like the rest. Pure stdlib PNG writer
(zlib + struct), nearest neighbour only, deterministic.

Usage: python3 generate_icon.py
"""

import os
import struct
import zlib

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/client/resources/assets/couch-controls/icon.png")

PALETTE = {
    ".": (0, 0, 0, 0),
    "O": (24, 24, 28, 255),
    "L": (132, 134, 140, 255),
    "B": (98, 100, 106, 255),
    "D": (66, 67, 72, 255),
    "d": (40, 41, 46, 255),
    "s": (86, 88, 94, 255),
    "R": (218, 28, 16, 255),
    "G": (23, 221, 98, 255),
    "U": (40, 84, 186, 255),
    "Y": (250, 222, 58, 255),
}

# Lit from the top left, shaded to the bottom right, as vanilla items are.
# d-pad left, face buttons right in redstone, emerald, lapis and gold.
SPRITE = """
................
................
...OOO....OOO...
..ODDDOOOODDDO..
.OLLLLLLLLLLLLO.
OLBBdBBBBBBBYBDO
OLBdddBBBBBRBGDO
OLBBdBBBBBBBUBDO
OLBBBsdBBsdBBBDO
OBBBBddBBddBBBDO
OBBBBBDDDDBBBDDO
OBBBDOOOOOODBDDO
OBBDO......ODDDO
.OOO........OOO.
................
................
"""


def sprite():
    rows = SPRITE.strip("\n").split("\n")
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), "item sprites are 16x16"
    return [[PALETTE[c] for c in row] for row in rows]


def scale(pixels, n):
    return [[px for px in row for _ in range(n)] for row in pixels for _ in range(n)]


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
    icon = scale(sprite(), 8)
    assert len(icon) == 128 and len(icon[0]) == 128, "mod menu icons are 128x128"
    write_png(OUT, icon)

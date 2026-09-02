#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
VoidReturn / 虚空回溯 - Minecraft pixel-art plugin icon generator.

The base art is hand-authored on a 32x32 grid (classic sprite resolution) and
scaled with NEAREST-NEIGHBOR to higher resolutions so every "pixel" stays a
crisp, hard-edged block -- the authentic Minecraft look.

Concept: a dark void disc (obsidian rim -> deep purple void + stars) with a
circular cyan "return" arrow wrapping a Minecraft player head (Steve) at the
center. => player + void + return, in one mark.

No third-party dependencies (pure stdlib PNG writer).
"""

import math
import os
import struct
import zlib

# ---------------------------------------------------------------------------
# Palette (Minecraft-ish flat shading: 1 highlight / 1 base / 1 shadow per material)
# ---------------------------------------------------------------------------
OBS   = (14, 10, 22)     # obsidian rim
VMID  = (29, 17, 64)     # void mid band
VIN   = (40, 22, 81)     # void inner band
STAR  = (216, 196, 255)  # star (pale violet)
STAR2 = (255, 255, 255)  # bright star
ARR_H = (127, 233, 216)  # arrow highlight (left/top)
ARR_B = (52, 201, 176)   # arrow base (teal)
ARR_S = (31, 143, 126)   # arrow shadow (right/bottom)
RIM   = (8, 5, 14)       # dark outline

# Steve face palette
HAIR  = (58, 43, 28)     # hair / beard
SKIN  = (197, 140, 85)   # skin base
SKINH = (232, 184, 125)  # skin highlight (top-left)
SKINS = (141, 94, 55)    # skin shadow (bottom-right)
EYEW  = (255, 255, 255)  # eye white
PUPIL = (58, 90, 204)    # eye pupil (blue)
MOUTH = (48, 38, 28)     # mouth

GRID = 32
CX = CY = 15.5


def dist(x, y):
    return math.hypot(x - CX, y - CY)


def ang_deg(x, y):
    # degrees, y-down screen space: 0=right, 90=down, -90=up(top)
    return math.degrees(math.atan2(y - CY, x - CX))


def put(grid, x, y, col, a=255):
    if 0 <= x < GRID and 0 <= y < GRID:
        grid[y][x] = (col[0], col[1], col[2], a)


def build_base():
    grid = [[None] * GRID for _ in range(GRID)]

    # --- disc background: bands by radius ---
    for y in range(GRID):
        for x in range(GRID):
            r = dist(x, y)
            if r > 15.0:
                continue  # transparent corners
            if r > 14.0:
                put(grid, x, y, RIM)
            elif r > 12.5:
                put(grid, x, y, OBS)
            elif r > 8.0:
                put(grid, x, y, VMID)
            else:
                put(grid, x, y, VIN)

    # --- stars (rim / top gap where they won't be overwritten by arrow/face) ---
    for (sx, sy, sc) in [(7, 6, STAR2), (25, 6, STAR), (6, 25, STAR),
                         (25, 25, STAR2), (15, 5, STAR)]:
        put(grid, sx, sy, sc)

    # --- circular "return" arrow (arc with a top gap) ---
    # arrow wraps around the player head at the rim side
    gap_lo, gap_hi = -112, -68  # gap centered at top (-90), 44 deg wide
    for y in range(GRID):
        for x in range(GRID):
            r = dist(x, y)
            if 10.0 <= r <= 11.5:
                a = ang_deg(x, y)
                if gap_lo <= a <= gap_hi:
                    continue
                nx = (x - CX) / 11.5
                ny = (y - CY) / 11.5
                s = nx + ny
                if s < -0.2:
                    col = ARR_H
                elif s > 0.35:
                    col = ARR_S
                else:
                    col = ARR_B
                put(grid, x, y, col)

    # --- arrowhead in the top gap, pointing UP (return from the void) ---
    # larger chevron, clearly reads as an arrowhead at small sizes
    head = [
        (15, 3),
        (15, 4),
        (14, 5), (15, 5), (16, 5),
        (13, 6), (14, 6), (15, 6), (16, 6), (17, 6),
        (12, 7), (13, 7), (14, 7), (15, 7), (16, 7), (17, 7), (18, 7),
    ]
    for (hx, hy) in head:
        # shade by top-left highlight / bottom-right shadow
        nx = (hx - CX) / 6.0
        ny = (hy - CY) / 6.0
        s = nx + ny
        if s < -0.2:
            col = ARR_H
        elif s > 0.3:
            col = ARR_S
        else:
            col = ARR_B
        put(grid, hx, hy, col)

    # --- Minecraft Steve player head (12x12, centered) ---
    face_rows = [
        "HHHHHHHHHHHH",
        "HHHHHHHHHHHH",
        "HHSSSSSSSSHH",
        "HHSSSSSSSSHH",
        "HHSSSSSSSSHH",
        "SSSSWWSSWWSS",   # eye whites (cols 4-5, 8-9)
        "SSSSPWSSPWSS",   # pupils (cols 4, 8)
        "SSSSSSSSSSSS",
        "SSSSSSSSSSSS",
        "SSSSSSSSSSSS",
        "SSSSMMSSSSSS",   # mouth (cols 4-5)
        "SSSSSSSSSSSS",
    ]
    fx0, fy0 = 10, 10  # top-left of 12x12 face
    for r, row in enumerate(face_rows):
        for c, ch in enumerate(row):
            x, y = fx0 + c, fy0 + r
            # pick base colour
            if ch == 'H':
                base = HAIR
            elif ch == 'W':
                base = EYEW
            elif ch == 'P':
                base = PUPIL
            elif ch == 'M':
                base = MOUTH
            else:  # S skin
                base = SKIN
            # volume shading only for skin (keep most of the face in base skin)
            if ch == 'S':
                nx = (x - CX) / 6.0
                ny = (y - CY) / 6.0
                s = nx + ny
                if s < -0.45:
                    base = SKINH
                elif s > 0.55:
                    base = SKINS
            put(grid, x, y, base)

    return grid


# ---------------------------------------------------------------------------
# Nearest-neighbor scaling + PNG output (pure stdlib)
# ---------------------------------------------------------------------------
def scale_grid(grid, scale):
    w = GRID * scale
    out = []
    for Y in range(w):
        row = []
        for X in range(w):
            src = grid[Y // scale][X // scale]
            row.append(src if src is not None else (0, 0, 0, 0))
        out.append(row)
    return out


def write_png(path, pixels):
    w = len(pixels[0])
    h = len(pixels)
    raw = bytearray()
    for row in pixels:
        raw.append(0)  # filter type 0
        for (r, g, b, a) in row:
            raw += bytes((r & 255, g & 255, b & 255, a & 255))
    comp = zlib.compress(bytes(raw), 9)

    def chunk(typ, data):
        return (struct.pack(">I", len(data)) + typ + data +
                struct.pack(">I", zlib.crc32(typ + data) & 0xffffffff))

    with open(path, "wb") as f:
        f.write(b"\x89PNG\r\n\x1a\n")
        f.write(chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0)))
        f.write(chunk(b"IDAT", comp))
        f.write(chunk(b"IEND", b""))


def main():
    out_dir = os.path.dirname(os.path.abspath(__file__))
    base = build_base()
    sizes = [32, 64, 128, 256, 512]
    for s in sizes:
        px = scale_grid(base, s // GRID)
        path = os.path.join(out_dir, f"voidreturn_icon_{s}.png")
        write_png(path, px)
        print(f"wrote {path}")
    # also a square (no transparency) version for flat surfaces
    sq = [[ (col if col is not None else (8,5,14,255)) for col in row] for row in base]
    write_png(os.path.join(out_dir, "voidreturn_icon_square_512.png"),
              scale_grid(sq, 512 // GRID))
    print("wrote square version")


if __name__ == "__main__":
    main()

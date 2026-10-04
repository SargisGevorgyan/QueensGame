#!/usr/bin/env python3
"""Generates every app icon asset from one design.

The icon is a 5x5 Queens board (colour regions split by dark walls) with the
game's gold crown on top. Run from the repo root:

    python3 -m pip install cairosvg pillow
    python3 design/icon/generate.py
"""
import io
import os

import cairosvg
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT_SVG = os.path.join(ROOT, "design", "icon")
IOS_ICON = os.path.join(ROOT, "QueensGame", "Assets.xcassets", "AppIcon.appiconset", "AppIcon.png")
ANDROID_RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
WEB_DIR = os.path.join(ROOT, "queens")

N = 5
REGIONS = [
    "AAABB",
    "ACCBB",
    "CCDDB",
    "ECDDD",
    "EEEDD",
]
# Region pastels from the light theme (--z1..--z5), wall colour from --line-strong.
COLORS = {"A": "#F3C6C8", "B": "#B9D7F1", "C": "#F6E2A9", "D": "#C7E7C1", "E": "#DCCEF2"}
WALL = "#23263A"
GRIDLINE = "#23263A"
GOLD = "#EFB437"
GOLD_SHADE = "#D99A17"
INK = "#1B1D2A"
# The game's crown glyph (queens/index.html #i-crown), 20x20 viewBox.
CROWN = "M2 6.6 6.4 9.6 10 3.4 13.6 9.6 18 6.6 16.5 16.4a1 1 0 0 1-1 .85H4.5a1 1 0 0 1-1-.85z"


def board_shapes(edges, unit):
    """The board as (x, y, w, h, colour, alpha) rects.

    `edges` are the N+1 cell boundaries on each axis; `unit` is the side of
    the visible square, which sets the line weights.
    """
    lo, hi = edges[0], edges[-1]
    out = []
    for r in range(N):
        for k in range(N):
            out.append((edges[k], edges[r], edges[k + 1] - edges[k], edges[r + 1] - edges[r],
                        COLORS[REGIONS[r][k]], 1))
    thin, thick = unit * 0.006, unit * 0.022
    for p in edges[1:-1]:
        out.append((p - thin / 2, lo, thin, hi - lo, GRIDLINE, 0.18))
        out.append((lo, p - thin / 2, hi - lo, thin, GRIDLINE, 0.18))
    for r in range(N):
        for k in range(N):
            if k + 1 < N and REGIONS[r][k] != REGIONS[r][k + 1]:
                out.append((edges[k + 1] - thick / 2, edges[r] - thick / 2, thick,
                            edges[r + 1] - edges[r] + thick, WALL, 1))
            if r + 1 < N and REGIONS[r][k] != REGIONS[r + 1][k]:
                out.append((edges[k] - thick / 2, edges[r + 1] - thick / 2,
                            edges[k + 1] - edges[k] + thick, thick, WALL, 1))
    return out


def board(size):
    """Full-bleed board as SVG elements in a size x size box."""
    edges = [i * size / N for i in range(N + 1)]
    return [f'<rect x="{x:.3f}" y="{y:.3f}" width="{w:.3f}" height="{h:.3f}" fill="{c}" fill-opacity="{a:g}"/>'
            for x, y, w, h, c, a in board_shapes(edges, size)]


def crown(cx, cy, width, mono=None):
    """The crown centred at (cx, cy), `width` wide, with an ink outline."""
    s = width / 16.0  # glyph spans x 2..18
    tx, ty = cx - 10 * s, cy - 9.9 * s  # glyph spans y 3.4..17.25
    sw = 1.15
    if mono:
        return [f'<g transform="translate({tx:.3f} {ty:.3f}) scale({s:.4f})">'
                f'<path d="{CROWN}" fill="{mono}"/></g>']
    return [
        f'<g transform="translate({tx:.3f} {ty + 0.9*s:.3f}) scale({s:.4f})">'
        f'<path d="{CROWN}" fill="{INK}" fill-opacity="0.35" stroke="{INK}" stroke-opacity="0.35" stroke-width="{sw}" stroke-linejoin="round"/></g>',
        f'<g transform="translate({tx:.3f} {ty:.3f}) scale({s:.4f})">'
        f'<path d="{CROWN}" fill="{GOLD}" stroke="{INK}" stroke-width="{sw}" stroke-linejoin="round"/>'
        f'<path d="M3.9 13.2H16.1" stroke="{GOLD_SHADE}" stroke-width="1.1" stroke-linecap="round"/>'
        f'<circle cx="10" cy="3.4" r="1.25" fill="#FFFFFF" stroke="{INK}" stroke-width="0.8"/>'
        f'<circle cx="2" cy="6.6" r="1.05" fill="#FFFFFF" stroke="{INK}" stroke-width="0.75"/>'
        f'<circle cx="18" cy="6.6" r="1.05" fill="#FFFFFF" stroke="{INK}" stroke-width="0.75"/>'
        "</g>",
    ]


def svg(size, body, rounded=0):
    clip = ""
    if rounded:
        clip = f'<clipPath id="r"><rect width="{size}" height="{size}" rx="{rounded}"/></clipPath>'
        body = [f'<g clip-path="url(#r)">'] + body + ["</g>"]
    return (f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}">'
            f'<defs>{clip}</defs>' + "".join(body) + "</svg>\n")


def full_icon(size=1024, rounded=0):
    # The crown fills ~58% of the square so it reads on a home screen.
    return svg(size, board(size) + crown(size / 2, size / 2, size * 0.58), rounded)


def png(svg_text, size, alpha=True):
    data = cairosvg.svg2png(bytestring=svg_text.encode(), output_width=size, output_height=size)
    im = Image.open(io.BytesIO(data))
    return im.convert("RGBA" if alpha else "RGB")


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(content)


def android_vector(body_xml, comment):
    return ('<?xml version="1.0" encoding="utf-8"?>\n'
            f"<!-- {comment} Generated by design/icon/generate.py. -->\n"
            '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
            '    android:width="108dp"\n    android:height="108dp"\n'
            '    android:viewportWidth="108"\n    android:viewportHeight="108">\n'
            + body_xml + "</vector>\n")


def android_board():
    # Launchers show only the middle 72dp of the 108dp layer, so lay the 5x5
    # grid out over that window (matching the iOS icon) and stretch the outer
    # cells to the canvas edge.
    edges = [0] + [18 + i * 72 / N for i in range(1, N)] + [108]
    paths = {}
    for x, y, w, h, c, a in board_shapes(edges, 72):
        paths.setdefault((c, a), []).append(f"M{x:.3f},{y:.3f}h{w:.3f}v{h:.3f}h{-w:.3f}z")
    out = ""
    for (c, a), d in paths.items():
        alpha = "" if a == 1 else f' android:fillAlpha="{a:g}"'
        out += f'    <path android:fillColor="{c}"{alpha} android:pathData="{"".join(d)}" />\n'
    return out


def android_crown(mono=False):
    # 46% of the 108dp canvas, centred, so the jewels and base corners stay
    # inside the 66dp safe circle that round launcher masks keep.
    s = 108 * 0.46 / 16.0
    tx, ty = 54 - 10 * s, 54 - 9.9 * s
    g = lambda dy, inner: (f'    <group android:translateX="{tx:.3f}" android:translateY="{ty + dy:.3f}" '
                           f'android:scaleX="{s:.4f}" android:scaleY="{s:.4f}">\n{inner}    </group>\n')
    if mono:
        return g(0, f'        <path android:fillColor="#FFFFFFFF" android:pathData="{CROWN}" />\n')
    shadow = (f'        <path android:fillColor="{INK}" android:fillAlpha="0.35" android:strokeColor="{INK}" '
              f'android:strokeAlpha="0.35" android:strokeWidth="1.15" android:strokeLineJoin="round" android:pathData="{CROWN}" />\n')
    body = (f'        <path android:fillColor="{GOLD}" android:strokeColor="{INK}" android:strokeWidth="1.15" '
            f'android:strokeLineJoin="round" android:pathData="{CROWN}" />\n'
            f'        <path android:strokeColor="{GOLD_SHADE}" android:strokeWidth="1.1" android:strokeLineCap="round" '
            f'android:pathData="M3.9,13.2H16.1" />\n')
    for cx, cy, rad, sw in ((10, 3.4, 1.25, 0.8), (2, 6.6, 1.05, 0.75), (18, 6.6, 1.05, 0.75)):
        body += (f'        <path android:fillColor="#FFFFFF" android:strokeColor="{INK}" android:strokeWidth="{sw}" '
                 f'android:pathData="M{cx - rad:g},{cy:g}a{rad:g},{rad:g} 0 1,0 {2*rad:g},0a{rad:g},{rad:g} 0 1,0 -{2*rad:g},0z" />\n')
    return g(0.9 * s, shadow) + g(0, body)


def main():
    master = full_icon()
    write(os.path.join(OUT_SVG, "icon.svg"), master)

    # iOS: one 1024 PNG, opaque (App Store rejects alpha).
    png(master, 1024, alpha=False).save(IOS_ICON, optimize=True)

    # Android adaptive layers (minSdk is 26, so no legacy PNG mipmaps are needed).
    drawable = os.path.join(ANDROID_RES, "drawable")
    write(os.path.join(drawable, "ic_launcher_background.xml"),
          android_vector(android_board(), "Queens board: colour regions split by walls."))
    write(os.path.join(drawable, "ic_launcher_foreground.xml"),
          android_vector(android_crown(), "The game's gold crown, inside the 66dp safe zone."))
    write(os.path.join(drawable, "ic_launcher_monochrome.xml"),
          android_vector(android_crown(mono=True), "Crown silhouette for themed icons."))

    # Web: SVG favicon, PNG fallback and Apple touch icon.
    write(os.path.join(WEB_DIR, "favicon.svg"), full_icon(1024, rounded=1024 * 0.2))
    png(full_icon(1024, rounded=1024 * 0.2), 32).save(os.path.join(WEB_DIR, "favicon-32.png"), optimize=True)
    png(master, 180, alpha=False).save(os.path.join(WEB_DIR, "apple-touch-icon.png"), optimize=True)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""
Builds the app's Google Sans fonts and the wordmark banner from the official variable font.

    make fonts SRC=/path/to/GoogleSans[GRAD,opsz,wght].ttf

The upstream file (google/fonts, ofl/googlesans) is ~5 MB and covers many scripts. The app
only needs two Latin weights, so this pins Regular (400) and Medium (500) and subsets them to
Latin + common punctuation; anything else (e.g. Devanagari app names) falls back to the system
font per glyph. It also outlines the "zyft" wordmark (Medium, -2 % tracking, per the design)
into the 320x180 TV banner so no font is needed to draw it.

Requires: pip install fonttools
"""
from __future__ import annotations  # PEP 604 annotations on Python 3.9 (macOS system python)

import argparse
import pathlib
import sys

from fontTools import subset
from fontTools.pens.boundsPen import BoundsPen
from fontTools.pens.svgPathPen import SVGPathPen
from fontTools.pens.transformPen import TransformPen
from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

ROOT = pathlib.Path(__file__).resolve().parents[2]
ASSETS = ROOT / "app/src/main/assets"
BANNER = ROOT / "app/src/main/res/drawable/banner.xml"
HEADER_LOGO = ROOT / "app/src/main/res/drawable/logo_lockup.xml"

WEIGHTS = {"GoogleSans-Regular.ttf": 400, "GoogleSans-Medium.ttf": 500}

# Basic Latin, Latin-1, Latin Extended-A, general punctuation (dashes, quotes, bullets,
# ellipsis), euro and rupee signs: covers the UI strings and most app names.
UNICODES = "U+0020-007E,U+00A0-00FF,U+0100-017F,U+2010-2027,U+2030-203A,U+20AC,U+20B9"

# Banner geometry from design/Zyft Logo.dc.html: 320x180, mark 56 + 16 gap + wordmark at 52 px.
BANNER_W, BANNER_H = 320, 180
MARK_SIZE, GAP, WORD_PX, TRACKING_EM, LIFT_PX = 56, 16, 52, -0.02, 3
# Rendered height of the header lockup; the 32 dp header row centres it.
HEADER_LOGO_DP = 22
WORD = "zyft"


def build_weights(src: pathlib.Path) -> dict[int, TTFont]:
    out = {}
    for name, weight in WEIGHTS.items():
        # updateFontNames renames the instance (e.g. "Medium") from the STAT table.
        font = instancer.instantiateVariableFont(
            TTFont(src), {"wght": weight, "GRAD": 0, "opsz": None}, updateFontNames=True,
        )
        options = subset.Options()
        options.layout_features = ["kern", "liga", "calt", "ccmp", "locl", "mark", "mkmk"]
        options.name_IDs = ["*"]  # keep copyright and license entries, required by the OFL
        options.notdef_outline = True
        subsetter = subset.Subsetter(options)
        subsetter.populate(unicodes=subset.parse_unicodes(UNICODES))
        subsetter.subset(font)
        path = ASSETS / "fonts" / name
        path.parent.mkdir(parents=True, exist_ok=True)
        # Keep the source font's timestamp so rebuilding unchanged fonts gives identical files.
        font.recalcTimestamp = False
        font.save(path)
        out[weight] = TTFont(path)
        print(f"{path.relative_to(ROOT)}: {path.stat().st_size // 1024} KB")
    return out


def wordmark_path(font: TTFont, scale: float, x0: float, baseline: float) -> tuple[str, float, tuple]:
    """Outline WORD with -2 % tracking; returns (SVG path data, advance width, ink bounds xmin,ymin,xmax,ymax)."""
    glyphs = font.getGlyphSet()
    cmap = font.getBestCmap()
    hmtx = font["hmtx"]
    units = font["head"].unitsPerEm
    pen = SVGPathPen(glyphs, ntos=lambda v: f"{v:.2f}".rstrip("0").rstrip("."))
    bounds = BoundsPen(glyphs)
    x = 0.0
    for ch in WORD:
        name = cmap[ord(ch)]
        # Font units are y-up; vector drawables are y-down.
        transform = (scale, 0, 0, -scale, x0 + x * scale, baseline)
        glyphs[name].draw(TransformPen(pen, transform))
        glyphs[name].draw(TransformPen(bounds, transform))
        x += hmtx[name][0] + TRACKING_EM * units
    x -= TRACKING_EM * units  # no tracking after the last letter
    return pen.getCommands(), x * scale, bounds.bounds


# Mark option A in its 48-unit grid: ring (stroke 3) and inner tile, plus its visible ink box.
MARK_PATHS = """        <path android:strokeColor="@color/accent" android:strokeWidth="3"
            android:pathData="M11.5,10.5 H36.5 A8,8 0 0 1 44.5,18.5 V29.5 A8,8 0 0 1 36.5,37.5 H11.5 A8,8 0 0 1 3.5,29.5 V18.5 A8,8 0 0 1 11.5,10.5 Z" />
        <path android:fillColor="@color/text_primary"
            android:pathData="M13,17 H35 A3,3 0 0 1 38,20 V28 A3,3 0 0 1 35,31 H13 A3,3 0 0 1 10,28 V20 A3,3 0 0 1 13,17 Z" />"""
MARK_INK = (2, 9, 46, 39)  # ring outer edge: 3.5 - 1.5 .. 44.5 + 1.5, 10.5 - 1.5 .. 37.5 + 1.5


def lockup(medium: TTFont, left: float, box_top: float, box_height: float) -> dict:
    """Mark + gap + wordmark laid out like the design: mark in a MARK_SIZE box, wordmark's
    CSS line box centred on the same box and nudged up by LIFT_PX."""
    scale = WORD_PX / medium["head"].unitsPerEm
    hhea = medium["hhea"]
    line_box = (hhea.ascent - hhea.descent) * scale
    baseline = box_top + (box_height - line_box) / 2 + hhea.ascent * scale - LIFT_PX
    word, word_width, (_, wy0, _, wy1) = wordmark_path(medium, scale, left + MARK_SIZE + GAP, baseline)
    mark_scale = MARK_SIZE / 48
    mark_y = box_top + (box_height - MARK_SIZE) / 2
    ink_top = min(mark_y + MARK_INK[1] * mark_scale, wy0)
    ink_bottom = max(mark_y + MARK_INK[3] * mark_scale, wy1)
    return dict(word=word, width=MARK_SIZE + GAP + word_width, mark_x=left, mark_y=mark_y,
                mark_scale=mark_scale, ink_top=ink_top, ink_bottom=ink_bottom)


def vector(width: float, height: float, body: str, comment: str, dp_height: float | None = None) -> str:
    dp_w, dp_h = (width, height) if dp_height is None else (width * dp_height / height, dp_height)
    return f"""<?xml version="1.0" encoding="utf-8"?>
<!-- GENERATED by scripts/fonts/build_fonts.py; do not edit by hand. -->
<!-- {comment} -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="{dp_w:.2f}dp" android:height="{dp_h:.2f}dp"
    android:viewportWidth="{width:.2f}" android:viewportHeight="{height:.2f}">
{body}
</vector>
"""


def lockup_body(l: dict, dy: float = 0) -> str:
    return f"""    <group android:translateY="{dy:.2f}">
        <group android:translateX="{l['mark_x']:.2f}" android:translateY="{l['mark_y']:.2f}" android:scaleX="{l['mark_scale']:.4f}" android:scaleY="{l['mark_scale']:.4f}">
{MARK_PATHS}
        </group>
        <path android:fillColor="@color/text_primary" android:pathData="{l['word']}" />
    </group>"""


def write_banner(medium: TTFont) -> None:
    probe = lockup(medium, 0, 0, BANNER_H)
    l = lockup(medium, (BANNER_W - probe["width"]) / 2, 0, BANNER_H)
    body = f'    <path android:fillColor="@color/brand_midnight" android:pathData="M0,0 H{BANNER_W} V{BANNER_H} H0 Z" />\n' + lockup_body(l)
    BANNER.write_text(vector(BANNER_W, BANNER_H, body,
        f'Android TV banner, 320x180: Zyft mark (option A) + "{WORD}" wordmark in Google Sans Medium, outlined.'))
    print(f"{BANNER.relative_to(ROOT)}: lockup {l['width']:.1f} px wide")


def write_header_logo(medium: TTFont) -> None:
    """Same lockup on a transparent background, cropped to its ink so it aligns in the header."""
    l = lockup(medium, 0, 0, MARK_SIZE)
    height = l["ink_bottom"] - l["ink_top"]
    HEADER_LOGO.write_text(vector(l["width"], height, lockup_body(l, dy=-l["ink_top"]),
        f'Home header logo: Zyft mark (option A) + "{WORD}" wordmark, transparent, cropped to ink.',
        dp_height=HEADER_LOGO_DP))
    print(f"{HEADER_LOGO.relative_to(ROOT)}: {l['width']:.1f} x {height:.1f} units")


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("src", type=pathlib.Path, help="GoogleSans[GRAD,opsz,wght].ttf from google/fonts")
    parser.add_argument("license", type=pathlib.Path, help="OFL.txt shipped next to the font")
    args = parser.parse_args()
    if not args.src.is_file() or not args.license.is_file():
        parser.error("font or license file not found")
    weights = build_weights(args.src)
    (ASSETS / "fonts" / "GoogleSans-OFL.txt").write_bytes(args.license.read_bytes())
    write_banner(weights[500])
    write_header_logo(weights[500])
    return 0


if __name__ == "__main__":
    sys.exit(main())

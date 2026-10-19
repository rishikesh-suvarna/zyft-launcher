#!/usr/bin/env python3
"""
Writes the README logos (docs/logo-dark.svg, docs/logo-light.svg) from the generated header
lockup drawable, so the docs and the app always show the same outlines.

    python3 scripts/fonts/readme_logos.py

Standard library only: unlike build_fonts.py it doesn't need the source font. Colours follow
design/Zyft Logo.dc.html: lime ring and light glyphs on dark, all midnight ("mono on light").
"""
from __future__ import annotations  # PEP 604 annotations on Python 3.9 (macOS system python)

import pathlib
import re
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[2]
LOCKUP = ROOT / "app/src/main/res/drawable/logo_lockup.xml"
DOCS = ROOT / "docs"
ANDROID = "{http://schemas.android.com/apk/res/android}"

LIME, LIGHT, MIDNIGHT = "#C5F36A", "#F2F3F5", "#0E1220"
VARIANTS = {
    "logo-dark.svg": {"@color/accent": LIME, "@color/text_primary": LIGHT},
    "logo-light.svg": {"@color/accent": MIDNIGHT, "@color/text_primary": MIDNIGHT},
}
README_HEIGHT_PX = 48


def attr(el: ET.Element, name: str, default: str = "") -> str:
    return el.get(ANDROID + name, default)


def convert(el: ET.Element, colours: dict[str, str]) -> str:
    """Vector drawable group/path tree to SVG, resolving @color references per variant."""
    if el.tag == "group":
        transform = (f'translate({attr(el, "translateX", "0")} {attr(el, "translateY", "0")}) '
                     f'scale({attr(el, "scaleX", "1")} {attr(el, "scaleY", "1")})')
        return f'<g transform="{transform}">' + "".join(convert(c, colours) for c in el) + "</g>"
    if el.tag == "path":
        fill, stroke = attr(el, "fillColor"), attr(el, "strokeColor")
        paint = f'fill="{colours[fill]}"' if fill else "fill=\"none\""
        if stroke:
            paint += f' stroke="{colours[stroke]}" stroke-width="{attr(el, "strokeWidth", "1")}"'
        return f'<path {paint} d="{attr(el, "pathData")}"/>'
    raise ValueError(f"unsupported element <{el.tag}> in {LOCKUP.name}")


def main() -> int:
    vector = ET.parse(LOCKUP).getroot()
    w, h = float(attr(vector, "viewportWidth")), float(attr(vector, "viewportHeight"))
    width_px = round(w * README_HEIGHT_PX / h)
    for name, colours in VARIANTS.items():
        body = "".join(convert(c, colours) for c in vector)
        svg = (f'<svg xmlns="http://www.w3.org/2000/svg" width="{width_px}" height="{README_HEIGHT_PX}" '
               f'viewBox="0 0 {w} {h}" role="img" aria-label="Zyft">{body}</svg>\n')
        (DOCS / name).write_text(re.sub(r"\s+", " ", svg).strip() + "\n")
        print(f"docs/{name}: {width_px}x{README_HEIGHT_PX}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

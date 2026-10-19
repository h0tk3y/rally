#!/usr/bin/env python3
"""Renders the app's adaptive launcher icon (res/drawable/rally_bg.xml + rally_fg_zero.xml) to ../images/icon.png.

An adaptive icon is 108 dp, and launchers show its middle 72 dp: this crops to that area with rounded corners."""
import os, re, subprocess
import xml.etree.ElementTree as ET

HERE = os.path.dirname(os.path.abspath(__file__))
RES = os.path.join(HERE, "..", "..", "android", "src", "main", "res", "drawable")
A = "{http://schemas.android.com/apk/res/android}"


def color(c):
    """Android #AARRGGBB or #RRGGBB to an SVG color and opacity."""
    c = c.lstrip("#")
    if len(c) == 8:
        return "#" + c[2:], int(c[:2], 16) / 255
    return "#" + c, 1.0


def paths(name):
    root = ET.parse(os.path.join(RES, name)).getroot()
    vw, vh = float(root.get(A + "viewportWidth")), float(root.get(A + "viewportHeight"))
    out = []
    for p in root.iter("path"):
        fill = p.get(A + "fillColor")
        if not fill:
            continue
        rgb, alpha = color(fill)
        out.append(f'<path d="{p.get(A + "pathData")}" fill="{rgb}" fill-opacity="{alpha}"/>')
    return vw, vh, out


bw, bh, bg = paths("rally_bg.xml")
fw, fh, fg = paths("rally_fg_zero.xml")
size = fw  # both layers fill the same 108 dp square; draw in the foreground's viewport
inset = size * 18 / 108  # the launcher shows the middle 72 dp of 108
svg = f'''<svg xmlns="http://www.w3.org/2000/svg" viewBox="{inset} {inset} {size - 2 * inset} {size - 2 * inset}" width="512" height="512">
<defs><clipPath id="m"><rect x="{inset}" y="{inset}" width="{size - 2 * inset}" height="{size - 2 * inset}" rx="{(size - 2 * inset) * 0.22}"/></clipPath></defs>
<g clip-path="url(#m)">
<g transform="scale({size / bw} {size / bh})">{"".join(bg)}</g>
{"".join(fg)}
</g></svg>'''
svg_path = os.path.join(HERE, "tmp", "icon.svg")
os.makedirs(os.path.dirname(svg_path), exist_ok=True)
open(svg_path, "w").write(svg)
out = os.path.join(HERE, "..", "images", "icon.png")
subprocess.run(["rsvg-convert", "-w", "256", "-h", "256", "-o", out, svg_path], check=True)
print(out)

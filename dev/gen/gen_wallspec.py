#!/usr/bin/env python3
"""Copies TV Home's drawn-background recipes from the page (app/shared/web/js/home_walls.js, the source) into WallSpec.kt, which
the TV paints from. Run after editing a recipe; tests/home_walls.test.js fails while the two differ.   python3 dev/gen/gen_wallspec.py"""
import json, re, pathlib

root = pathlib.Path(__file__).resolve().parents[2]
js = (root / 'app/shared/web/js/home_walls.js').read_text()
data = json.loads(re.search(r'const HOME_ART = (\{.*?\n\});', js, re.S).group(1))
body = json.dumps(data, separators=(',', ':'))
(root / 'app/shared/src/home/kotlin/dev/tvremote/home/WallSpec.kt').write_text('''package dev.tvremote.home

/**
 * The recipes of TV Home's drawn backgrounds ([HomeConfig.ART]), as data: the TV paints them (app/home WallArt.kt) and the page
 * draws its small previews from the same numbers (js/home_walls.js, which also says what each field means). The two copies are kept
 * equal by tests/home_walls.test.js, so a preview is the picture the TV shows. Made by dev/gen/gen_wallspec.py: edit the page's, not this.
 */
object WallSpec {
    const val JSON = """%s"""
}
''' % body)
print(len(body), 'bytes of recipes')

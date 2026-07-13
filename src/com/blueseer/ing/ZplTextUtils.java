/*
The MIT License (MIT)

Copyright (c) Terry Evans Vaughn

All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.blueseer.ing;

import java.awt.Font;
import java.awt.font.FontRenderContext;
import java.util.ArrayList;
import java.util.List;

/**
 * Shared ZPL text-layout helpers - real-font-metric word wrap and plain field
 * emission - originally written for {@link IngredientLabelEngine}'s bold
 * ingredient list and now reused by {@link NutritionLabelEngine}'s nutrition
 * table and by the storage/usage instructions splice in {@code OVData}, so
 * all three dynamic label sections wrap text the same way instead of
 * depending on the printer/renderer's own {@code ^FB} auto-wrap (which a
 * label preview library was found not to handle correctly for short blocks -
 * see the git history for the itemFIC.prn storage/usage overlap this
 * replaced).
 */
public final class ZplTextUtils {

    private ZplTextUtils() {
    }

    /** The rendered ZPL for one dynamic label section, plus the Y just past its last line - so the
     *  next section down can start right there instead of at a fixed template guess, eliminating
     *  dead whitespace when a section renders shorter than the template author assumed. */
    public record Rendered(String zpl, int endY) {
    }

    // Measures text at a given ZPL font height using a real font's metrics (java.awt.Font/
    // FontRenderContext work headlessly - no display needed) rather than a flat per-character
    // ratio, which was producing visibly overlapping words. This still only approximates whatever
    // font the physical printer actually has resident, but tracks a real font's varying glyph
    // widths (a "W" isn't the same width as an "i") instead of a single guessed average.
    private static final FontRenderContext MEASURE_FRC = new FontRenderContext(null, true, true);

    public static double measureWidth(String text, int fontHeight) {
        Font font = new Font(Font.SANS_SERIF, Font.PLAIN, fontHeight);
        return font.getStringBounds(text, MEASURE_FRC).getWidth();
    }

    /** Simple word-wrap for plain (non-bold) text, using real font measurement against a pixel/dot width. */
    public static List<String> wrapPlain(String text, int fontHeight, int width) {
        List<String> lines = new ArrayList<>();
        String[] words = text.split(" ");
        StringBuilder cur = new StringBuilder();
        for (String w : words) {
            String candidate = cur.length() == 0 ? w : cur + " " + w;
            if (measureWidth(candidate, fontHeight) > width && cur.length() > 0) {
                lines.add(cur.toString());
                cur = new StringBuilder(w);
            } else {
                cur = new StringBuilder(candidate);
            }
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }

    public static void appendZplField(StringBuilder zpl, int x, int y, int fontHeight, String text, boolean bold) {
        String escaped = zplEscape(text);
        zpl.append("^FO").append(x).append(",").append(y)
                .append("^A0N,").append(fontHeight).append(",").append(fontHeight)
                .append("^FD").append(escaped).append("^FS");
        if (bold) {
            // double-strike one dot down-and-right to thicken the strokes
            zpl.append("^FO").append(x + 1).append(",").append(y + 1)
                    .append("^A0N,").append(fontHeight).append(",").append(fontHeight)
                    .append("^FD").append(escaped).append("^FS");
        }
    }

    /** A horizontal divider rule spanning {@code width} dots, e.g. between two major label sections. */
    public static void appendDivider(StringBuilder zpl, int x, int y, int width) {
        zpl.append("^FO").append(x).append(",").append(y).append("^GB").append(width).append(",2,2^FS");
    }

    /** A vertical divider rule of the given height, e.g. a table's column separator. */
    public static void appendVerticalDivider(StringBuilder zpl, int x, int y, int height) {
        zpl.append("^FO").append(x).append(",").append(y).append("^GB2,").append(height).append(",2^FS");
    }

    public static String zplEscape(String s) {
        // ^ and ~ are ZPL command-prefix characters; strip rather than risk corrupting
        // the command stream if either ever appears in ingredient/nutrition/instruction text.
        return s.replace("^", "").replace("~", "");
    }
}

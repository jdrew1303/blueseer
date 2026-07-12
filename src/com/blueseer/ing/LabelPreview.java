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

import com.zpl2pdf.ZPLConfig;
import com.zpl2pdf.ZPLRenderer;

import java.io.File;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders a ZPL label to a temporary PDF for on-screen preview before
 * committing to a physical print - so a mistake (wrong lot number, a typo
 * in the recipe, a misencoded barcode) costs a look at a screen instead of
 * a wasted label. Uses the JavaZPL2PDF library (MIT, Maven Central
 * io.github.varunsermaldev:JavaZPL2PDF), verified against BlueSeer's own
 * generated ZPL - including the bold double-strike allergen trick, ^GB
 * divider lines, ^FB word-wrap, and the ^BC barcode with its interpretation
 * line - before adopting it here.
 *
 * This is preview-only. The physical print path (OVData.printLabelItem)
 * does not depend on this class or on JavaZPL2PDF at all, so a bug or
 * limitation in this rendering library can never affect what actually gets
 * printed on a real label.
 */
public class LabelPreview {

    // Standard resolution for the desktop Zebra thermal printers BlueSeer's
    // existing templates (item.prn, item4x6.prn, itemFIC.prn, ...) assume.
    private static final int DEFAULT_DPI = 203;

    public static File renderToPdf(String zplText) throws IOException {
        int pwDots = extractInt(zplText, "\\^PW(\\d+)", 4 * DEFAULT_DPI);
        int llDots = extractInt(zplText, "\\^LL(\\d+)", 6 * DEFAULT_DPI);
        float widthIn = pwDots / (float) DEFAULT_DPI;
        float heightIn = llDots / (float) DEFAULT_DPI;

        ZPLConfig config = new ZPLConfig(widthIn, heightIn, "in", DEFAULT_DPI);
        File out = File.createTempFile("blueseer_label_preview_", ".pdf");
        out.deleteOnExit();
        ZPLRenderer.renderWithConfig(zplText, config, out.getAbsolutePath());
        return out;
    }

    private static int extractInt(String zpl, String pattern, int fallback) {
        Matcher m = Pattern.compile(pattern).matcher(zpl);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }
}

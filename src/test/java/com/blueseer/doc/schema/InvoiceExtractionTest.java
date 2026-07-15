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
package com.blueseer.doc.schema;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the deterministic arithmetic reconciliation check - the
 * alternative to giving the LLM a calculator tool, see
 * InvoiceExtraction#totalsReconcile's javadoc.
 */
public class InvoiceExtractionTest {

    private InvoiceExtraction withLines(double total, InvoiceExtraction.Line... lines) {
        return new InvoiceExtraction("Acme", "INV-1", "2026-07-15", total, List.of(lines));
    }

    @Test
    void reconcilesWhenLinesSumToTotal() {
        InvoiceExtraction inv = withLines(42.50,
                new InvoiceExtraction.Line("Flour 25kg", 2, "bag", 20.00, "high"),
                new InvoiceExtraction.Line("Yeast 1kg", 1, "ea", 2.50, "high"));
        assertTrue(inv.totalsReconcile());
    }

    @Test
    void flagsWhenLinesDontSumToTotal() {
        InvoiceExtraction inv = withLines(100.00,
                new InvoiceExtraction.Line("Flour 25kg", 2, "bag", 20.00, "high"));
        assertFalse(inv.totalsReconcile());
    }

    @Test
    void toleratesSmallRoundingDifference() {
        InvoiceExtraction inv = withLines(42.51,
                new InvoiceExtraction.Line("Flour 25kg", 2, "bag", 20.00, "high"),
                new InvoiceExtraction.Line("Yeast 1kg", 1, "ea", 2.50, "high"));
        assertTrue(inv.totalsReconcile());
    }

    @Test
    void skipsCheckWhenNoTotalExtracted() {
        InvoiceExtraction inv = withLines(0,
                new InvoiceExtraction.Line("Flour 25kg", 2, "bag", 20.00, "high"));
        assertTrue(inv.totalsReconcile());
    }
}

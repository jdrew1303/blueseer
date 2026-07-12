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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit tests for the pure, DB-free rounding/%RI math in
 * {@link NutritionLabelEngine} - the three-tier rounding regime (Commission
 * guidance document, Dec 2012) and the fixed Annex XIII Part B %RI
 * calculation. These are the parts most critical to get exactly right and
 * most amenable to plain unit testing without a database; full BOM-recursion
 * end-to-end coverage (the actual per-item flatten/rollup) still needs the
 * SQLite test harness used elsewhere in this project and isn't covered here -
 * see the epic's NUTR-14 story.
 */
class NutritionLabelEngineTest {

    // Mirrors the seeded FAT row: >=10g -> nearest 1g, <10g and >0.5g -> nearest
    // 0.1g, <=0.5g -> "<0.5g" (or "0g" if truly negligible).
    private static final nutData.nut_mstr MACRO_SPEC = new nutData.nut_mstr(
            "FAT", "Fat", "g", true, 2, 37, 9, 10, 1, 0.1, 0.5, 0.5, null, true);

    // Mirrors the seeded SALT row - note the high-tier precision is 0.1g (one
    // decimal), not "nearest whole gram" like the macro group above, and the
    // trace label (0.01) genuinely differs from the trace cutoff (0.0125).
    private static final nutData.nut_mstr SALT_SPEC = new nutData.nut_mstr(
            "SALT", "Salt", "g", true, 12, 0, 0, 1, 0.1, 0.01, 0.0125, 0.01, null, true);

    @Test
    void macroTier_roundsToWholeGramAtOrAboveTenGrams() {
        assertEquals("15g", NutritionLabelEngine.formatGrams(15.2, MACRO_SPEC));
        assertEquals("10g", NutritionLabelEngine.formatGrams(10.0, MACRO_SPEC));
    }

    @Test
    void macroTier_roundsToOneDecimalBetweenTraceCutoffAndTenGrams() {
        assertEquals("3.2g", NutritionLabelEngine.formatGrams(3.24, MACRO_SPEC));
        assertEquals("9.9g", NutritionLabelEngine.formatGrams(9.94, MACRO_SPEC));
    }

    @Test
    void macroTier_showsTraceLabelAtOrBelowCutoff() {
        assertEquals("<0.5g", NutritionLabelEngine.formatGrams(0.3, MACRO_SPEC));
        assertEquals("<0.5g", NutritionLabelEngine.formatGrams(0.5, MACRO_SPEC));
    }

    @Test
    void macroTier_showsZeroForTrueZero() {
        assertEquals("0g", NutritionLabelEngine.formatGrams(0.0, MACRO_SPEC));
    }

    @Test
    void saltTier_highPrecisionIsOneDecimalNotWholeGram() {
        // 1.05 is >= the 1g high threshold, so it rounds to the nearest 0.1g (1.1),
        // NOT the nearest whole gram the macro group would use at this threshold.
        assertEquals("1.1g", NutritionLabelEngine.formatGrams(1.05, SALT_SPEC));
        assertEquals("2.0g", NutritionLabelEngine.formatGrams(2.0, SALT_SPEC));
    }

    @Test
    void saltTier_midPrecisionIsTwoDecimals() {
        assertEquals("0.43g", NutritionLabelEngine.formatGrams(0.43, SALT_SPEC));
    }

    @Test
    void saltTier_traceLabelDiffersFromItsOwnCutoff() {
        // cutoff is 0.0125, but the displayed trace label is "<0.01g", not "<0.0125g" -
        // this is the one nutrient where guidance's label and cutoff genuinely differ.
        assertEquals("<0.01g", NutritionLabelEngine.formatGrams(0.008, SALT_SPEC));
    }

    @Test
    void roundToIncrement_wholeNumber() {
        assertEquals(12.0, NutritionLabelEngine.roundToIncrement(12.34, 1), 0.0001);
    }

    @Test
    void roundToIncrement_oneDecimalHalfRoundsUp() {
        assertEquals(12.6, NutritionLabelEngine.roundToIncrement(12.55, 0.1), 0.0001);
    }

    @Test
    void riPercent_wholeNumberOfReferenceIntake() {
        assertEquals(50, NutritionLabelEngine.riPercent(35.0, 70.0));
    }

    @Test
    void riPercent_zeroValue() {
        assertEquals(0, NutritionLabelEngine.riPercent(0.0, 70.0));
    }

    @Test
    void riPercent_guardsAgainstZeroReferenceIntake() {
        assertEquals(0, NutritionLabelEngine.riPercent(70.0, 0.0));
    }
}

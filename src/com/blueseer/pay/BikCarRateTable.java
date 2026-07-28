package com.blueseer.pay;

import java.math.BigDecimal;

/**
 * C-07's Table B (CO&#8322; g/km -&gt; vehicle category) and Table A
 * (category &times; business-mileage band -&gt; rate percentage) lookups,
 * per TDM Part 05-01-01b - a small Java lookup table, not an EvalEx
 * expression, per the calc-engine spec's own instruction (a 6-category
 * &times; 4-band matrix is not idiomatic as nested {@code IF()} calls).
 */
final class BikCarRateTable {

    private static final BigDecimal MILEAGE_BAND_1_UPPER = new BigDecimal("26000");
    private static final BigDecimal MILEAGE_BAND_2_UPPER = new BigDecimal("39000");
    private static final BigDecimal MILEAGE_BAND_3_UPPER = new BigDecimal("48000");

    private static final BigDecimal CO2_BAND_A_UPPER = new BigDecimal("59");
    private static final BigDecimal CO2_BAND_B_UPPER = new BigDecimal("99");
    private static final BigDecimal CO2_BAND_C_UPPER = new BigDecimal("139");
    private static final BigDecimal CO2_BAND_D_UPPER = new BigDecimal("179");

    private BikCarRateTable() {
    }

    /** Table B: A1 = 0g/km; A = &gt;0-59g/km; B = &gt;59-99g/km; C = &gt;99-139g/km; D = &gt;139-179g/km; E = &gt;179g/km. */
    static String categoryForCo2(BigDecimal co2GramsPerKm) {
        if (co2GramsPerKm.compareTo(BigDecimal.ZERO) <= 0) {
            return "A1";
        }
        if (co2GramsPerKm.compareTo(CO2_BAND_A_UPPER) <= 0) {
            return "A";
        }
        if (co2GramsPerKm.compareTo(CO2_BAND_B_UPPER) <= 0) {
            return "B";
        }
        if (co2GramsPerKm.compareTo(CO2_BAND_C_UPPER) <= 0) {
            return "C";
        }
        if (co2GramsPerKm.compareTo(CO2_BAND_D_UPPER) <= 0) {
            return "D";
        }
        return "E";
    }

    /** Table A: the rate for {@code category} at whichever of the 4 mileage bands {@code annualisedBusinessKm} falls into. */
    static BigDecimal rateForCategoryAndMileage(ITaxYearRules.BikCarRates rates, String category, BigDecimal annualisedBusinessKm) {
        ITaxYearRules.BikCarRates.CategoryRateBands bands = bandsFor(rates, category);
        if (annualisedBusinessKm.compareTo(MILEAGE_BAND_1_UPPER) <= 0) {
            return bands.upTo26000Km();
        }
        if (annualisedBusinessKm.compareTo(MILEAGE_BAND_2_UPPER) <= 0) {
            return bands.from26001To39000Km();
        }
        if (annualisedBusinessKm.compareTo(MILEAGE_BAND_3_UPPER) <= 0) {
            return bands.from39001To48000Km();
        }
        return bands.from48001KmUp();
    }

    /** Table A's highest/undiscounted rate for {@code category} - the 0-26,000km band - needed for C-07 Step 4's 20%-reduction alternative. */
    static BigDecimal lowestMileageBandRate(ITaxYearRules.BikCarRates rates, String category) {
        return bandsFor(rates, category).upTo26000Km();
    }

    private static ITaxYearRules.BikCarRates.CategoryRateBands bandsFor(ITaxYearRules.BikCarRates rates, String category) {
        ITaxYearRules.BikCarRates.CategoryRateBands bands = rates.ratesByCategory().get(category);
        if (bands == null) {
            throw new IllegalArgumentException("Unknown BIK car vehicle category: " + category);
        }
        return bands;
    }
}

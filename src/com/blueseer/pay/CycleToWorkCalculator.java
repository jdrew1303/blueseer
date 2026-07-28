package com.blueseer.pay;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * C-11 Cycle to Work Scheme: the exemption limit is a flat €1,250 (standard
 * bike) or €1,500 (e-bike, per Finance Act provisions) - not a per-tax-year
 * rate resolved from {@link ITaxYearRules}, since this scheme's caps are set
 * by separate legislation on a different update cadence than the annual
 * Budget figures that interface models. Eligibility is a simple once-per-4-years
 * check against the employee's last arrangement start date.
 */
final class CycleToWorkCalculator {

    private static final BigDecimal STANDARD_BIKE_LIMIT = new BigDecimal("1250");
    private static final BigDecimal ELECTRIC_BIKE_LIMIT = new BigDecimal("1500");
    private static final int ELIGIBILITY_WINDOW_YEARS = 4;

    private CycleToWorkCalculator() {
    }

    static BigDecimal exemptionLimit(boolean isElectricBike) {
        return isElectricBike ? ELECTRIC_BIKE_LIMIT : STANDARD_BIKE_LIMIT;
    }

    static boolean isEligible(LocalDate lastArrangementDateOrNull, LocalDate today) {
        return lastArrangementDateOrNull == null
                || !lastArrangementDateOrNull.plusYears(ELIGIBILITY_WINDOW_YEARS).isAfter(today);
    }
}

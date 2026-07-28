package com.blueseer.pay;

import com.blueseer.pay.UscCalculator.CalculationBasis;
import com.blueseer.pay.UscCalculator.UscContext;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hand-verified against the exact arithmetic in
 * {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md} C-03.
 * Uses a synthetic {@link ITaxYearRules.UscRates} with clean monthly band
 * ceilings (12,000/24,000/60,000 annual -&gt; 1,000/2,000/5,000 monthly at
 * periodNumber=1) instead of the real 2026 figures, since 28700/12 does not
 * divide evenly and would make the expected values impossible to verify by
 * hand; {@link PrsiCalculatorTest} uses the real figures because PRSI's flat
 * rate needs no such division.
 */
class UscCalculatorTest {

    private static final ITaxYearRules.UscRates RATES = new ITaxYearRules.UscRates(
            new BigDecimal("12000"), new BigDecimal("0.005"),
            new BigDecimal("24000"), new BigDecimal("0.02"),
            new BigDecimal("60000"), new BigDecimal("0.03"),
            new BigDecimal("0.08"),
            new BigDecimal("0.02"),
            new BigDecimal("0.08"));

    @Test
    void cumulativeBasis_exemptMarker_shortCircuitsToZero() {
        UscContext ctx = new UscContext(CalculationBasis.CUMULATIVE,
                new BigDecimal("3000.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, true, false);

        EngineResult result = UscCalculator.calculate(ctx, RATES);

        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(result.amount()));
        assertEquals(1, result.steps().size());
    }

    @Test
    void cumulativeBasis_entirelyWithinBand1() {
        UscContext ctx = new UscContext(CalculationBasis.CUMULATIVE,
                new BigDecimal("500.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false);

        EngineResult result = UscCalculator.calculate(ctx, RATES);

        // 500 * 0.005 = 2.50
        assertEquals(0, new BigDecimal("2.50").compareTo(result.amount()));
    }

    @Test
    void cumulativeBasis_spansThreeBands_standardBranch() {
        UscContext ctx = new UscContext(CalculationBasis.CUMULATIVE,
                new BigDecimal("3000.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false);

        EngineResult result = UscCalculator.calculate(ctx, RATES);

        // band1: 1000*0.005=5.00; band2: 1000*0.02=20.00; band3: 1000*0.03=30.00 -> 55.00
        assertEquals(0, new BigDecimal("55.00").compareTo(result.amount()));
    }

    @Test
    void cumulativeBasis_spansThreeBands_reducedRateCapBranch() {
        UscContext ctx = new UscContext(CalculationBasis.CUMULATIVE,
                new BigDecimal("3000.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, false, true);

        EngineResult result = UscCalculator.calculate(ctx, RATES);

        // band1: 1000*0.005=5.00; remainder above band1: 2000*0.02(cap)=40.00 -> 45.00
        assertEquals(0, new BigDecimal("45.00").compareTo(result.amount()));
    }

    @Test
    void week1Basis_ignoresPeriodNumberAndPriorHistory() {
        UscContext ctx = new UscContext(CalculationBasis.WEEK1,
                new BigDecimal("3000.00"), 12, 7,
                new BigDecimal("999999"), new BigDecimal("999999"), false, false);

        EngineResult result = UscCalculator.calculate(ctx, RATES);

        assertEquals(0, new BigDecimal("55.00").compareTo(result.amount()));
    }

    @Test
    void week1Basis_exemptMarker_shortCircuitsToZero() {
        UscContext ctx = new UscContext(CalculationBasis.WEEK1,
                new BigDecimal("3000.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, true, false);

        EngineResult result = UscCalculator.calculate(ctx, RATES);

        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(result.amount()));
    }

    @Test
    void emergencyBasis_flatRateNoBands() {
        UscContext ctx = new UscContext(CalculationBasis.EMERGENCY,
                new BigDecimal("1000.00"), 52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false);

        EngineResult result = UscCalculator.calculate(ctx, RATES);

        // 1000 * 0.08 = 80.00
        assertEquals(0, new BigDecimal("80.00").compareTo(result.amount()));
    }
}

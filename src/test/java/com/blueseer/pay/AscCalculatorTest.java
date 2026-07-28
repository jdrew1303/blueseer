package com.blueseer.pay;

import com.blueseer.pay.AscCalculator.AscContext;
import com.blueseer.pay.ITaxYearRules.AscGroup;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hand-verified against the exact arithmetic in
 * {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md} C-05.
 * Uses the real 2026 Standard Accrual rates - unlike USC's bands, ASC's
 * 34,500/60,000 annual ceilings both divide evenly by 12, so no synthetic
 * rate table is needed to keep the expected values hand-verifiable.
 */
class AscCalculatorTest {

    private static final Paye2026Rules RULES = new Paye2026Rules();
    private static final ITaxYearRules.AscRates RATES = RULES.ascRates(AscGroup.STANDARD_ACCRUAL);

    @Test
    void belowExemptCeiling_noAscDue() {
        AscContext ctx = new AscContext(new BigDecimal("2000.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO,
                false, BigDecimal.ZERO, false, BigDecimal.ZERO);

        EngineResult result = AscCalculator.calculate(ctx, RATES);

        assertEquals(0, new BigDecimal("0.00").compareTo(result.amount()));
    }

    @Test
    void withinMidBand_midRateOnly() {
        AscContext ctx = new AscContext(new BigDecimal("4000.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO,
                false, BigDecimal.ZERO, false, BigDecimal.ZERO);

        EngineResult result = AscCalculator.calculate(ctx, RATES);

        // midBandPortion = 4000 - 2875 = 1125; 1125 * 0.10 = 112.50
        assertEquals(0, new BigDecimal("112.50").compareTo(result.amount()));
    }

    @Test
    void aboveUpperBand_midAndTopRateBlended() {
        AscContext ctx = new AscContext(new BigDecimal("6000.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO,
                false, BigDecimal.ZERO, false, BigDecimal.ZERO);

        EngineResult result = AscCalculator.calculate(ctx, RATES);

        // mid: (5000-2875)*0.10=212.50; top: (6000-5000)*0.105=105.00 -> 317.50
        assertEquals(0, new BigDecimal("317.50").compareTo(result.amount()));
    }

    @Test
    void manualOverrideAmount_takesPrecedenceOverSystemCalculation() {
        AscContext ctx = new AscContext(new BigDecimal("4000.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO,
                true, new BigDecimal("99.99"), false, BigDecimal.ZERO);

        EngineResult result = AscCalculator.calculate(ctx, RATES);

        assertEquals(0, new BigDecimal("99.99").compareTo(result.amount()));
    }

    @Test
    void manualOverridePercentage_appliesToPensionableRemunerationThisPeriod() {
        AscContext ctx = new AscContext(new BigDecimal("4000.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO,
                false, BigDecimal.ZERO, true, new BigDecimal("0.05"));

        EngineResult result = AscCalculator.calculate(ctx, RATES);

        assertEquals(0, new BigDecimal("200.00").compareTo(result.amount()));
    }
}

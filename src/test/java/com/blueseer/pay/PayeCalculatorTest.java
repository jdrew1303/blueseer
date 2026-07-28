package com.blueseer.pay;

import com.blueseer.pay.PayeCalculator.CalculationBasis;
import com.blueseer.pay.PayeCalculator.PayeContext;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hand-verified against the exact arithmetic in
 * {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md} C-01.
 * The cumulative/Week1 cases deliberately use monthly figures that divide
 * evenly (&euro;4,200 credit / &euro;48,000 SRCOP over 12 periods = a clean
 * &euro;350/&euro;4,000 per period) so the expected result can be checked by
 * hand without repeating-decimal arithmetic; {@link PrsiCalculatorTest} and
 * {@link UscCalculatorTest} do the same.
 */
class PayeCalculatorTest {

    private static final Paye2026Rules RULES = new Paye2026Rules();

    @Test
    void cumulativeBasis_incomeSplitAcrossBothRates() {
        PayeContext ctx = new PayeContext(
                CalculationBasis.CUMULATIVE,
                new BigDecimal("4500.00"), // grossPayThisPeriod
                12,                        // payPeriodsPerYear
                1,                         // periodNumber
                BigDecimal.ZERO,           // cumulativeGrossPayPriorToThisPeriod
                BigDecimal.ZERO,           // cumulativeTaxPaidToDate
                new BigDecimal("4200"),    // annualTaxCredit -> 350/period
                new BigDecimal("48000"),   // annualCutOffPoint -> 4000/period
                true, 0);

        EngineResult result = PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.MONTHLY));

        // standard: 4000 * 0.20 = 800; higher: 500 * 0.40 = 200; gross tax 1000; less credit 350 = 650
        assertEquals(0, new BigDecimal("650.00").compareTo(result.amount()));
        assertEquals(8, result.steps().size());
    }

    @Test
    void week1Basis_ignoresPeriodNumberAndPriorHistory() {
        PayeContext ctx = new PayeContext(
                CalculationBasis.WEEK1,
                new BigDecimal("4500.00"),
                12,
                5, // periodNumber - must be irrelevant under Week1
                new BigDecimal("999999"), // prior gross - must be irrelevant under Week1
                new BigDecimal("999999"), // prior tax paid - must be irrelevant under Week1
                new BigDecimal("4200"),
                new BigDecimal("48000"),
                true, 0);

        EngineResult result = PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.MONTHLY));

        assertEquals(0, new BigDecimal("650.00").compareTo(result.amount()));
    }

    @Test
    void emergencyBasis_withinAllowanceWindow_getsCutOffButNoCredit() {
        PayeContext ctx = new PayeContext(
                CalculationBasis.EMERGENCY,
                new BigDecimal("900.00"),
                52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, // annualTaxCredit/CutOffPoint irrelevant under EMERGENCY
                true,
                2); // within the 4-week weekly allowance window

        EngineResult result = PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.WEEKLY));

        // cutoff 846.16: standard 846.16*0.20=169.232; higher (900-846.16)*0.40=21.536; sum 190.768 -> 190.77
        assertEquals(0, new BigDecimal("190.77").compareTo(result.amount()));
    }

    @Test
    void emergencyBasis_afterAllowanceWindowExpires_fullyTaxedAtHigherRate() {
        PayeContext ctx = new PayeContext(
                CalculationBasis.EMERGENCY,
                new BigDecimal("900.00"),
                52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                true,
                5); // past the 4-week weekly window

        EngineResult result = PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.WEEKLY));

        assertEquals(0, new BigDecimal("360.00").compareTo(result.amount()));
    }

    @Test
    void emergencyBasis_noPpsNumber_fullyTaxedAtHigherRateRegardlessOfPeriod() {
        PayeContext ctx = new PayeContext(
                CalculationBasis.EMERGENCY,
                new BigDecimal("900.00"),
                52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO,
                false, // no PPS number
                1);    // even within what would otherwise be the allowance window

        EngineResult result = PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.WEEKLY));

        assertEquals(0, new BigDecimal("360.00").compareTo(result.amount()));
    }
}

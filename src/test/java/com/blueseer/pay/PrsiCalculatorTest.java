package com.blueseer.pay;

import com.blueseer.pay.PrsiCalculator.PrsiContext;
import com.blueseer.pay.PrsiCalculator.PrsiResult;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Hand-verified against the exact arithmetic in
 * {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md} C-02.
 * Pre-October rates only (weeklyLowerThreshold=352, prsiCreditUpperThreshold=424,
 * prsiCreditMax=12, employeeRate=0.0420, employerLowerRate=0.0900,
 * employerHigherRate=0.1125, employerHigherRateThreshold=552).
 */
class PrsiCalculatorTest {

    private static final Paye2026Rules RULES = new Paye2026Rules();
    private static final LocalDate PRE_OCTOBER_PAY_DATE = LocalDate.of(2026, 3, 1);

    @Test
    void belowLowerThreshold_noEmployeePrsiButEmployerStillCharged() {
        PrsiContext ctx = new PrsiContext(new BigDecimal("300.00"));

        PrsiResult result = PrsiCalculator.calculate(ctx, RULES.prsiRates(PRE_OCTOBER_PAY_DATE, PayFrequency.WEEKLY));

        assertEquals(0, new BigDecimal("0.00").compareTo(result.employee().amount()));
        // 300 <= 552 -> 300 * 0.09 = 27.00
        assertEquals(0, new BigDecimal("27.00").compareTo(result.employer().amount()));
    }

    @Test
    void withinTaperedCreditBand_creditReducesEmployeePrsi() {
        PrsiContext ctx = new PrsiContext(new BigDecimal("400.00"));

        PrsiResult result = PrsiCalculator.calculate(ctx, RULES.prsiRates(PRE_OCTOBER_PAY_DATE, PayFrequency.WEEKLY));

        // raw: 400*0.042=16.80; credit: 12-((400-352-0.01)/6)=12-7.99833..=4.00166..; 16.80-4.00166..=12.79833.. -> 12.80
        assertEquals(0, new BigDecimal("12.80").compareTo(result.employee().amount()));
        // 400 <= 552 -> 400 * 0.09 = 36.00
        assertEquals(0, new BigDecimal("36.00").compareTo(result.employer().amount()));
    }

    @Test
    void aboveCreditUpperThresholdAndEmployerHigherThreshold_fullRatesNoCredit() {
        PrsiContext ctx = new PrsiContext(new BigDecimal("600.00"));

        PrsiResult result = PrsiCalculator.calculate(ctx, RULES.prsiRates(PRE_OCTOBER_PAY_DATE, PayFrequency.WEEKLY));

        // 600 * 0.042 = 25.20, no credit (600 is not < 424)
        assertEquals(0, new BigDecimal("25.20").compareTo(result.employee().amount()));
        // 600 > 552 -> 600 * 0.1125 = 67.50
        assertEquals(0, new BigDecimal("67.50").compareTo(result.employer().amount()));
    }
}

package com.blueseer.pay;

import com.blueseer.pay.LptCalculator.LptContext;
import com.blueseer.pay.LptCalculator.LptDeductionMethod;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Hand-verified against the exact arithmetic in irish-payroll-2026-calc-engines-spec.md C-04. */
class LptCalculatorTest {

    @Test
    void fixedPeriodicMethod_dividesAnnualChargeByPeriods() {
        LptContext ctx = new LptContext(LptDeductionMethod.FIXED_PERIODIC,
                new BigDecimal("360"), 12, BigDecimal.ZERO, BigDecimal.ZERO);

        EngineResult result = LptCalculator.calculate(ctx);

        assertEquals(0, new BigDecimal("30.00").compareTo(result.amount()));
    }

    @Test
    void percentageOfNetMethod_appliesRateToNetPay() {
        LptContext ctx = new LptContext(LptDeductionMethod.PERCENTAGE_OF_NET,
                BigDecimal.ZERO, 12, new BigDecimal("1000.00"), new BigDecimal("0.01"));

        EngineResult result = LptCalculator.calculate(ctx);

        assertEquals(0, new BigDecimal("10.00").compareTo(result.amount()));
    }

    @Test
    void noneMethod_deductsNothing() {
        LptContext ctx = new LptContext(LptDeductionMethod.NONE,
                BigDecimal.ZERO, 12, BigDecimal.ZERO, BigDecimal.ZERO);

        EngineResult result = LptCalculator.calculate(ctx);

        assertEquals(0, new BigDecimal("0.00").compareTo(result.amount()));
    }
}

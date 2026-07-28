package com.blueseer.pay;

import com.blueseer.pay.AscCalculator.AscContext;
import com.blueseer.pay.ITaxYearRules.AscGroup;
import com.blueseer.pay.LptCalculator.LptContext;
import com.blueseer.pay.LptCalculator.LptDeductionMethod;
import com.blueseer.pay.PayeCalculator.CalculationBasis;
import com.blueseer.pay.PayeCalculator.PayeContext;
import com.blueseer.pay.PrsiCalculator.PrsiContext;
import com.blueseer.pay.PrsiCalculator.PrsiResult;
import com.blueseer.pay.UscCalculator.UscContext;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * End-to-end wiring test for the {@link IPayrollCalculationService} facade:
 * confirms each method resolves {@link Paye2026Rules} through the registry
 * and delegates to the correct C-01...C-05 calculator, reproducing the same
 * expected values already hand-verified in each calculator's own test.
 */
class PayrollCalculationServiceTest {

    private static final IPayrollCalculationService SERVICE =
            new PayrollCalculationService(new TaxYearRulesRegistry(List.of(new Paye2026Rules())));

    @Test
    void calculatePaye_delegatesToC01() {
        PayeContext ctx = new PayeContext(CalculationBasis.CUMULATIVE,
                new BigDecimal("4500.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("4200"), new BigDecimal("48000"),
                true, 0);

        EngineResult result = SERVICE.calculatePaye(2026, PayFrequency.MONTHLY, ctx);

        assertEquals(0, new BigDecimal("650.00").compareTo(result.amount()));
    }

    @Test
    void calculatePrsi_delegatesToC02() {
        PrsiContext ctx = new PrsiContext(new BigDecimal("400.00"));

        PrsiResult result = SERVICE.calculatePrsi(2026, LocalDate.of(2026, 3, 1), PayFrequency.WEEKLY, ctx);

        assertEquals(0, new BigDecimal("12.80").compareTo(result.employee().amount()));
        assertEquals(0, new BigDecimal("36.00").compareTo(result.employer().amount()));
    }

    @Test
    void calculateUsc_delegatesToC03() {
        UscContext ctx = new UscContext(UscCalculator.CalculationBasis.CUMULATIVE,
                new BigDecimal("500.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false);

        EngineResult result = SERVICE.calculateUsc(2026, ctx);

        assertEquals(0, new BigDecimal("2.50").compareTo(result.amount()));
    }

    @Test
    void calculateLpt_delegatesToC04() {
        LptContext ctx = new LptContext(LptDeductionMethod.FIXED_PERIODIC,
                new BigDecimal("360"), 12, BigDecimal.ZERO, BigDecimal.ZERO);

        EngineResult result = SERVICE.calculateLpt(ctx);

        assertEquals(0, new BigDecimal("30.00").compareTo(result.amount()));
    }

    @Test
    void calculateAsc_delegatesToC05() {
        AscContext ctx = new AscContext(new BigDecimal("4000.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO,
                false, BigDecimal.ZERO, false, BigDecimal.ZERO);

        EngineResult result = SERVICE.calculateAsc(2026, AscGroup.STANDARD_ACCRUAL, ctx);

        assertEquals(0, new BigDecimal("112.50").compareTo(result.amount()));
    }

    @Test
    void unregisteredTaxYear_failsLoudlyRatherThanFallingBackToAnotherYear() {
        UscContext ctx = new UscContext(UscCalculator.CalculationBasis.CUMULATIVE,
                new BigDecimal("500.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false);

        assertThrows(IllegalStateException.class, () -> SERVICE.calculateUsc(2025, ctx));
    }
}

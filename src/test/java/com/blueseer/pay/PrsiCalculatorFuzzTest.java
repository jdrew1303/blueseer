package com.blueseer.pay;

import com.blueseer.pay.PrsiCalculator.PrsiContext;
import com.blueseer.pay.PrsiCalculator.PrsiResult;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary and randomized (seeded, reproducible) coverage for C-02 PRSI,
 * complementing {@link PrsiCalculatorTest}'s hand-verified worked examples.
 * Covers both the pre- and post-1-October-2026 rate step, since {@link
 * ITaxYearRules#prsiRates} resolves a genuinely different rate set either
 * side of that date.
 */
class PrsiCalculatorFuzzTest {

    private static final Paye2026Rules RULES = new Paye2026Rules();
    private static final LocalDate PRE_OCTOBER = LocalDate.of(2026, 3, 1);
    private static final LocalDate POST_OCTOBER = LocalDate.of(2026, 11, 1);
    private static final long SEED = 20260101L;
    private static final int ITERATIONS = 500;

    // ---- Explicit boundary cases ----

    @Test
    void zeroReckonablePay_bothRateEras_returnsZeroForBothEmployeeAndEmployer() {
        for (LocalDate payDate : new LocalDate[] {PRE_OCTOBER, POST_OCTOBER}) {
            PrsiResult result = PrsiCalculator.calculate(new PrsiContext(BigDecimal.ZERO),
                    RULES.prsiRates(payDate, PayFrequency.WEEKLY));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.employee().amount()), payDate + ": employee");
            assertEquals(0, BigDecimal.ZERO.compareTo(result.employer().amount()), payDate + ": employer");
        }
    }

    @Test
    void reckonablePayExactlyAtLowerThreshold_stillZeroEmployeePrsi() {
        // The formula's own "<=" boundary - confirmed exactly at the threshold, not just just-below.
        PrsiResult result = PrsiCalculator.calculate(new PrsiContext(new BigDecimal("352.00")),
                RULES.prsiRates(PRE_OCTOBER, PayFrequency.WEEKLY));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.employee().amount()));
    }

    @Test
    void reckonablePayExactlyAtEmployerHigherRateThreshold_stillLowerRate() {
        PrsiResult result = PrsiCalculator.calculate(new PrsiContext(new BigDecimal("552.00")),
                RULES.prsiRates(PRE_OCTOBER, PayFrequency.WEEKLY));
        // 552 <= 552 -> lower rate: 552 * 0.09 = 49.68
        assertEquals(0, new BigDecimal("49.68").compareTo(result.employer().amount()));
    }

    @Test
    void reckonablePayOneCentAboveEmployerHigherRateThreshold_switchesToHigherRate() {
        PrsiResult result = PrsiCalculator.calculate(new PrsiContext(new BigDecimal("552.01")),
                RULES.prsiRates(PRE_OCTOBER, PayFrequency.WEEKLY));
        // 552.01 > 552 -> higher rate: 552.01 * 0.1125 = 62.101125 -> 62.10
        assertEquals(0, new BigDecimal("62.10").compareTo(result.employer().amount()));
    }

    @Test
    void reckonablePayExactlyAtCreditUpperThreshold_noCreditApplied() {
        // Credit band condition is a strict "<", so exactly at prsiCreditUpperThreshold (424) gets no credit.
        PrsiResult result = PrsiCalculator.calculate(new PrsiContext(new BigDecimal("424.00")),
                RULES.prsiRates(PRE_OCTOBER, PayFrequency.WEEKLY));
        // 424 * 0.042 = 17.808 -> 17.81, no credit
        assertEquals(0, new BigDecimal("17.81").compareTo(result.employee().amount()));
    }

    @Test
    void postOctoberRateStep_higherEmployeeAndEmployerRatesThanPreOctober() {
        PrsiContext ctx = new PrsiContext(new BigDecimal("1000.00"));
        BigDecimal preEmployee = PrsiCalculator.calculate(ctx, RULES.prsiRates(PRE_OCTOBER, PayFrequency.WEEKLY)).employee().amount();
        BigDecimal postEmployee = PrsiCalculator.calculate(ctx, RULES.prsiRates(POST_OCTOBER, PayFrequency.WEEKLY)).employee().amount();
        assertTrue(postEmployee.compareTo(preEmployee) > 0, "post-1-Oct-2026 employee PRSI rate must be strictly higher");
    }

    @Test
    void extremeReckonablePay_veryLarge_doesNotThrowAndStaysNonNegative() {
        for (LocalDate payDate : new LocalDate[] {PRE_OCTOBER, POST_OCTOBER}) {
            PrsiContext ctx = new PrsiContext(new BigDecimal("999999999.99"));
            PrsiResult result = assertDoesNotThrow(() -> PrsiCalculator.calculate(ctx, RULES.prsiRates(payDate, PayFrequency.WEEKLY)));
            assertTrue(result.employee().amount().signum() >= 0);
            assertTrue(result.employer().amount().signum() >= 0);
        }
    }

    @Test
    void amountIsAlwaysScaledToTwoDecimalPlaces() {
        PrsiResult result = PrsiCalculator.calculate(new PrsiContext(new BigDecimal("456.789")), RULES.prsiRates(PRE_OCTOBER, PayFrequency.WEEKLY));
        assertEquals(2, result.employee().amount().scale());
        assertEquals(2, result.employer().amount().scale());
    }

    // ---- Randomized fuzzing ----

    @Test
    void randomizedReckonablePay_neverNegative_neverThrows() {
        Random random = new Random(SEED);
        for (LocalDate payDate : new LocalDate[] {PRE_OCTOBER, POST_OCTOBER}) {
            for (PayFrequency freq : PayFrequency.values()) {
                ITaxYearRules.PrsiRates rates = RULES.prsiRates(payDate, freq);
                for (int i = 0; i < ITERATIONS; i++) {
                    BigDecimal pay = randomAmount(random, 1_000_000);
                    PrsiContext ctx = new PrsiContext(pay);
                    PrsiResult result = assertDoesNotThrow(() -> PrsiCalculator.calculate(ctx, rates),
                            () -> payDate + "/" + freq + " threw for pay=" + pay);
                    assertTrue(result.employee().amount().signum() >= 0,
                            () -> payDate + "/" + freq + ": negative employee PRSI for pay=" + pay);
                    assertTrue(result.employer().amount().signum() >= 0,
                            () -> payDate + "/" + freq + ": negative employer PRSI for pay=" + pay);
                }
            }
        }
    }

    @Test
    void monotonicInReckonablePay_increasingPayNeverDecreasesEitherPrsiFigure() {
        Random random = new Random(SEED + 1);
        for (LocalDate payDate : new LocalDate[] {PRE_OCTOBER, POST_OCTOBER}) {
            for (PayFrequency freq : PayFrequency.values()) {
                ITaxYearRules.PrsiRates rates = RULES.prsiRates(payDate, freq);
                BigDecimal previousPay = BigDecimal.ZERO;
                BigDecimal previousEmployee = BigDecimal.ZERO;
                BigDecimal previousEmployer = BigDecimal.ZERO;
                for (int i = 0; i < ITERATIONS; i++) {
                    BigDecimal pay = previousPay.add(randomAmount(random, 2000));
                    PrsiResult result = PrsiCalculator.calculate(new PrsiContext(pay), rates);

                    BigDecimal priorPayForMessage = previousPay;
                    BigDecimal priorEmployeeForMessage = previousEmployee;
                    BigDecimal priorEmployerForMessage = previousEmployer;
                    assertTrue(result.employee().amount().compareTo(previousEmployee) >= 0,
                            () -> payDate + "/" + freq + ": employee PRSI decreased from " + priorEmployeeForMessage
                                    + " (pay=" + priorPayForMessage + ") to " + result.employee().amount() + " (pay=" + pay + ")");
                    assertTrue(result.employer().amount().compareTo(previousEmployer) >= 0,
                            () -> payDate + "/" + freq + ": employer PRSI decreased from " + priorEmployerForMessage
                                    + " (pay=" + priorPayForMessage + ") to " + result.employer().amount() + " (pay=" + pay + ")");

                    previousPay = pay;
                    previousEmployee = result.employee().amount();
                    previousEmployer = result.employer().amount();
                }
            }
        }
    }

    private static BigDecimal randomAmount(Random random, int maxWhole) {
        double value = random.nextDouble() * maxWhole;
        return BigDecimal.valueOf(Math.round(value * 100.0) / 100.0);
    }
}

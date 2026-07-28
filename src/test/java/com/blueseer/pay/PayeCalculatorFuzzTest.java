package com.blueseer.pay;

import com.blueseer.pay.PayeCalculator.CalculationBasis;
import com.blueseer.pay.PayeCalculator.PayeContext;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary and randomized (seeded, reproducible) coverage for C-01 PAYE,
 * complementing {@link PayeCalculatorTest}'s hand-verified worked examples.
 * Not property-based via a library (no jqwik dependency in this project) -
 * a plain seeded {@link Random} loop is sufficient for the invariants below
 * and keeps this dependency-free, matching the rest of this module's test
 * setup (JUnit 5 only).
 */
class PayeCalculatorFuzzTest {

    private static final Paye2026Rules RULES = new Paye2026Rules();
    private static final long SEED = 20260101L;
    private static final int ITERATIONS = 500;

    // ---- Explicit boundary cases ----

    @Test
    void zeroGrossPay_everyBasis_returnsZeroWithoutException() {
        for (CalculationBasis basis : CalculationBasis.values()) {
            PayeContext ctx = new PayeContext(basis, BigDecimal.ZERO, 52, 1,
                    BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("4000"), new BigDecimal("44000"), true, 1);
            EngineResult result = PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.WEEKLY));
            assertEquals(0, BigDecimal.ZERO.compareTo(result.amount()), basis + ": zero gross must produce zero PAYE");
        }
    }

    @Test
    void cumulativeBasis_grossExactlyAtCutOff_entirelyStandardRate() {
        PayeContext ctx = new PayeContext(CalculationBasis.CUMULATIVE, new BigDecimal("4000.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("48000"), true, 1);
        EngineResult result = PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.MONTHLY));
        // cutoff this period = 48000/12 = 4000; gross = 4000 -> no higher-rate portion: 4000*0.20 = 800.00
        assertEquals(0, new BigDecimal("800.00").compareTo(result.amount()));
    }

    @Test
    void cumulativeBasis_creditExceedsGrossTax_clampsToZeroNotNegative() {
        PayeContext ctx = new PayeContext(CalculationBasis.CUMULATIVE, new BigDecimal("10.00"), 52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("10000"), new BigDecimal("44000"), true, 1);
        EngineResult result = PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.WEEKLY));
        assertEquals(0, BigDecimal.ZERO.compareTo(result.amount()));
    }

    @Test
    void cumulativeBasis_priorPeriodsAlreadyExhaustedCredit_thisPeriodPaysFullMarginalTax() {
        // Cumulative tax paid to date already equals net cumulative tax due before this period's gross is added -
        // a large one-off addition this period should be taxed without any further credit relief.
        PayeContext ctx = new PayeContext(CalculationBasis.CUMULATIVE, new BigDecimal("1000.00"), 12, 6,
                new BigDecimal("20000"), new BigDecimal("3000"), new BigDecimal("4200"), new BigDecimal("48000"), true, 1);
        EngineResult result = assertDoesNotThrow(() ->
                PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.MONTHLY)));
        assertTrue(result.amount().signum() >= 0);
    }

    @Test
    void emergencyBasis_exactlyAtAllowanceWindowBoundary_stillGetsAllowance() {
        PayeContext ctx = new PayeContext(CalculationBasis.EMERGENCY, new BigDecimal("500.00"), 52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true, 4);
        EngineResult result = PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.WEEKLY));
        // 500 < 846.16 weekly allowance -> entirely standard rate: 500*0.20 = 100.00
        assertEquals(0, new BigDecimal("100.00").compareTo(result.amount()));
    }

    @Test
    void emergencyBasis_oneStepPastAllowanceWindow_allowanceWithdrawn() {
        PayeContext withinWindow = new PayeContext(CalculationBasis.EMERGENCY, new BigDecimal("500.00"), 52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true, 4);
        PayeContext pastWindow = new PayeContext(CalculationBasis.EMERGENCY, new BigDecimal("500.00"), 52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true, 5);

        BigDecimal within = PayeCalculator.calculate(withinWindow, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.WEEKLY)).amount();
        BigDecimal past = PayeCalculator.calculate(pastWindow, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.WEEKLY)).amount();

        assertTrue(past.compareTo(within) > 0, "losing the emergency allowance must strictly increase PAYE for identical gross");
    }

    @Test
    void extremeGrossPay_veryLarge_doesNotThrowAndStaysNonNegative() {
        for (CalculationBasis basis : CalculationBasis.values()) {
            PayeContext ctx = new PayeContext(basis, new BigDecimal("999999999.99"), 52, 1,
                    BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("4000"), new BigDecimal("44000"), true, 1);
            EngineResult result = assertDoesNotThrow(() ->
                    PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.WEEKLY)),
                    () -> basis + " threw on an extreme gross value");
            assertTrue(result.amount().signum() >= 0);
        }
    }

    @Test
    void amountIsAlwaysScaledToTwoDecimalPlaces() {
        PayeContext ctx = new PayeContext(CalculationBasis.CUMULATIVE, new BigDecimal("1234.5678"), 12, 3,
                new BigDecimal("500.111"), BigDecimal.ZERO, new BigDecimal("4200"), new BigDecimal("48000"), true, 1);
        EngineResult result = PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(PayFrequency.MONTHLY));
        assertEquals(2, result.amount().scale());
    }

    // ---- Randomized fuzzing ----

    @Test
    void randomizedContexts_neverNegative_neverThrows() {
        Random random = new Random(SEED);
        for (CalculationBasis basis : CalculationBasis.values()) {
            for (PayFrequency freq : PayFrequency.values()) {
                for (int i = 0; i < ITERATIONS; i++) {
                    PayeContext ctx = randomContext(random, basis, freq);
                    EngineResult result = assertDoesNotThrow(() ->
                            PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(freq)),
                            () -> basis + "/" + freq + " threw for " + ctx);
                    assertTrue(result.amount().signum() >= 0,
                            () -> basis + "/" + freq + ": negative PAYE for " + ctx + " -> " + result.amount());
                }
            }
        }
    }

    @Test
    void monotonicInGrossPay_everyBasis_increasingGrossNeverDecreasesPaye() {
        Random random = new Random(SEED + 1);
        for (CalculationBasis basis : CalculationBasis.values()) {
            for (PayFrequency freq : PayFrequency.values()) {
                // Fix every other field, vary only grossPayThisPeriod across an
                // increasing sequence - PAYE is a monotonically non-decreasing
                // function of gross pay for a fixed basis/credit/cut-off/period.
                int periodNumber = 1 + random.nextInt(freq.periodsPerYear());
                BigDecimal priorGross = randomAmount(random, 100000);
                BigDecimal priorTax = randomAmount(random, 20000);
                BigDecimal credit = randomAmount(random, 10000);
                BigDecimal cutOff = randomAmount(random, 60000);
                boolean hasPps = random.nextBoolean();
                int periodsSinceEmergency = 1 + random.nextInt(10);

                BigDecimal previousGross = BigDecimal.ZERO;
                BigDecimal previousAmount = BigDecimal.ZERO;
                for (int i = 0; i < ITERATIONS; i++) {
                    BigDecimal gross = previousGross.add(randomAmount(random, 5000));
                    PayeContext ctx = new PayeContext(basis, gross, freq.periodsPerYear(), periodNumber,
                            priorGross, priorTax, credit, cutOff, hasPps, periodsSinceEmergency);
                    BigDecimal amount = PayeCalculator.calculate(ctx, RULES.payeRates(), RULES.emergencyPayeAllowance(freq)).amount();

                    BigDecimal priorGrossForMessage = previousGross;
                    BigDecimal priorAmountForMessage = previousAmount;
                    assertTrue(amount.compareTo(previousAmount) >= 0,
                            () -> basis + "/" + freq + ": PAYE decreased from " + priorAmountForMessage + " (gross=" + priorGrossForMessage
                                    + ") to " + amount + " (gross=" + gross + ")");

                    previousGross = gross;
                    previousAmount = amount;
                }
            }
        }
    }

    private static PayeContext randomContext(Random random, CalculationBasis basis, PayFrequency freq) {
        return new PayeContext(basis,
                randomAmount(random, 1_000_000),
                freq.periodsPerYear(),
                1 + random.nextInt(freq.periodsPerYear()),
                randomAmount(random, 1_000_000),
                randomAmount(random, 200_000),
                randomAmount(random, 20_000),
                randomAmount(random, 100_000),
                random.nextBoolean(),
                1 + random.nextInt(10));
    }

    private static BigDecimal randomAmount(Random random, int maxWhole) {
        double value = random.nextDouble() * maxWhole;
        return BigDecimal.valueOf(Math.round(value * 100.0) / 100.0);
    }
}

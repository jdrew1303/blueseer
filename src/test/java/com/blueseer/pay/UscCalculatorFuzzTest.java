package com.blueseer.pay;

import com.blueseer.pay.UscCalculator.CalculationBasis;
import com.blueseer.pay.UscCalculator.UscContext;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import java.util.Random;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Boundary and randomized (seeded, reproducible) coverage for C-03 USC.
 * There is no pre-existing {@code UscCalculatorTest} with hand-verified
 * worked examples in this module yet - the band-boundary cases below serve
 * that role as well as the fuzzing/monotonicity role.
 */
class UscCalculatorFuzzTest {

    private static final Paye2026Rules RULES = new Paye2026Rules();
    private static final ITaxYearRules.UscRates RATES = RULES.uscRates();
    private static final long SEED = 20260101L;
    private static final int ITERATIONS = 500;

    // ---- Explicit boundary cases ----

    @Test
    void zeroUscablePay_everyBasis_returnsZeroWithoutException() {
        for (CalculationBasis basis : CalculationBasis.values()) {
            UscContext ctx = new UscContext(basis, BigDecimal.ZERO, 12, 1, BigDecimal.ZERO, BigDecimal.ZERO, false, false);
            EngineResult result = UscCalculator.calculate(ctx, RATES);
            assertEquals(0, BigDecimal.ZERO.compareTo(result.amount()), basis + ": zero USC-able pay must produce zero USC");
        }
    }

    @Test
    void exemptMarker_cumulativeBasis_shortCircuitsToZeroRegardlessOfPay() {
        UscContext ctx = new UscContext(CalculationBasis.CUMULATIVE, new BigDecimal("50000.00"), 12, 12,
                BigDecimal.ZERO, BigDecimal.ZERO, true, false);
        EngineResult result = UscCalculator.calculate(ctx, RATES);
        assertEquals(0, BigDecimal.ZERO.compareTo(result.amount()));
        assertEquals(1, result.steps().size(), "exempt short-circuit should log exactly one step, not run the full band chain");
    }

    @Test
    void exemptMarker_week1Basis_shortCircuitsToZeroRegardlessOfPay() {
        UscContext ctx = new UscContext(CalculationBasis.WEEK1, new BigDecimal("5000.00"), 52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, true, false);
        EngineResult result = UscCalculator.calculate(ctx, RATES);
        assertEquals(0, BigDecimal.ZERO.compareTo(result.amount()));
    }

    @Test
    void emergencyBasis_ignoresExemptMarker_byDesign() {
        // A brand-new employee on Emergency USC basis has, by definition, no
        // RPN yet - and a USC exemption code can only ever arrive via RPN.
        // "exempt AND on Emergency basis" is therefore not a reachable real-
        // world combination; this documents the engine's actual (deliberate)
        // behaviour rather than silently assuming it was overlooked.
        UscContext exempt = new UscContext(CalculationBasis.EMERGENCY, new BigDecimal("1000.00"), 52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, true, false);
        UscContext notExempt = new UscContext(CalculationBasis.EMERGENCY, new BigDecimal("1000.00"), 52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false);
        BigDecimal exemptAmount = UscCalculator.calculate(exempt, RATES).amount();
        BigDecimal notExemptAmount = UscCalculator.calculate(notExempt, RATES).amount();
        assertEquals(0, exemptAmount.compareTo(notExemptAmount), "exemptMarker has no effect under EMERGENCY basis");
    }

    @Test
    void cumulativeBasis_payExactlyAtBand1Ceiling_entirelyBand1Rate() {
        // annualBand1Ceiling = 12012 per Paye2026Rules -> monthly ceiling = 1001.00
        UscContext ctx = new UscContext(CalculationBasis.CUMULATIVE, new BigDecimal("1001.00"), 12, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false);
        EngineResult result = UscCalculator.calculate(ctx, RATES);
        // 1001.00 * 0.005 = 5.005 -> 5.01 (HALF_UP)
        assertEquals(0, new BigDecimal("5.01").compareTo(result.amount()));
    }

    @Test
    void reducedRateCap_appliesFlatRateAboveBand1InsteadOfBands2Through4() {
        UscContext capped = new UscContext(CalculationBasis.WEEK1, new BigDecimal("2000.00"), 52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, false, true);
        UscContext uncapped = new UscContext(CalculationBasis.WEEK1, new BigDecimal("2000.00"), 52, 1,
                BigDecimal.ZERO, BigDecimal.ZERO, false, false);
        BigDecimal cappedAmount = UscCalculator.calculate(capped, RATES).amount();
        BigDecimal uncappedAmount = UscCalculator.calculate(uncapped, RATES).amount();
        // reducedRateCapValue (2%) is below band3/band4's marginal rates, so the capped route must charge less.
        assertTrue(cappedAmount.compareTo(uncappedAmount) < 0,
                "reduced-rate cap must produce less USC than the standard band chain at this income level");
    }

    @Test
    void extremeUscablePay_veryLarge_doesNotThrowAndStaysNonNegative() {
        for (CalculationBasis basis : CalculationBasis.values()) {
            UscContext ctx = new UscContext(basis, new BigDecimal("999999999.99"), 52, 1,
                    BigDecimal.ZERO, BigDecimal.ZERO, false, false);
            EngineResult result = assertDoesNotThrow(() -> UscCalculator.calculate(ctx, RATES),
                    () -> basis + " threw on an extreme USC-able pay value");
            assertTrue(result.amount().signum() >= 0);
        }
    }

    @Test
    void amountIsAlwaysScaledToTwoDecimalPlaces() {
        UscContext ctx = new UscContext(CalculationBasis.CUMULATIVE, new BigDecimal("1234.5678"), 12, 3,
                new BigDecimal("500.111"), BigDecimal.ZERO, false, false);
        EngineResult result = UscCalculator.calculate(ctx, RATES);
        assertEquals(2, result.amount().scale());
    }

    // ---- Randomized fuzzing ----

    @Test
    void randomizedContexts_neverNegative_neverThrows() {
        Random random = new Random(SEED);
        for (CalculationBasis basis : CalculationBasis.values()) {
            for (PayFrequency freq : PayFrequency.values()) {
                for (int i = 0; i < ITERATIONS; i++) {
                    UscContext ctx = randomContext(random, basis, freq);
                    EngineResult result = assertDoesNotThrow(() -> UscCalculator.calculate(ctx, RATES),
                            () -> basis + "/" + freq + " threw for " + ctx);
                    assertTrue(result.amount().signum() >= 0,
                            () -> basis + "/" + freq + ": negative USC for " + ctx + " -> " + result.amount());
                }
            }
        }
    }

    @Test
    void monotonicInUscablePay_everyBasis_increasingPayNeverDecreasesUsc() {
        Random random = new Random(SEED + 1);
        for (CalculationBasis basis : CalculationBasis.values()) {
            for (PayFrequency freq : PayFrequency.values()) {
                int periodNumber = 1 + random.nextInt(freq.periodsPerYear());
                BigDecimal priorPay = randomAmount(random, 100000);
                BigDecimal priorUsc = randomAmount(random, 10000);
                boolean reducedCap = random.nextBoolean();

                BigDecimal previousPay = BigDecimal.ZERO;
                BigDecimal previousAmount = BigDecimal.ZERO;
                for (int i = 0; i < ITERATIONS; i++) {
                    BigDecimal pay = previousPay.add(randomAmount(random, 3000));
                    UscContext ctx = new UscContext(basis, pay, freq.periodsPerYear(), periodNumber,
                            priorPay, priorUsc, false, reducedCap);
                    BigDecimal amount = UscCalculator.calculate(ctx, RATES).amount();

                    BigDecimal priorPayForMessage = previousPay;
                    BigDecimal priorAmountForMessage = previousAmount;
                    assertTrue(amount.compareTo(previousAmount) >= 0,
                            () -> basis + "/" + freq + ": USC decreased from " + priorAmountForMessage + " (pay=" + priorPayForMessage
                                    + ") to " + amount + " (pay=" + pay + ")");

                    previousPay = pay;
                    previousAmount = amount;
                }
            }
        }
    }

    private static UscContext randomContext(Random random, CalculationBasis basis, PayFrequency freq) {
        return new UscContext(basis,
                randomAmount(random, 1_000_000),
                freq.periodsPerYear(),
                1 + random.nextInt(freq.periodsPerYear()),
                randomAmount(random, 1_000_000),
                randomAmount(random, 100_000),
                random.nextBoolean(),
                random.nextBoolean());
    }

    private static BigDecimal randomAmount(Random random, int maxWhole) {
        double value = random.nextDouble() * maxWhole;
        return BigDecimal.valueOf(Math.round(value * 100.0) / 100.0);
    }
}

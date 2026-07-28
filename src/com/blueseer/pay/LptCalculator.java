package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * C-04 LPT (Local Property Tax at Source), per
 * {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md}. LPT is
 * not an independently calculated tax - the employer deducts exactly what
 * Revenue instructs via the RPN, using one of two Revenue-specified
 * collection methods. This engine is a routing function over RPN-supplied
 * instructions, not a rate-table calculation.
 *
 * <p>{@link LptContext#netPayThisPeriod()} is only available after
 * {@link PayeCalculator}/{@link PrsiCalculator}/{@link UscCalculator} have
 * run for the period, so per C-06's stated engine order LPT must be
 * sequenced last among the money-affecting engines (PAYE {@literal ->} PRSI
 * {@literal ->} USC {@literal ->} LPT), before ASC.
 */
public final class LptCalculator {

    private static final String ENGINE = "C-04 LPT";

    private LptCalculator() {
    }

    public enum LptDeductionMethod { FIXED_PERIODIC, PERCENTAGE_OF_NET, NONE }

    /**
     * @param lptAnnualChargeFromRpn populated when {@code lptDeductionMethod == FIXED_PERIODIC}
     * @param netPayThisPeriod       populated when {@code lptDeductionMethod == PERCENTAGE_OF_NET};
     *                                used by Revenue for arrears collection scenarios
     * @param lptPercentageRate      RPN-supplied, only populated when {@code lptDeductionMethod == PERCENTAGE_OF_NET}
     */
    public record LptContext(
            LptDeductionMethod lptDeductionMethod,
            BigDecimal lptAnnualChargeFromRpn,
            int payPeriodsPerYear,
            BigDecimal netPayThisPeriod,
            BigDecimal lptPercentageRate) {
    }

    public static EngineResult calculate(LptContext ctx) {
        Map<String, Object> initial = new HashMap<>();
        initial.put("lptDeductionMethod", ctx.lptDeductionMethod().name());
        initial.put("lptAnnualChargeFromRpn", ctx.lptAnnualChargeFromRpn());
        initial.put("payPeriodsPerYear", BigDecimal.valueOf(ctx.payPeriodsPerYear()));
        initial.put("netPayThisPeriod", ctx.netPayThisPeriod());
        initial.put("lptPercentageRate", ctx.lptPercentageRate());

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        runner.step("Periodic LPT under the fixed-charge method",
                "lptAnnualChargeFromRpn / payPeriodsPerYear",
                "lptFixedPeriodic");
        runner.step("Periodic LPT under the percentage-of-net method",
                "netPayThisPeriod * lptPercentageRate",
                "lptPercentageOfNet");
        runner.finalStep("LPT payable this period",
                "IF(lptDeductionMethod == \"FIXED_PERIODIC\", lptFixedPeriodic, IF(lptDeductionMethod == \"PERCENTAGE_OF_NET\", lptPercentageOfNet, 0))");

        return runner.result();
    }
}

package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * C-02 PRSI (Class A), per
 * {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md}. Unlike
 * PAYE/USC, PRSI is a <strong>flat rate on the whole of reckonable pay</strong>
 * once the applicable subclass threshold is crossed - not a marginal/banded
 * calculation - confirmed against the Department of Social Protection's own
 * "PRSI Class A Rates" table, which is why {@link #calculate} has no
 * cumulative-vs-Week1 distinction the way {@link PayeCalculator} does: PRSI
 * is "always deducted on a Week One/Month One basis" per that source, with
 * no cumulative variant at all.
 *
 * <p>{@code reckonablePayThisPeriod} is currently taken to equal
 * {@code grossPayThisPeriod} - the calc-engine spec flags this as a known
 * simplification (Open Question 2) pending confirmation of exactly which
 * BIK/notional-pay components, if any, must be excluded from reckonable pay.
 */
public final class PrsiCalculator {

    private static final String ENGINE = "C-02 PRSI";

    private PrsiCalculator() {
    }

    /**
     * @param reckonablePayThisPeriod see the class-level note on the
     *                                {@code grossPayThisPeriod} simplification
     *                                currently in effect
     */
    public record PrsiContext(BigDecimal reckonablePayThisPeriod) {
    }

    public record PrsiResult(EngineResult employee, EngineResult employer) {
    }

    public static PrsiResult calculate(PrsiContext ctx, ITaxYearRules.PrsiRates rates) {
        Map<String, Object> initial = new HashMap<>();
        initial.put("reckonablePayThisPeriod", ctx.reckonablePayThisPeriod());
        initial.put("weeklyLowerThreshold", rates.weeklyLowerThreshold());
        initial.put("prsiCreditUpperThreshold", rates.prsiCreditUpperThreshold());
        initial.put("prsiCreditMax", rates.prsiCreditMax());
        initial.put("employeeRate", rates.employeeRate());
        initial.put("employerLowerRate", rates.employerLowerRate());
        initial.put("employerHigherRate", rates.employerHigherRate());
        initial.put("employerHigherRateThreshold", rates.employerHigherRateThreshold());

        EvalExStepRunner employeeRunner = new EvalExStepRunner(ENGINE, initial);
        employeeRunner.step("Employee PRSI before credit",
                "IF(reckonablePayThisPeriod <= weeklyLowerThreshold, 0, reckonablePayThisPeriod * employeeRate)",
                "employeePrsiRaw");
        employeeRunner.step("Tapered PRSI credit",
                "IF(reckonablePayThisPeriod > weeklyLowerThreshold && reckonablePayThisPeriod < prsiCreditUpperThreshold, "
                        + "MAX(0, prsiCreditMax - ((reckonablePayThisPeriod - weeklyLowerThreshold - 0.01) / 6)), 0)",
                "prsiCredit");
        employeeRunner.finalStep("Employee PRSI payable this period",
                "MAX(0, employeePrsiRaw - prsiCredit)");

        EvalExStepRunner employerRunner = new EvalExStepRunner(ENGINE, initial);
        employerRunner.finalStep("Employer PRSI payable this period",
                "IF(reckonablePayThisPeriod <= employerHigherRateThreshold, reckonablePayThisPeriod * employerLowerRate, reckonablePayThisPeriod * employerHigherRate)");

        return new PrsiResult(employeeRunner.result(), employerRunner.result());
    }
}

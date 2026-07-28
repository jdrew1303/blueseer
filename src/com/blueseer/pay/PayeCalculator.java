package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * C-01 PAYE, per {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md}.
 * Cumulative, Week1/Month1, and Emergency basis, exactly as specified there -
 * every step below is the literal EvalEx formula string from that document,
 * not a hand-rolled Java equivalent, so the audit trail this produces reads
 * identically to the spec a reviewer would check it against.
 *
 * <p>Manual entry of an employee's own tax credit/cut-off point is
 * deliberately not possible anywhere in this class - {@link PayeContext#annualTaxCredit()}
 * and {@link PayeContext#annualCutOffPoint()} must come from an RPN (S-14/S-15),
 * matching the exemplar's own explicit statement that manual entry "is thus
 * no longer possible" under PAYE Modernisation.
 */
public final class PayeCalculator {

    private static final String ENGINE = "C-01 PAYE";

    private PayeCalculator() {
    }

    public enum CalculationBasis { CUMULATIVE, WEEK1, EMERGENCY }

    /**
     * @param calculationBasis                     RPN-supplied, except for a
     *                                              brand-new employee with no
     *                                              RPN yet, who is always
     *                                              {@link CalculationBasis#EMERGENCY}
     * @param grossPayThisPeriod                    taxable gross pay for the
     *                                              period being processed
     * @param payPeriodsPerYear                     52 / 26 / 12
     * @param periodNumber                          1-based sequence number
     *                                              within the tax year
     * @param cumulativeGrossPayPriorToThisPeriod    sum of all prior periods
     *                                              this tax year, excludes
     *                                              the current period
     * @param cumulativeTaxPaidToDate               PAYE already deducted in
     *                                              prior periods this tax year
     * @param annualTaxCredit                       RPN-supplied, already
     *                                              Revenue-aggregated
     * @param annualCutOffPoint                     RPN-supplied SRCOP for
     *                                              this employment
     * @param hasPpsNumber                          only consulted under
     *                                              {@link CalculationBasis#EMERGENCY}
     * @param periodsSinceEmergencyStart             1-based; only consulted
     *                                              under {@link CalculationBasis#EMERGENCY}
     */
    public record PayeContext(
            CalculationBasis calculationBasis,
            BigDecimal grossPayThisPeriod,
            int payPeriodsPerYear,
            int periodNumber,
            BigDecimal cumulativeGrossPayPriorToThisPeriod,
            BigDecimal cumulativeTaxPaidToDate,
            BigDecimal annualTaxCredit,
            BigDecimal annualCutOffPoint,
            boolean hasPpsNumber,
            int periodsSinceEmergencyStart) {
    }

    public static EngineResult calculate(PayeContext ctx, ITaxYearRules.PayeRates payeRates,
            ITaxYearRules.EmergencyPayeAllowance emergencyAllowance) {
        Map<String, Object> initial = new HashMap<>();
        initial.put("grossPayThisPeriod", ctx.grossPayThisPeriod());
        initial.put("payPeriodsPerYear", BigDecimal.valueOf(ctx.payPeriodsPerYear()));
        initial.put("periodNumber", BigDecimal.valueOf(ctx.periodNumber()));
        initial.put("standardRate", payeRates.standardRate());
        initial.put("higherRate", payeRates.higherRate());

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        return switch (ctx.calculationBasis()) {
            case CUMULATIVE -> calculateCumulative(ctx, runner);
            case WEEK1 -> calculateWeek1(ctx, runner);
            case EMERGENCY -> calculateEmergency(ctx, runner, emergencyAllowance);
        };
    }

    private static EngineResult calculateCumulative(PayeContext ctx, EvalExStepRunner runner) {
        runner.put("cumulativeGrossPayPriorToThisPeriod", ctx.cumulativeGrossPayPriorToThisPeriod());
        runner.put("annualTaxCredit", ctx.annualTaxCredit());
        runner.put("annualCutOffPoint", ctx.annualCutOffPoint());
        runner.put("cumulativeTaxPaidToDate", ctx.cumulativeTaxPaidToDate());

        runner.step("Cumulative gross pay to date",
                "cumulativeGrossPayPriorToThisPeriod + grossPayThisPeriod",
                "cumulativeGrossToDate");
        runner.step("Cumulative tax credit entitlement to date",
                "(annualTaxCredit / payPeriodsPerYear) * periodNumber",
                "cumulativeCreditToDate");
        runner.step("Cumulative standard-rate cut-off to date",
                "(annualCutOffPoint / payPeriodsPerYear) * periodNumber",
                "cumulativeCutOffToDate");
        runner.step("Tax at standard rate on income within the cut-off",
                "MIN(cumulativeGrossToDate, cumulativeCutOffToDate) * standardRate",
                "standardRateTax");
        runner.step("Tax at higher rate on income above the cut-off",
                "MAX(0, cumulativeGrossToDate - cumulativeCutOffToDate) * higherRate",
                "higherRateTax");
        runner.step("Gross cumulative tax liability",
                "standardRateTax + higherRateTax",
                "grossCumulativeTax");
        runner.step("Net cumulative tax due after credits",
                "MAX(0, grossCumulativeTax - cumulativeCreditToDate)",
                "netCumulativeTaxDue");
        runner.finalStep("PAYE payable this period",
                "MAX(0, netCumulativeTaxDue - cumulativeTaxPaidToDate)");

        return runner.result();
    }

    private static EngineResult calculateWeek1(PayeContext ctx, EvalExStepRunner runner) {
        runner.put("annualTaxCredit", ctx.annualTaxCredit());
        runner.put("annualCutOffPoint", ctx.annualCutOffPoint());

        runner.step("Period tax credit (no look-back)",
                "annualTaxCredit / payPeriodsPerYear",
                "periodCredit");
        runner.step("Period cut-off (no look-back)",
                "annualCutOffPoint / payPeriodsPerYear",
                "periodCutOff");
        runner.step("Standard-rate tax this period",
                "MIN(grossPayThisPeriod, periodCutOff) * standardRate",
                "standardRateTaxThisPeriod");
        runner.step("Higher-rate tax this period",
                "MAX(0, grossPayThisPeriod - periodCutOff) * higherRate",
                "higherRateTaxThisPeriod");
        runner.finalStep("PAYE payable this period",
                "MAX(0, (standardRateTaxThisPeriod + higherRateTaxThisPeriod) - periodCredit)");

        return runner.result();
    }

    private static EngineResult calculateEmergency(PayeContext ctx, EvalExStepRunner runner,
            ITaxYearRules.EmergencyPayeAllowance emergencyAllowance) {
        runner.put("hasPpsNumber", ctx.hasPpsNumber());
        runner.put("periodsSinceEmergencyStart", BigDecimal.valueOf(ctx.periodsSinceEmergencyStart()));
        runner.put("emergencyCutOffAllowancePerPeriod", emergencyAllowance.cutOffAllowancePerPeriod());
        runner.put("emergencyAllowanceWindowLength", BigDecimal.valueOf(emergencyAllowance.allowanceWindowLength()));

        // Revenue form RPC020012: the credit is always zero, with or without
        // a PPSN - this step exists so the audit trail shows that
        // explicitly rather than silently omitting a credit line.
        runner.step("Emergency credit available (always zero)",
                "0",
                "emergencyCreditAvailable");
        runner.step("Emergency cut-off available this period",
                "IF(hasPpsNumber == false, 0, IF(periodsSinceEmergencyStart <= emergencyAllowanceWindowLength, emergencyCutOffAllowancePerPeriod, 0))",
                "emergencyCutOffAvailable");
        runner.step("Standard-rate tax this period",
                "MIN(grossPayThisPeriod, emergencyCutOffAvailable) * standardRate",
                "standardRateTaxThisPeriod");
        runner.step("Higher-rate tax this period",
                "MAX(0, grossPayThisPeriod - emergencyCutOffAvailable) * higherRate",
                "higherRateTaxThisPeriod");
        runner.finalStep("PAYE payable this period",
                "MAX(0, (standardRateTaxThisPeriod + higherRateTaxThisPeriod) - emergencyCreditAvailable)");

        return runner.result();
    }
}

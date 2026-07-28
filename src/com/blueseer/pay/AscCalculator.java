package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * C-05 ASC (Additional Superannuation Contribution), per
 * {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md}. Applies
 * only to employees flagged as members of a public-service pension scheme.
 * Band structure (two bands plus an exempt ceiling, both {@link
 * ITaxYearRules.AscGroup}s sharing the same exempt/upper ceilings) is
 * confirmed against publicservicepensions.gov.ie, not derived here - this
 * class only transcribes the calc-engine spec's EvalEx formulas.
 */
public final class AscCalculator {

    private static final String ENGINE = "C-05 ASC";

    private AscCalculator() {
    }

    /**
     * @param pensionableRemunerationThisPeriod basic pay excluding non-pensionable
     *                                            overtime, plus pensionable allowances
     * @param hasManualOverrideAmount            true when the user has typed a euro
     *                                            amount directly over the calculated ASC
     * @param manualOverrideAmount               populated only when {@code hasManualOverrideAmount == true}
     * @param hasManualOverridePercentage        true when the user has entered a
     *                                            Week1/Month1 override percentage instead
     * @param manualOverridePercentage           populated only when {@code hasManualOverridePercentage == true}
     */
    public record AscContext(
            BigDecimal pensionableRemunerationThisPeriod,
            int payPeriodsPerYear,
            int periodNumber,
            BigDecimal cumulativePensionableRemunerationPriorToThisPeriod,
            BigDecimal cumulativeAscPaidToDate,
            boolean hasManualOverrideAmount,
            BigDecimal manualOverrideAmount,
            boolean hasManualOverridePercentage,
            BigDecimal manualOverridePercentage) {
    }

    public static EngineResult calculate(AscContext ctx, ITaxYearRules.AscRates rates) {
        Map<String, Object> initial = new HashMap<>();
        initial.put("pensionableRemunerationThisPeriod", ctx.pensionableRemunerationThisPeriod());
        initial.put("payPeriodsPerYear", BigDecimal.valueOf(ctx.payPeriodsPerYear()));
        initial.put("periodNumber", BigDecimal.valueOf(ctx.periodNumber()));
        initial.put("cumulativePensionableRemunerationPriorToThisPeriod", ctx.cumulativePensionableRemunerationPriorToThisPeriod());
        initial.put("cumulativeAscPaidToDate", ctx.cumulativeAscPaidToDate());
        initial.put("activeExemptCeiling", rates.exemptCeiling());
        initial.put("activeUpperBandCeiling", rates.upperBandCeiling());
        initial.put("activeMidRate", rates.midRate());
        initial.put("activeTopRate", rates.topRate());
        initial.put("hasManualOverrideAmount", ctx.hasManualOverrideAmount());
        initial.put("manualOverrideAmount", ctx.manualOverrideAmount());
        initial.put("hasManualOverridePercentage", ctx.hasManualOverridePercentage());
        initial.put("manualOverridePercentage", ctx.manualOverridePercentage());

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        runner.step("Cumulative pensionable remuneration to date",
                "cumulativePensionableRemunerationPriorToThisPeriod + pensionableRemunerationThisPeriod",
                "cumulativePensionablePay");
        runner.step("Cumulative exempt ceiling to date",
                "(activeExemptCeiling / payPeriodsPerYear) * periodNumber",
                "exemptCeilingToDate");
        runner.step("Cumulative upper band ceiling to date",
                "(activeUpperBandCeiling / payPeriodsPerYear) * periodNumber",
                "upperBandCeilingToDate");
        runner.step("Mid-band portion (exempt ceiling to upper band ceiling)",
                "MAX(0, MIN(cumulativePensionablePay, upperBandCeilingToDate) - exemptCeilingToDate)",
                "midBandPortion");
        runner.step("Top-band portion (above upper band ceiling)",
                "MAX(0, cumulativePensionablePay - upperBandCeilingToDate)",
                "topBandPortion");
        runner.step("Cumulative ASC gross liability",
                "(midBandPortion * activeMidRate) + (topBandPortion * activeTopRate)",
                "cumulativeAscGross");
        runner.step("System-calculated ASC this period",
                "MAX(0, cumulativeAscGross - cumulativeAscPaidToDate)",
                "systemCalculatedAsc");
        runner.finalStep("Final ASC this period, applying any manual override",
                "IF(hasManualOverrideAmount == true, manualOverrideAmount, "
                        + "IF(hasManualOverridePercentage == true, pensionableRemunerationThisPeriod * manualOverridePercentage, systemCalculatedAsc))");

        return runner.result();
    }
}

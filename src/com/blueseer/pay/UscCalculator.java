package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * C-03 USC, per {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md}.
 * Cumulative and Emergency bases are the literal EvalEx formula strings from
 * that document. The spec does not give a separate Week1 formula set for
 * USC - C-21 (Week 53) states explicitly that "USC's existing Week1
 * mechanics in C-03 are the same shape" as C-01's Week1/Month1 basis, so
 * {@link #calculateWeek1} mirrors {@link PayeCalculator}'s Week1 treatment:
 * the band ceilings are period-only (no {@code periodNumber} multiplication)
 * and there is no look-back against cumulative pay or cumulative USC paid.
 */
public final class UscCalculator {

    private static final String ENGINE = "C-03 USC";

    private UscCalculator() {
    }

    public enum CalculationBasis { CUMULATIVE, WEEK1, EMERGENCY }

    /**
     * @param uscablePayThisPeriod USC applies to gross pay generally
     *                              <em>before</em> pension-contribution
     *                              relief, unlike PAYE - not a reused
     *                              {@code grossPayThisPeriod} value
     */
    public record UscContext(
            CalculationBasis calculationBasis,
            BigDecimal uscablePayThisPeriod,
            int payPeriodsPerYear,
            int periodNumber,
            BigDecimal cumulativeUscablePayPriorToThisPeriod,
            BigDecimal cumulativeUscPaidToDate,
            boolean uscExemptMarker,
            boolean reducedRateCapApplies) {
    }

    public static EngineResult calculate(UscContext ctx, ITaxYearRules.UscRates rates) {
        Map<String, Object> initial = new HashMap<>();
        initial.put("uscablePayThisPeriod", ctx.uscablePayThisPeriod());
        initial.put("payPeriodsPerYear", BigDecimal.valueOf(ctx.payPeriodsPerYear()));
        initial.put("periodNumber", BigDecimal.valueOf(ctx.periodNumber()));
        initial.put("annualBand1Ceiling", rates.annualBand1Ceiling());
        initial.put("band1Rate", rates.band1Rate());
        initial.put("annualBand2Ceiling", rates.annualBand2Ceiling());
        initial.put("band2Rate", rates.band2Rate());
        initial.put("annualBand3Ceiling", rates.annualBand3Ceiling());
        initial.put("band3Rate", rates.band3Rate());
        initial.put("band4Rate", rates.band4Rate());
        initial.put("reducedRateCapValue", rates.reducedRateCapValue());
        initial.put("reducedRateCapApplies", ctx.reducedRateCapApplies());
        initial.put("emergencyUscRate", rates.emergencyUscRate());

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        return switch (ctx.calculationBasis()) {
            case CUMULATIVE -> calculateCumulative(ctx, runner);
            case WEEK1 -> calculateWeek1(ctx, runner);
            case EMERGENCY -> calculateEmergency(runner);
        };
    }

    private static EngineResult calculateCumulative(UscContext ctx, EvalExStepRunner runner) {
        runner.put("uscExemptMarker", ctx.uscExemptMarker());

        BigDecimal gate = runner.step("Exemption short-circuit",
                "IF(uscExemptMarker == true, 0, -1)",
                "exemptionGate");
        if (gate.signum() == 0) {
            return runner.result();
        }

        runner.put("cumulativeUscablePayPriorToThisPeriod", ctx.cumulativeUscablePayPriorToThisPeriod());
        runner.put("cumulativeUscPaidToDate", ctx.cumulativeUscPaidToDate());

        runner.step("Cumulative USC-able pay to date",
                "cumulativeUscablePayPriorToThisPeriod + uscablePayThisPeriod",
                "cumulativeUscablePay");
        runner.step("Cumulative band 1 ceiling to date",
                "(annualBand1Ceiling / payPeriodsPerYear) * periodNumber",
                "band1CeilingToDate");
        runner.step("Cumulative band 2 ceiling to date",
                "(annualBand2Ceiling / payPeriodsPerYear) * periodNumber",
                "band2CeilingToDate");
        runner.step("Cumulative band 3 ceiling to date",
                "(annualBand3Ceiling / payPeriodsPerYear) * periodNumber",
                "band3CeilingToDate");
        runner.step("Band 1 portion",
                "MIN(cumulativeUscablePay, band1CeilingToDate)",
                "band1Portion");
        runner.step("Band 2 portion",
                "MAX(0, MIN(cumulativeUscablePay, band2CeilingToDate) - band1CeilingToDate)",
                "band2Portion");
        runner.step("Band 3 portion",
                "MAX(0, MIN(cumulativeUscablePay, band3CeilingToDate) - band2CeilingToDate)",
                "band3Portion");
        runner.step("Band 4 portion",
                "MAX(0, cumulativeUscablePay - band3CeilingToDate)",
                "band4Portion");
        runner.step("Cumulative USC gross liability",
                "IF(reducedRateCapApplies == true, (band1Portion * band1Rate) + (MAX(0, cumulativeUscablePay - band1CeilingToDate) * reducedRateCapValue), "
                        + "(band1Portion * band1Rate) + (band2Portion * band2Rate) + (band3Portion * band3Rate) + (band4Portion * band4Rate))",
                "cumulativeUscGross");
        runner.finalStep("USC payable this period",
                "MAX(0, cumulativeUscGross - cumulativeUscPaidToDate)");

        return runner.result();
    }

    private static EngineResult calculateWeek1(UscContext ctx, EvalExStepRunner runner) {
        runner.put("uscExemptMarker", ctx.uscExemptMarker());

        BigDecimal gate = runner.step("Exemption short-circuit",
                "IF(uscExemptMarker == true, 0, -1)",
                "exemptionGate");
        if (gate.signum() == 0) {
            return runner.result();
        }

        runner.step("Period band 1 ceiling (no look-back)",
                "annualBand1Ceiling / payPeriodsPerYear",
                "band1CeilingThisPeriod");
        runner.step("Period band 2 ceiling (no look-back)",
                "annualBand2Ceiling / payPeriodsPerYear",
                "band2CeilingThisPeriod");
        runner.step("Period band 3 ceiling (no look-back)",
                "annualBand3Ceiling / payPeriodsPerYear",
                "band3CeilingThisPeriod");
        runner.step("Band 1 portion this period",
                "MIN(uscablePayThisPeriod, band1CeilingThisPeriod)",
                "band1Portion");
        runner.step("Band 2 portion this period",
                "MAX(0, MIN(uscablePayThisPeriod, band2CeilingThisPeriod) - band1CeilingThisPeriod)",
                "band2Portion");
        runner.step("Band 3 portion this period",
                "MAX(0, MIN(uscablePayThisPeriod, band3CeilingThisPeriod) - band2CeilingThisPeriod)",
                "band3Portion");
        runner.step("Band 4 portion this period",
                "MAX(0, uscablePayThisPeriod - band3CeilingThisPeriod)",
                "band4Portion");
        runner.finalStep("USC payable this period",
                "IF(reducedRateCapApplies == true, (band1Portion * band1Rate) + (MAX(0, uscablePayThisPeriod - band1CeilingThisPeriod) * reducedRateCapValue), "
                        + "(band1Portion * band1Rate) + (band2Portion * band2Rate) + (band3Portion * band3Rate) + (band4Portion * band4Rate))");

        return runner.result();
    }

    private static EngineResult calculateEmergency(EvalExStepRunner runner) {
        runner.finalStep("USC payable this period (flat rate, no bands, no cut-off)",
                "uscablePayThisPeriod * emergencyUscRate");

        return runner.result();
    }
}

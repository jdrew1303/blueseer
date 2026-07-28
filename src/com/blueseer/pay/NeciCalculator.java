package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C-24 NECI Pension - <strong>lower-confidence tier</strong>, per the
 * calc-engine spec §C-24: sourced from two independently-consistent
 * secondary payroll-vendor documents, not a primary NECI/Revenue
 * publication. Percentage-of-earnings, member side floored at a minimum
 * weekly amount; no minimum-floor equivalent found for the employer side in
 * the (unverified) sourcing.
 */
final class NeciCalculator {

    private static final String ENGINE = "C-24 NECI Pension";

    private NeciCalculator() {
    }

    record NeciResult(BigDecimal memberContributionRaw, BigDecimal memberContributionFinal, BigDecimal employerContributionFinal,
            List<CalculationStep> steps) {
    }

    static NeciResult calculate(BigDecimal pensionableEarningsThisPeriod, ITaxYearRules.NeciRates rates) {
        Map<String, Object> initial = new HashMap<>();
        initial.put("pensionableEarningsThisPeriod", pensionableEarningsThisPeriod == null ? BigDecimal.ZERO : pensionableEarningsThisPeriod);
        initial.put("memberContributionRate", rates.memberContributionRate());
        initial.put("memberMinimumWeeklyContribution", rates.memberMinimumWeeklyContribution());
        initial.put("employerContributionRate", rates.employerContributionRate());

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        BigDecimal memberRaw = runner.step("Member contribution before minimum floor",
                "pensionableEarningsThisPeriod * memberContributionRate",
                "memberContributionRaw");
        BigDecimal memberFinal = runner.step("Member contribution, floored at the stated minimum",
                "MAX(memberContributionRaw, memberMinimumWeeklyContribution)",
                "memberContributionFinal");
        BigDecimal employerFinal = runner.finalStep("Employer contribution",
                "pensionableEarningsThisPeriod * employerContributionRate");

        return new NeciResult(memberRaw, memberFinal, employerFinal, runner.result().steps());
    }
}

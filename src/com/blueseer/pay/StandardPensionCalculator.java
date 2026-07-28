package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C-15 Standard Pension Contribution Relief, per Revenue's "Tax relief
 * limits on pension contributions" page (see calc-engine spec §C-15).
 * {@code ageRelatedPercentageLimit} is resolved from age via a Java band
 * lookup before the 5-step EvalEx chain runs, since it is a 6-band table,
 * not a formula (the same pattern as {@link BikCarRateTable}).
 */
final class StandardPensionCalculator {

    private static final String ENGINE = "C-15 Standard Pension Contribution Relief";

    private StandardPensionCalculator() {
    }

    record StandardPensionContext(
            int employeeAge,
            BigDecimal netRelevantEarningsOrRemuneration,
            BigDecimal proposedContributionThisPeriod,
            BigDecimal cumulativeReliefEligibleContributionsYearToDate) {
    }

    record StandardPensionResult(
            BigDecimal ageRelatedPercentageLimit,
            BigDecimal earningsForReliefCapped,
            BigDecimal maximumAnnualTaxRelievableContribution,
            BigDecimal remainingReliefHeadroomForYear,
            BigDecimal taxRelievablePortionThisPeriod,
            BigDecimal nonRelievableExcess,
            List<CalculationStep> steps) {
    }

    static BigDecimal ageRelatedPercentageLimit(int age, ITaxYearRules.StandardPensionRates rates) {
        if (age < 30) {
            return rates.ageUnder30();
        }
        if (age <= 39) {
            return rates.age30To39();
        }
        if (age <= 49) {
            return rates.age40To49();
        }
        if (age <= 54) {
            return rates.age50To54();
        }
        if (age <= 59) {
            return rates.age55To59();
        }
        return rates.age60Plus();
    }

    static StandardPensionResult calculate(StandardPensionContext ctx, ITaxYearRules.StandardPensionRates rates) {
        BigDecimal percentageLimit = ageRelatedPercentageLimit(ctx.employeeAge(), rates);

        Map<String, Object> initial = new HashMap<>();
        initial.put("netRelevantEarningsOrRemuneration", nz(ctx.netRelevantEarningsOrRemuneration()));
        initial.put("earningsCap", rates.earningsCap());
        initial.put("ageRelatedPercentageLimit", percentageLimit);
        initial.put("proposedContributionThisPeriod", nz(ctx.proposedContributionThisPeriod()));
        initial.put("cumulativeReliefEligibleContributionsYearToDate", nz(ctx.cumulativeReliefEligibleContributionsYearToDate()));

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        BigDecimal earningsForReliefCapped = runner.step("Earnings for relief purposes, capped",
                "MIN(netRelevantEarningsOrRemuneration, earningsCap)",
                "earningsForReliefCapped");
        BigDecimal maxAnnual = runner.step("Maximum annual tax-relievable contribution",
                "earningsForReliefCapped * ageRelatedPercentageLimit",
                "maximumAnnualTaxRelievableContribution");
        BigDecimal remainingHeadroom = runner.step("Remaining relief headroom for the year",
                "MAX(0, maximumAnnualTaxRelievableContribution - cumulativeReliefEligibleContributionsYearToDate)",
                "remainingReliefHeadroomForYear");
        BigDecimal relievablePortion = runner.step("Tax-relievable portion of this period's contribution",
                "MIN(proposedContributionThisPeriod, remainingReliefHeadroomForYear)",
                "taxRelievablePortionThisPeriod");
        BigDecimal nonRelievableExcess = runner.finalStep("Non-relievable excess",
                "MAX(0, proposedContributionThisPeriod - taxRelievablePortionThisPeriod)");

        return new StandardPensionResult(percentageLimit, earningsForReliefCapped, maxAnnual, remainingHeadroom,
                relievablePortion, nonRelievableExcess, runner.result().steps());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

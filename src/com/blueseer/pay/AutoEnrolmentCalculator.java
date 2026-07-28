package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C-14 Auto-Enrolment (My Future Fund / NAERSA), per gov.ie's own
 * contribution-rate table (see calc-engine spec §C-14). {@code
 * isEligibleForAutoEnrolment} is resolved in Java before the EvalEx chain
 * runs (23-60 age band, &euro;20,000 aggregate-earnings gate, no existing
 * supplementary pension coverage) - genuinely NAERSA's decision in
 * production, this stub's best-effort local approximation of it.
 */
final class AutoEnrolmentCalculator {

    private static final String ENGINE = "C-14 Auto-Enrolment";
    private static final BigDecimal AGGREGATE_EARNINGS_GATE = new BigDecimal("20000");

    private AutoEnrolmentCalculator() {
    }

    record AutoEnrolmentContext(
            int employeeAge,
            BigDecimal aggregateAnnualEarningsAcrossEmployments,
            boolean hasExistingSupplementaryPensionCoverage,
            boolean hasOptedInVoluntarily,
            boolean hasOptedOutOrSuspended,
            BigDecimal grossPayThisPeriod,
            BigDecimal cumulativeAeAssessableGrossPayYearToDate) {
    }

    record AutoEnrolmentResult(
            boolean isEligibleForAutoEnrolment,
            boolean participationActive,
            BigDecimal assessableGrossPayThisPeriod,
            BigDecimal employeeAeContributionThisPeriod,
            BigDecimal employerAeContributionThisPeriod,
            BigDecimal stateTopUpThisPeriod,
            List<CalculationStep> steps) {
    }

    static boolean isEligible(int age, BigDecimal aggregateAnnualEarnings, boolean hasExistingSupplementaryPensionCoverage) {
        return age >= 23 && age <= 60
                && aggregateAnnualEarnings != null && aggregateAnnualEarnings.compareTo(AGGREGATE_EARNINGS_GATE) >= 0
                && !hasExistingSupplementaryPensionCoverage;
    }

    static AutoEnrolmentResult calculate(AutoEnrolmentContext ctx, ITaxYearRules.AutoEnrolmentRates rates) {
        boolean eligible = isEligible(ctx.employeeAge(), ctx.aggregateAnnualEarningsAcrossEmployments(), ctx.hasExistingSupplementaryPensionCoverage());

        Map<String, Object> initial = new HashMap<>();
        initial.put("isEligibleForAutoEnrolment", eligible);
        initial.put("hasOptedInVoluntarily", ctx.hasOptedInVoluntarily());
        initial.put("hasOptedOutOrSuspended", ctx.hasOptedOutOrSuspended());
        initial.put("grossPayThisPeriod", nz(ctx.grossPayThisPeriod()));
        initial.put("annualEarningsCapForAeContributions", rates.annualEarningsCap());
        initial.put("cumulativeAeAssessableGrossPayYearToDate", nz(ctx.cumulativeAeAssessableGrossPayYearToDate()));
        initial.put("employeeContributionRate", rates.employeeContributionRate());
        initial.put("employerContributionRate", rates.employerContributionRate());
        initial.put("stateTopUpDivisor", rates.stateTopUpDivisor());

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        BigDecimal participationFlag = runner.step("Active participation this period",
                "IF(isEligibleForAutoEnrolment == false && hasOptedInVoluntarily == false, 0, IF(hasOptedOutOrSuspended == true, 0, 1))",
                "participationActive");
        BigDecimal assessable = runner.step("Assessable gross pay this period, capped at the annual ceiling",
                "MAX(0, MIN(grossPayThisPeriod, annualEarningsCapForAeContributions - cumulativeAeAssessableGrossPayYearToDate))",
                "assessableGrossPayThisPeriod");
        BigDecimal employeeContribution = runner.step("Employee AE contribution this period",
                "IF(participationActive == 1, assessableGrossPayThisPeriod * employeeContributionRate, 0)",
                "employeeAeContributionThisPeriod");
        BigDecimal employerContribution = runner.step("Employer AE contribution this period",
                "IF(participationActive == 1, assessableGrossPayThisPeriod * employerContributionRate, 0)",
                "employerAeContributionThisPeriod");
        BigDecimal stateTopUp = runner.finalStep("State top-up this period",
                "employeeAeContributionThisPeriod / stateTopUpDivisor");

        return new AutoEnrolmentResult(eligible, participationFlag.compareTo(BigDecimal.ZERO) != 0, assessable,
                employeeContribution, employerContribution, stateTopUp, runner.result().steps());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

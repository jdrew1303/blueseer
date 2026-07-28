package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C-09 BIK Preferential Loans, per TDM Part 05-01-01d §2.3/§3/§6.2 (see
 * calc-engine spec §C-09). {@code loanCategory} routing ("HOME_LOAN" vs.
 * "OTHER") is pre-branched in Java rather than an EvalEx string-equality
 * formula, matching this codebase's established resolution of the
 * calc-engine spec's own open question on EvalEx string support (see
 * {@code CycleToWorkCalculator}). Steps 1-3 run once per sub-period, per the
 * TDM's own worked examples; Java sums the per-sub-period results before
 * Step 4's joint-loan apportionment runs once on the annual total.
 */
final class BikLoanCalculator {

    private static final String ENGINE = "C-09 BIK Preferential Loans";

    private BikLoanCalculator() {
    }

    record SubPeriodInput(BigDecimal openingPrincipalBalance, int daysInSubPeriod, BigDecimal actualInterestPaidForSubPeriod) {
    }

    record BikLoanContext(
            String loanCategory,
            List<SubPeriodInput> subPeriods,
            int daysInYear,
            boolean isJointLoanWithNonEmployee,
            boolean isMarriedOrCivilPartnerJointLoan,
            BigDecimal employeeSharePercentage) {
    }

    record BikLoanResult(BigDecimal summedAnnualTaxableBenefit, BigDecimal finalAnnualNotionalPay, List<CalculationStep> steps) {
    }

    static BikLoanResult calculate(BikLoanContext ctx, ITaxYearRules.BikLoanRates rates) {
        BigDecimal specifiedRate = "HOME_LOAN".equals(ctx.loanCategory()) ? rates.specifiedRateHomeLoan() : rates.specifiedRateOther();

        List<CalculationStep> allSteps = new ArrayList<>();
        BigDecimal summed = BigDecimal.ZERO;
        int idx = 1;
        for (SubPeriodInput sp : ctx.subPeriods()) {
            Map<String, Object> initial = new HashMap<>();
            initial.put("openingPrincipalBalance", sp.openingPrincipalBalance());
            initial.put("specifiedRateApplicable", specifiedRate);
            initial.put("daysInSubPeriod", BigDecimal.valueOf(sp.daysInSubPeriod()));
            initial.put("daysInYear", BigDecimal.valueOf(ctx.daysInYear()));
            initial.put("actualInterestPaidForSubPeriod", sp.actualInterestPaidForSubPeriod());

            EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);
            runner.step("Sub-period " + idx + " - interest at specified rate",
                    "openingPrincipalBalance * specifiedRateApplicable * (daysInSubPeriod / daysInYear)",
                    "interestAtSpecifiedRate");
            BigDecimal taxableBenefit = runner.finalStep("Sub-period " + idx + " - taxable benefit",
                    "MAX(0, interestAtSpecifiedRate - actualInterestPaidForSubPeriod)");
            allSteps.addAll(runner.result().steps());
            summed = summed.add(taxableBenefit);
            idx++;
        }

        Map<String, Object> finalCtx = new HashMap<>();
        finalCtx.put("summedAnnualTaxableBenefit", summed);
        finalCtx.put("isJointLoanWithNonEmployee", ctx.isJointLoanWithNonEmployee());
        finalCtx.put("isMarriedOrCivilPartnerJointLoan", ctx.isMarriedOrCivilPartnerJointLoan());
        finalCtx.put("employeeSharePercentage", ctx.employeeSharePercentage());
        EvalExStepRunner finalRunner = new EvalExStepRunner(ENGINE, finalCtx);
        BigDecimal finalAmount = finalRunner.finalStep("Joint-loan apportionment (annual notional pay)",
                "IF(isJointLoanWithNonEmployee == true, IF(isMarriedOrCivilPartnerJointLoan == true, summedAnnualTaxableBenefit, summedAnnualTaxableBenefit * employeeSharePercentage), summedAnnualTaxableBenefit)");
        allSteps.addAll(finalRunner.result().steps());

        return new BikLoanResult(summed, finalAmount, allSteps);
    }
}

package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C-23 CWPS (Construction Workers' Pension Scheme), per cwps.ie's own
 * "Annual Contribution Rates" card (see calc-engine spec §C-23). Flat weekly
 * euro amounts - no age band, no earnings cap, unlike C-15. Only the pension
 * and death-in-service elements are PAYE-relievable (deducted from gross
 * before C-01); the sick pay element and both voluntary elements are
 * deducted from net pay, per the source's own asterisk on that split - this
 * class does not decide gross/net routing itself, it only returns
 * {@code memberPayeRelievableAmount} for the caller to route.
 */
final class CwpsCalculator {

    private static final String ENGINE = "C-23 CWPS";

    private CwpsCalculator() {
    }

    record CwpsContext(boolean includesVoluntaryHealthTrust, boolean includesVoluntaryBenevolentFund) {
    }

    record CwpsResult(
            BigDecimal memberMandatoryTotal,
            BigDecimal memberTotalDeduction,
            BigDecimal employerMandatoryTotal,
            BigDecimal memberPayeRelievableAmount,
            List<CalculationStep> steps) {
    }

    static CwpsResult calculate(CwpsContext ctx, ITaxYearRules.CwpsRates rates) {
        Map<String, Object> initial = new HashMap<>();
        initial.put("memberPensionContributionWeekly", rates.memberPensionWeekly());
        initial.put("memberDeathInServiceContributionWeekly", rates.memberDeathInServiceWeekly());
        initial.put("memberSickPayContributionWeekly", rates.memberSickPayWeekly());
        initial.put("memberHealthTrustContributionWeekly", rates.memberHealthTrustWeekly());
        initial.put("memberBenevolentFundContributionWeekly", rates.memberBenevolentFundWeekly());
        initial.put("employerPensionContributionWeekly", rates.employerPensionWeekly());
        initial.put("employerDeathInServiceContributionWeekly", rates.employerDeathInServiceWeekly());
        initial.put("employerSickPayContributionWeekly", rates.employerSickPayWeekly());
        initial.put("includesVoluntaryHealthTrust", ctx.includesVoluntaryHealthTrust());
        initial.put("includesVoluntaryBenevolentFund", ctx.includesVoluntaryBenevolentFund());

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        BigDecimal memberMandatoryTotal = runner.step("Member's total weekly CWPS deduction (mandatory elements)",
                "memberPensionContributionWeekly + memberDeathInServiceContributionWeekly + memberSickPayContributionWeekly",
                "memberMandatoryTotal");
        BigDecimal memberTotalDeduction = runner.step("Member's total weekly CWPS deduction including voluntary elements",
                "memberMandatoryTotal + IF(includesVoluntaryHealthTrust == true, memberHealthTrustContributionWeekly, 0) "
                        + "+ IF(includesVoluntaryBenevolentFund == true, memberBenevolentFundContributionWeekly, 0)",
                "memberTotalDeduction");
        BigDecimal employerMandatoryTotal = runner.step("Employer's total weekly CWPS cost (mandatory elements)",
                "employerPensionContributionWeekly + employerDeathInServiceContributionWeekly + employerSickPayContributionWeekly",
                "employerMandatoryTotal");
        BigDecimal payeRelievable = runner.finalStep("PAYE-relievable portion of the member deduction",
                "memberPensionContributionWeekly + memberDeathInServiceContributionWeekly");

        return new CwpsResult(memberMandatoryTotal, memberTotalDeduction, employerMandatoryTotal, payeRelievable, runner.result().steps());
    }
}

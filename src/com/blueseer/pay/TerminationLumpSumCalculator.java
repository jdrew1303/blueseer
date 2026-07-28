package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * C-10 Termination Lump Sum (SCSB / Basic Exemption), per Revenue TDM Part
 * 05-05-19 and ss.123/201/Sch.3 TCA 1997 (see calc-engine spec §C-10). This
 * transcribes Steps 1-9 of the manual's own worked-example formulas; the
 * upstream exclusions the manual assumes have already happened (statutory
 * redundancy, s.112 contractual payments, pension-scheme lump sums proper)
 * are the caller's responsibility, not this engine's.
 */
public final class TerminationLumpSumCalculator {

    private static final String ENGINE = "C-10 Termination Lump Sum";

    private TerminationLumpSumCalculator() {
    }

    public record TerminationLumpSumContext(
            BigDecimal exGratiaLumpSumAmount,
            int completeYearsOfService,
            BigDecimal averageAnnualRemunerationLast36Months,
            boolean eligibleForIncreasedExemption,
            BigDecimal relevantCapitalSum,
            BigDecimal priorLifetimeReliefClaimed) {
    }

    public record TerminationLumpSumResult(
            BigDecimal standardBasicExemption,
            BigDecimal increasedExemption,
            BigDecimal totalBasicExemption,
            BigDecimal scsb,
            BigDecimal additionalExemptionFromScsb,
            BigDecimal totalExemptionUncapped,
            BigDecimal remainingLifetimeCapHeadroom,
            BigDecimal finalExemptionApplied,
            BigDecimal taxableAmount,
            java.util.List<CalculationStep> steps) {
    }

    public static TerminationLumpSumResult calculate(TerminationLumpSumContext ctx, ITaxYearRules.TerminationLumpSumRates rates) {
        Map<String, Object> initial = new HashMap<>();
        initial.put("exGratiaLumpSumAmount", ctx.exGratiaLumpSumAmount());
        initial.put("completeYearsOfService", BigDecimal.valueOf(ctx.completeYearsOfService()));
        initial.put("averageAnnualRemunerationLast36Months", ctx.averageAnnualRemunerationLast36Months());
        initial.put("relevantCapitalSum", ctx.relevantCapitalSum());
        initial.put("priorLifetimeReliefClaimed", ctx.priorLifetimeReliefClaimed());
        initial.put("basicExemptionFlatAmount", rates.basicExemptionFlatAmount());
        initial.put("basicExemptionPerYearAmount", rates.basicExemptionPerYearAmount());
        initial.put("eligibleForIncreasedExemption", ctx.eligibleForIncreasedExemption());
        initial.put("increasedExemptionCap", rates.increasedExemptionCap());
        initial.put("scsbDivisor", rates.scsbDivisor());
        initial.put("lifetimeReliefCapAmount", rates.lifetimeReliefCapAmount());

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        BigDecimal standardBasicExemption = runner.step("Standard basic exemption",
                "basicExemptionFlatAmount + (basicExemptionPerYearAmount * completeYearsOfService)",
                "standardBasicExemption");
        BigDecimal increasedExemption = runner.step("Increased exemption",
                "IF(eligibleForIncreasedExemption == true, MAX(0, increasedExemptionCap - relevantCapitalSum), 0)",
                "increasedExemption");
        BigDecimal totalBasicExemption = runner.step("Total basic exemption",
                "standardBasicExemption + increasedExemption",
                "totalBasicExemption");
        BigDecimal scsb = runner.step("SCSB",
                "MAX(0, ((averageAnnualRemunerationLast36Months / scsbDivisor) * completeYearsOfService) - relevantCapitalSum)",
                "scsb");
        BigDecimal additionalExemptionFromScsb = runner.step("Additional exemption arising from SCSB",
                "MAX(0, scsb - totalBasicExemption)",
                "additionalExemptionFromScsb");
        BigDecimal totalExemptionUncapped = runner.step("Total exemption before the lifetime cap",
                "totalBasicExemption + additionalExemptionFromScsb",
                "totalExemptionUncapped");
        BigDecimal remainingLifetimeCapHeadroom = runner.step("Remaining lifetime cap headroom",
                "MAX(0, lifetimeReliefCapAmount - priorLifetimeReliefClaimed)",
                "remainingLifetimeCapHeadroom");
        BigDecimal finalExemptionApplied = runner.step("Final exemption applied",
                "MIN(totalExemptionUncapped, MIN(remainingLifetimeCapHeadroom, exGratiaLumpSumAmount))",
                "finalExemptionApplied");
        BigDecimal taxableAmount = runner.finalStep("Taxable lump sum amount (PRSI-exempt; charged to PAYE/USC only)",
                "MAX(0, exGratiaLumpSumAmount - finalExemptionApplied)");

        return new TerminationLumpSumResult(
                standardBasicExemption, increasedExemption, totalBasicExemption, scsb, additionalExemptionFromScsb,
                totalExemptionUncapped, remainingLifetimeCapHeadroom, finalExemptionApplied, taxableAmount,
                runner.result().steps());
    }
}

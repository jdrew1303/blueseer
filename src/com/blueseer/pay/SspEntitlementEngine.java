package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C-12 Statutory Sick Leave, per S.I. No. 607/2022 and the WRC/DSP sources
 * cross-cited in the calc-engine spec §C-12 (see calc-engine spec for the
 * three pay-calculation categories and the 13-week averaging window).
 * {@code payCalculationCategory} routing is pre-branched in Java, selecting
 * which of the three Step-1 formulas actually runs - keeping the exact
 * formula string in the audit trail rather than an EvalEx string-equality
 * expression, per this codebase's established resolution of that open
 * question (see {@code BikLoanCalculator}).
 */
final class SspEntitlementEngine {

    private static final String ENGINE = "C-12 Statutory Sick Leave";

    private SspEntitlementEngine() {
    }

    record SspContext(
            String payCalculationCategory,
            BigDecimal normalDailyHoursLastWorkedPay,
            BigDecimal fixedHourlyRate,
            BigDecimal scheduledHoursOnSickDay,
            BigDecimal averageHourlyRateOver13Weeks,
            int statutorySickLeaveDaysUsedYearToDate,
            boolean hasCompletedQualifyingService,
            boolean hasMedicalCertificate) {
    }

    record SspResult(
            BigDecimal dailyRateBase,
            int sslDaysRemaining,
            boolean eligibleForSslPayToday,
            BigDecimal sslPayThisDay,
            List<CalculationStep> steps) {
    }

    static SspResult calculate(SspContext ctx, ITaxYearRules.SslRates rates) {
        Map<String, Object> initial = new HashMap<>();
        initial.put("normalDailyHoursLastWorkedPay", nz(ctx.normalDailyHoursLastWorkedPay()));
        initial.put("fixedHourlyRate", nz(ctx.fixedHourlyRate()));
        initial.put("scheduledHoursOnSickDay", nz(ctx.scheduledHoursOnSickDay()));
        initial.put("averageHourlyRateOver13Weeks", nz(ctx.averageHourlyRateOver13Weeks()));
        initial.put("statutorySickLeaveDaysEntitlementPerYear", BigDecimal.valueOf(rates.daysEntitlementPerYear()));
        initial.put("statutorySickLeaveDaysUsedYearToDate", BigDecimal.valueOf(ctx.statutorySickLeaveDaysUsedYearToDate()));
        initial.put("hasCompletedQualifyingService", ctx.hasCompletedQualifyingService());
        initial.put("hasMedicalCertificate", ctx.hasMedicalCertificate());
        initial.put("sslRatePercentage", rates.ratePercentage());
        initial.put("sslDailyCap", rates.dailyCap());

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        String category = ctx.payCalculationCategory();
        String step1Formula = switch (category) {
            case "FIXED_PERIOD" -> "normalDailyHoursLastWorkedPay";
            case "FIXED_RATE_VARIABLE_HOURS" -> "fixedHourlyRate * scheduledHoursOnSickDay";
            default -> "averageHourlyRateOver13Weeks * scheduledHoursOnSickDay";
        };
        BigDecimal dailyRateBase = runner.step("Daily rate base (category: " + category + ")", step1Formula, "dailyRateBase");

        BigDecimal sslDaysRemaining = runner.step("SSL days remaining before today's claim",
                "MAX(0, statutorySickLeaveDaysEntitlementPerYear - statutorySickLeaveDaysUsedYearToDate)",
                "sslDaysRemaining");
        // Encoded as 1/0 rather than a bare boolean literal, matching this
        // codebase's established pattern (see ClassSPrsiCalculator's routing
        // step) for an IF() whose result is stored and re-tested by a later
        // step rather than consumed inline within the same formula.
        BigDecimal eligibleFlag = runner.step("Eligible for SSL pay today",
                "IF(hasCompletedQualifyingService == false, 0, IF(hasMedicalCertificate == false, 0, IF(sslDaysRemaining <= 0, 0, 1)))",
                "eligibleForSslPayToday");
        runner.step("SSL pay for this day before the daily cap",
                "dailyRateBase * sslRatePercentage",
                "sslPayBeforeCap");
        BigDecimal finalPay = runner.finalStep("SSL pay for this day, final",
                "IF(eligibleForSslPayToday == 1, MIN(sslPayBeforeCap, sslDailyCap), 0)");

        return new SspResult(dailyRateBase, sslDaysRemaining.intValue(), eligibleFlag.compareTo(BigDecimal.ZERO) != 0,
                finalPay, runner.result().steps());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

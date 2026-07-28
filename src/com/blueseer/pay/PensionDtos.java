package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.util.List;

/** DTOs backing S-59 Pension Deduction Setup (C-15/C-23/C-24) and S-60 Pension Tracing Number Entry. */
public final class PensionDtos {

    private PensionDtos() {
    }

    /** {@code schemeType}: {@code "STANDARD"} \| {@code "CWPS"} \| {@code "NECI"}. Percentage/fixed-amount/employer-match fields apply to Standard only; health-trust/benevolent-fund flags apply to CWPS only. */
    public record PensionDeductionDTO(
            EmployeeId employeeId,
            String schemeType,
            boolean percentageMode,
            BigDecimal contributionValue,
            boolean employerMatch,
            BigDecimal employerMatchValue,
            boolean includeHealthTrust,
            boolean includeBenevolentFund) {
    }

    public record PensionReliefPreviewDTO(
            BigDecimal ageRelatedPercentageLimit,
            BigDecimal remainingReliefHeadroom,
            BigDecimal taxRelievablePortion,
            BigDecimal nonRelievableExcess,
            List<CalculationStep> steps) {
    }

    public record CwpsPreviewDTO(
            boolean isCwpsRegistered,
            BigDecimal memberPensionWeekly, BigDecimal memberDeathInServiceWeekly, BigDecimal memberSickPayWeekly,
            BigDecimal employerPensionWeekly, BigDecimal employerDeathInServiceWeekly, BigDecimal employerSickPayWeekly,
            BigDecimal memberHealthTrustWeekly, BigDecimal memberBenevolentFundWeekly,
            BigDecimal memberTotalDeduction, BigDecimal employerMandatoryTotal) {
    }

    public record NeciPreviewDTO(BigDecimal memberContribution, BigDecimal employerContribution) {
    }
}

package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;

/** DTOs backing S-61 Auto-Enrolment (MyFuture Fund) - C-14. */
public final class AutoEnrolmentDtos {

    private AutoEnrolmentDtos() {
    }

    /** {@code status}: {@code "Eligible"} \| {@code "Not Eligible"} \| {@code "Opted In"} \| {@code "Exempt (existing pension coverage)"}. */
    public record AeEligibilityStatusDTO(String status, String contributionTierLabel, boolean hasExistingPensionCoverage, boolean optedOutOrSuspended) {
    }

    public record AeContributionPeriodDTO(int periodNumber, BigDecimal employeeAmount, BigDecimal employerAmount, BigDecimal stateAmount, BigDecimal totalAmount) {
    }

    public record AecsSubmissionResult(boolean success, String message) {
    }

    public record AepnCorrectionDTO(EmployeeId employeeId, String note) {
    }
}

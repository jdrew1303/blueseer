package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTOs backing S-58 Parenting Benefits (Maternity/Paternity/Parent's) - C-18. */
public final class ParentingBenefitDtos {

    private ParentingBenefitDtos() {
    }

    public record ParentingBenefitDTO(
            EmployeeId employeeId,
            String benefitType,
            LocalDate leaveStartDate,
            LocalDate leaveEndDate,
            boolean reportedToRevenue,
            BigDecimal employerTopUpAmountPerPeriod) {
    }
}

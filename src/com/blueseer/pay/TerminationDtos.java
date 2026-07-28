package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.util.List;

/** DTOs backing S-50 Termination Lump Sum. */
public final class TerminationDtos {

    private TerminationDtos() {
    }

    public record TerminationLumpSumDTO(
            EmployeeId employeeId,
            BigDecimal exGratiaLumpSumAmount,
            BigDecimal relevantCapitalSum) {
    }

    public record LumpSumExemptionResultDTO(
            int completeYearsOfService,
            BigDecimal averageAnnualRemunerationLast36Months,
            boolean eligibleForIncreasedExemption,
            BigDecimal standardBasicExemption,
            BigDecimal increasedExemption,
            BigDecimal totalBasicExemption,
            BigDecimal scsb,
            BigDecimal finalExemptionApplied,
            BigDecimal taxableAmount,
            List<CalculationStep> steps) {
    }
}

package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTOs backing S-27 Cycle to Work Scheme (C-11). */
public final class CycleToWorkDtos {

    private CycleToWorkDtos() {
    }

    public record CycleToWorkDTO(
            EmployeeId employeeId,
            BigDecimal benefitValue,
            boolean isElectricBike,
            LocalDate sacrificeStartDate,
            BigDecimal salaryForgonePerPeriod) {
    }

    public record CycleEligibilityDTO(boolean eligibleThisCycle, BigDecimal exemptionLimit, LocalDate lastArrangementDateOrNull) {
    }
}

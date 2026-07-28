package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs backing S-56 Statutory Sick Pay Setup/Operation (C-12). */
public final class SspDtos {

    private SspDtos() {
    }

    public record SspClaimDTO(
            EmployeeId employeeId,
            LocalDate illnessStartDate,
            int daysClaimedThisInstance,
            boolean hasMedicalCertificate) {
    }

    public record SspPreviewDTO(
            boolean qualifyingServiceMet,
            int daysRemainingThisYear,
            BigDecimal dailyRateBase,
            BigDecimal sslPayThisInstance,
            List<CalculationStep> steps) {
    }
}

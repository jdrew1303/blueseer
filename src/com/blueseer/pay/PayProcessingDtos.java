package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.PayslipId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs backing S-17...S-24 (Payroll Calendar & Pay Processing family). */
public final class PayProcessingDtos {

    private PayProcessingDtos() {
    }

    /** S-17 Payroll Calendar row. */
    public record CalendarPeriodDTO(int periodNumber, LocalDate from, LocalDate to, boolean isCurrentPeriod) {
    }

    /** S-18/S-19/S-20 single-employee pay entry draft for one period. */
    public record PayEntryDTO(
            EmployeeId employeeId,
            int periodNumber,
            BigDecimal hourlyRate,
            BigDecimal standardHours,
            BigDecimal timeAndAThirdHours,
            BigDecimal timeAndAHalfHours,
            BigDecimal doubleTimeHours,
            BigDecimal basicPay,
            BigDecimal holidayPayAmount,
            int additionalWeeksSpread,
            boolean leaving,
            LocalDate leaveDateOrNull,
            String noteOrNull) {
    }

    /** S-19 Quick Edit grid row - core columns only, per the exemplar's stated grid-mode limitation. */
    public record PayEntryRowDTO(
            EmployeeId employeeId,
            String displayName,
            BigDecimal hours,
            BigDecimal basicPay,
            BigDecimal grossPayPreview) {
    }

    public record BatchSaveResult(int savedCount, List<String> errors) {
    }

    /** C-20 Net-to-Gross iterative solve result, with the full iteration trail per its audit-trail requirement. */
    public record NetToGrossResultDTO(
            BigDecimal solvedGross,
            BigDecimal paye,
            BigDecimal prsi,
            BigDecimal usc,
            BigDecimal net,
            List<String> iterationTrail) {
    }

    /** S-21 Payroll Preview row. */
    public record PreviewRowDTO(
            EmployeeId employeeId,
            String displayName,
            BigDecimal gross,
            BigDecimal paye,
            BigDecimal prsi,
            BigDecimal usc,
            BigDecimal net) {
    }

    /** S-22 Finalise Pay Period summary panel. */
    public record FinalisationSummaryDTO(
            LocalDate lastRpnImportDateOrNull,
            String lastPeriodUpdatedLabel,
            int periodBeingProcessed,
            LocalDate suggestedPayDate,
            boolean payslipReferenceNoteVisible) {
    }

    public record FinalisationResult(boolean success, int payslipsCreated, String message) {
    }

    /** S-23 Payslip Workings row - one per persisted {@link CalculationStep}. */
    public record CalculationStepDTO(String engineName, String stepLabel, String formula, BigDecimal resultValue) {
    }

    /** S-24 Computational Anomaly row. */
    public record AnomalyDTO(
            EmployeeId employeeId,
            String displayName,
            BigDecimal thisPeriod,
            BigDecimal priorPeriod,
            BigDecimal percentChange,
            String flaggedReason) {
    }

    /** A finalised (S-22) payslip - persisted so S-23/S-24 have something to read back rather than recomputing. */
    public record PayslipRecordDTO(
            PayslipId id,
            EmployeeId employeeId,
            int periodNumber,
            LocalDate payDate,
            BigDecimal gross,
            BigDecimal paye,
            BigDecimal prsiEmployee,
            BigDecimal usc,
            BigDecimal net,
            List<CalculationStep> steps) {
    }
}

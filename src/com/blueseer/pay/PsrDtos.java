package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.PsrBatchId;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTOs backing S-31/S-32/S-33 (PSR prepare/submit, control panel, correction wizard). */
public final class PsrDtos {

    private PsrDtos() {
    }

    public record PsrSummaryDTO(BigDecimal payeTotal, BigDecimal uscTotal, BigDecimal prsiTotal, BigDecimal lptTotal) {
    }

    public record PsrLineItemDTO(EmployeeId employeeId, BigDecimal paye, BigDecimal prsi, BigDecimal usc, BigDecimal lpt) {
    }

    public record PsrSubmissionResult(boolean success, String revenueReferenceNumber, BigDecimal totalSubmitted) {
    }

    public record PsrControlRowDTO(PsrBatchId batchId, LocalDate payDate, int payslipCount, int countReturned, String status) {
    }

    /** S-33's six correction tiles. Only the last three carry a Controller payload - the first three route to other screens. */
    public enum CorrectionType { DO_IT_ALL_AGAIN, NEW_EMPLOYEE, PAYMENT_WAS_DIFFERENT, WRONG_PPS_NUMBER, WRONG_PRSI_CLASS, EMPLOYEE_HAS_LEFT }

    /** One variant per applicable correction type, so applyCorrection stays type-safe. */
    public sealed interface CorrectionDetailsDTO {
        record PaymentWasDifferent(EmployeeId employeeId, LocalDate originalPeriod, BigDecimal actualAmountPaid) implements CorrectionDetailsDTO {
        }

        record WrongPpsNumber(EmployeeId employeeId, String correctPpsNumber) implements CorrectionDetailsDTO {
        }

        record WrongPrsiClass(EmployeeId employeeId, String correctPrsiClass) implements CorrectionDetailsDTO {
        }
    }

    public record CorrectionResult(boolean success, String detailMessageOrNull) {
    }

    /** S-34 - highlighted in the view when {@code previousStatus != currentStatus}. */
    public record PsrRecheckResultDTO(PsrBatchId batchId, LocalDate payDate, String previousStatus, String currentStatus) {
    }
}

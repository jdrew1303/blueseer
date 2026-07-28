package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.time.LocalDate;
import java.util.List;

/** DTOs backing S-36 Print/Email Payslips. */
public final class PayslipDistributionDtos {

    private PayslipDistributionDtos() {
    }

    public record PeriodDTO(int periodNumber, LocalDate payDate) {
    }

    /** The exemplar's own four documented stationery options - one .jrxml each under {@code sf/jasper/}. */
    public enum PayslipType {
        TWO_PER_PAGE_HIRES("2 per page (High Resolution)", "payslip_2perpage_hires"),
        TWO_PER_PAGE_LORES("2 per page (Low Resolution)", "payslip_2perpage_lores"),
        PAYSLIP_AND_CHEQUE("Payslip & Cheque", "payslip_and_cheque"),
        LASER_SECURITY("Laser Security Payslip", "payslip_laser_security");

        private final String label;
        private final String templateName;

        PayslipType(String label, String templateName) {
            this.label = label;
            this.templateName = templateName;
        }

        public String label() {
            return label;
        }

        public String templateName() {
            return templateName;
        }
    }

    public record PrintPayslipsRequestDTO(
            CompanyId companyId,
            int periodNumber,
            List<EmployeeId> employeeIds,
            int copies,
            boolean includeZeroPayment,
            PayslipType type) {
    }

    public record PrintResult(boolean success, String detailMessageOrNull) {
    }

    public record EmailRecipientDTO(EmployeeId employeeId, String employeeName, String emailAddress) {
    }

    public record EmailPayslipsRequestDTO(
            CompanyId companyId,
            int periodNumber,
            List<EmailRecipientDTO> recipients,
            boolean includeZeroPayment,
            PayslipType type,
            boolean passwordProtect,
            String pdfPasswordOrNull) {
    }

    public enum EmailSendStatus {
        PENDING, SENT, FAILED
    }

    public record EmailSendResult(int sentCount, int failedCount) {
    }
}

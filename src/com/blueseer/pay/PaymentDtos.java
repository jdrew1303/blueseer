package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs backing S-38 Bank Payment File (SEPA) and S-39 Paying Employees Reporting. */
public final class PaymentDtos {

    private PaymentDtos() {
    }

    /**
     * There is no persisted company bank-account store yet (S-01's wizard
     * doesn't capture one) - {@link InMemoryPaymentController} returns a single
     * fixed placeholder account, the same documented gap as the placeholder
     * employer profile in {@link PayslipReportDataProvider}.
     */
    public record BankAccountDTO(String label, String iban, String bic) {
        @Override
        public String toString() {
            return label;
        }
    }

    public record BankFileRequestDTO(
            CompanyId companyId,
            BankAccountDTO sourceAccount,
            LocalDate paymentDate,
            int periodNumber,
            List<EmployeeId> employeeIds,
            String fileFormat) {
    }

    /** One DTO serves both S-39 tabs - Cash Requirement Summary is a filtered view of the same totals. */
    public record PayMethodSummaryDTO(
            LocalDate payDate,
            BigDecimal cashTotal,
            int cashCount,
            BigDecimal chequeTotal,
            int chequeCount,
            BigDecimal creditTransferTotal,
            int creditTransferCount) {
    }
}

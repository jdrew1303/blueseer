package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PaymentDtos.BankAccountDTO;
import com.blueseer.pay.PaymentDtos.BankFileRequestDTO;
import com.blueseer.pay.PaymentDtos.PayMethodSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.time.LocalDate;
import java.util.List;

/** Controller backing S-38 Bank Payment File (SEPA) and S-39 Paying Employees Reporting. */
public interface IPaymentController {

    /** Populates S-38's {@code cbSourceAccount}. */
    List<BankAccountDTO> getSourceAccounts(CompanyId id);

    List<EmployeeSummaryDTO> getCreditTransferEmployees(CompanyId id, int periodNumber);

    /** Backs {@code SepaPaymentFileBuilder}, dispatching internally per {@code req.fileFormat()}. */
    byte[] generateBankFile(BankFileRequestDTO req);

    /** Backs {@code PayMethodAggregator}. */
    PayMethodSummaryDTO getPayMethodSummary(CompanyId id, LocalDate payDate);
}

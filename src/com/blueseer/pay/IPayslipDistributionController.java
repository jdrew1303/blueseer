package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayslipDistributionDtos.EmailPayslipsRequestDTO;
import com.blueseer.pay.PayslipDistributionDtos.EmailSendResult;
import com.blueseer.pay.PayslipDistributionDtos.EmailSendStatus;
import com.blueseer.pay.PayslipDistributionDtos.PeriodDTO;
import com.blueseer.pay.PayslipDistributionDtos.PrintPayslipsRequestDTO;
import com.blueseer.pay.PayslipDistributionDtos.PrintResult;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.util.List;

/** Controller backing S-36 Print/Email Payslips and (via {@code sendPayslipEmails}) S-37. */
public interface IPayslipDistributionController {

    List<PeriodDTO> getProcessedPeriods(CompanyId id);

    List<EmployeeSummaryDTO> getEmployeesForPeriod(CompanyId id, int periodNumber);

    /** Backs {@code PayslipDistributionService}/{@code PayslipRenderSelector}. */
    PrintResult printPayslips(PrintPayslipsRequestDTO req);

    /** Invoked once per recipient, on the EDT, as each email actually sends. */
    interface PayslipEmailProgressListener {
        void onRecipientSent(EmployeeId id, EmailSendStatus status);
    }

    /** Backs {@code SecurePayslipMailer}; streams per-recipient progress back via {@code listener}. */
    EmailSendResult sendPayslipEmails(EmailPayslipsRequestDTO req, PayslipEmailProgressListener listener);
}

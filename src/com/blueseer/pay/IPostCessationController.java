package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PostCessationDtos.PostCessationResult;
import com.blueseer.pay.PostCessationDtos.TaxYearScope;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Controller backing S-51 (current year) and S-52 (out of year) Post-Cessation Payment - shared by both screens. */
public interface IPostCessationController {

    List<EmployeeSummaryDTO> getEligibleFormerEmployees(CompanyId id, TaxYearScope scope);

    /** Backs {@code PostCessationPaymentService}, resolving the current or a historical {@link ITaxYearRules}. */
    PostCessationResult processPayment(EmployeeId id, BigDecimal amount, LocalDate paymentDate, TaxYearScope scope);
}

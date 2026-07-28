package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.RemittanceDtos.DateRange;
import com.blueseer.pay.RemittanceDtos.PaymentDueDateDTO;
import com.blueseer.pay.RemittanceDtos.PaymentRecordRowDTO;
import com.blueseer.pay.RemittanceDtos.RemittanceSummaryDTO;
import com.blueseer.pay.RemittanceDtos.ReturnSearchResultDTO;

import java.util.List;

/** Controller backing S-66 Remittance Overview, S-67 Payment Due Dates, S-68 Revenue Payments Record, S-69 Returns Look-Up. */
public interface IRemittanceController {

    /** Backs {@code RemittanceSummaryService}. */
    RemittanceSummaryDTO getRemittanceSummary(CompanyId id);

    /** Backs {@code PaymentDueDateCalculator}. */
    List<PaymentDueDateDTO> getPaymentDueDates(CompanyId id, int taxYear);

    /** Backs {@code RevenuePaymentLedger}. */
    List<PaymentRecordRowDTO> getPaymentsRecord(CompanyId id, DateRange range);

    /** Backs {@code ReturnsLookupService}. */
    List<ReturnSearchResultDTO> searchReturns(CompanyId id, String query);
}

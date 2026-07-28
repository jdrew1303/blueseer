package com.blueseer.pay;

import com.blueseer.pay.PayslipDistributionDtos.EmailSendResult;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.ReportDtos.AddDedReportRequestDTO;
import com.blueseer.pay.ReportDtos.AuditTrailRequestDTO;
import com.blueseer.pay.ReportDtos.BatchGenerationResult;
import com.blueseer.pay.ReportDtos.EmploymentDetailsSummaryRequestDTO;
import com.blueseer.pay.ReportDtos.OtherReportRequestDTO;
import com.blueseer.pay.ReportDtos.OtherReportType;
import com.blueseer.pay.ReportDtos.PensionReportRequestDTO;
import com.blueseer.pay.ReportDtos.PensionSchemeType;
import com.blueseer.pay.ReportDtos.RegisterFilterDTO;
import com.blueseer.pay.ReportDtos.RegisterRowDTO;
import com.blueseer.pay.ReportDtos.ReportHandle;
import com.blueseer.pay.ReportDtos.TaxDetailsRequestDTO;

import java.io.File;
import java.util.List;

/** Controller backing §3.8 Reports Hub (S-40...S-47). */
public interface IReportController {

    /** Backs {@code AuditTrailAggregator} (S-40). */
    ReportHandle getAuditTrailReport(AuditTrailRequestDTO req);

    /** Backs {@code TaxDetailsAggregator} (S-41). */
    ReportHandle getTaxDetailsReport(TaxDetailsRequestDTO req);

    /** Powers S-42's live {@code tblRegister}. */
    List<RegisterRowDTO> getEmployeeRegister(RegisterFilterDTO filter);

    /** Backs {@code EmployeeRegisterService} - the printable/exportable form of S-42's live filter state. */
    ReportHandle getEmployeeRegisterReportHandle(RegisterFilterDTO filter);

    /** Backs {@code AdditionsDeductionsAggregator} (S-43). */
    ReportHandle getAdditionsDeductionsReport(AddDedReportRequestDTO req);

    /** Backs {@code PensionReportAggregator} (S-44) - {@code type} distinguishes Normal/CWPS/NECI. */
    ReportHandle getPensionReport(PensionSchemeType type, PensionReportRequestDTO req);

    /** Backs {@code OtherReportsAggregator} (S-45). */
    ReportHandle getOtherReport(OtherReportType type, OtherReportRequestDTO req);

    /** Backs {@code YearEndAggregator} (S-46) - whole-tax-year, period-independent. */
    ReportHandle getYearEndSummary(CompanyId id, int taxYear);

    /** Backs {@code EmploymentDetailsSummaryGenerator} (S-47). */
    BatchGenerationResult generateEmploymentDetailsSummary(EmploymentDetailsSummaryRequestDTO req);

    /**
     * Generic over {@link ReportHandle} and reused unchanged by every §3.8
     * screen - S-41 through S-47 never define their own copies of these four.
     */
    void printReport(ReportHandle h, boolean includeDepartmental);

    void copyReportToClipboard(ReportHandle h);

    File exportReportHtml(ReportHandle h);

    EmailSendResult emailReport(ReportHandle h);
}

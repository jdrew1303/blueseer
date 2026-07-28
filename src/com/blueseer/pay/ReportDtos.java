package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.DepartmentId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import net.sf.jasperreports.engine.JasperPrint;

import java.util.List;

/** DTOs backing §3.8 Reports Hub (S-40...S-47). */
public final class ReportDtos {

    private ReportDtos() {
    }

    public enum DirectorsFilter {
        ALL_EMPLOYEES, EXCLUDE_DIRECTORS, DIRECTORS_ONLY
    }

    public record AuditTrailRequestDTO(
            CompanyId companyId, int periodFrom, int periodTo,
            DirectorsFilter directorsFilter, boolean summaryOnly, boolean sortAlphabetically) {
    }

    public enum TaxDetailsBasis {
        MONTHLY, QUARTERLY
    }

    public record TaxDetailsRequestDTO(
            CompanyId companyId, int periodFrom, int periodTo, TaxDetailsBasis basis, boolean sortAlphabetically) {
    }

    public record RegisterFilterDTO(
            CompanyId companyId, DepartmentId departmentIdOrNull, boolean activeOnly, boolean includeLeavers) {
    }

    public record RegisterRowDTO(
            EmployeeId id, String worksNumber, String surname, String firstName,
            String departmentName, String ppsNumber, boolean isFormerEmployee) {
    }

    public record AddDedReportRequestDTO(
            CompanyId companyId, int periodFrom, int periodTo, String typeDescriptionOrNull, boolean sortAlphabetically) {
    }

    public enum PensionSchemeType {
        NORMAL, CWPS, NECI
    }

    public record PensionReportRequestDTO(CompanyId companyId, int periodFrom, int periodTo, boolean sortAlphabetically) {
    }

    public enum OtherReportType {
        HOURS_OVERTIME, HOLIDAY_PAY, NOTIONAL_PAY, ASC
    }

    public record OtherReportRequestDTO(CompanyId companyId, int periodFrom, int periodTo, boolean sortAlphabetically) {
    }

    public record EmploymentDetailsSummaryRequestDTO(
            CompanyId companyId, int taxYear, List<EmployeeId> employeeIds, boolean batchMode) {
    }

    public record BatchGenerationResult(int generatedCount, int skippedCount, List<String> skipReasons) {
    }

    /**
     * The shared handle every §3.8 screen's action bar (print/copy/HTML/email)
     * operates on generically. Carries both the rendered {@link JasperPrint}
     * (for on-screen preview and PDF print/export) and the raw tabular data
     * (so clipboard-copy can build a plain tab-separated table without
     * re-deriving it from Jasper's internal model).
     */
    public record ReportHandle(String title, List<String> headers, List<List<String>> rows, JasperPrint print) {
    }
}

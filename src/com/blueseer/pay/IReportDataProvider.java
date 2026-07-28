package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * §4.1 report data contract - one concrete implementation per report DTO
 * (one per row in the roadmap §3 Reports columns), feeding each `.jrxml`
 * template's {@code JRBeanCollectionDataSource}. Track C builds against
 * hand-built sample beans of {@code T} before a real implementation exists.
 */
public interface IReportDataProvider<T> {

    List<T> fetchReportData(ReportCriteria criteria);

    record ReportCriteria(CompanyId companyId, LocalDate periodFrom, LocalDate periodTo, Map<String, Object> additionalFilters) {
    }
}

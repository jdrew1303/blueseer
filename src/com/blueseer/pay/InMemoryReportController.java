package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.DeductionLineDTO;
import com.blueseer.pay.EmployeeDtos.DepartmentDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.PersonalDetailsDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayslipDistributionDtos.EmailSendResult;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.DepartmentId;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.ReportDtos.AddDedReportRequestDTO;
import com.blueseer.pay.ReportDtos.AuditTrailRequestDTO;
import com.blueseer.pay.ReportDtos.BatchGenerationResult;
import com.blueseer.pay.ReportDtos.DirectorsFilter;
import com.blueseer.pay.ReportDtos.EmploymentDetailsSummaryRequestDTO;
import com.blueseer.pay.ReportDtos.OtherReportRequestDTO;
import com.blueseer.pay.ReportDtos.OtherReportType;
import com.blueseer.pay.ReportDtos.PensionReportRequestDTO;
import com.blueseer.pay.ReportDtos.PensionSchemeType;
import com.blueseer.pay.ReportDtos.RegisterFilterDTO;
import com.blueseer.pay.ReportDtos.RegisterRowDTO;
import com.blueseer.pay.ReportDtos.ReportHandle;
import com.blueseer.pay.ReportDtos.TaxDetailsBasis;
import com.blueseer.pay.ReportDtos.TaxDetailsRequestDTO;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.io.File;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Track A/B/C stub for {@link IReportController}, backed by {@link
 * PayrollStubStore}. Every §3.8 report maps its own row shape into the
 * shared {@link GenericReportRowDTO}/{@code generic_tabular_report.jrxml}
 * six-column template rather than a bespoke layout per report - see that
 * template's own javadoc for why.
 *
 * <p>Three reports are honest reflections of data this Track A stub simply
 * doesn't retain: {@code store.draftPayEntries} is keyed by employee only
 * (not by period), so hours/overtime and holiday-pay figures are lost the
 * moment the next period's entry overwrites them - S-45's "Hours
 * Worked/Overtime" and "Holiday Pay" sub-reports are therefore always empty.
 * "Notional Pay" (BIK) has no data source at all since roadmap §3.10 S-53
 * BIK isn't built yet. "ASC" reads real {@link CalculationStep} data from
 * {@code C-05 ASC} engine runs when present, but no demo employee has ASC
 * wired into {@link PayslipEngineChain} yet, so it is empty too, not
 * fabricated. S-44 Pension Reports similarly has no dedicated pension-scheme
 * field on any employee record (roadmap §3.10 S-59 isn't built) - Normal/
 * CWPS/NECI are distinguished by a case-insensitive keyword match against
 * {@link DeductionLineDTO#description()}, the same "reuse an existing field
 * as an honest proxy" approach used for PRSI Class S/director elsewhere in
 * this module.
 *
 * <p>{@link #exportReportHtml} writes the file but deliberately does not
 * auto-open it (via {@code java.awt.Desktop.open}): on a machine with no
 * default .html handler configured, that call surfaces Windows' native
 * "Open With" chooser, a modal OS dialog outside the JVM's control that a
 * {@code try/catch} cannot suppress - the same "no browser integration in
 * scope" simplification the RPN dialog spec (S-14) already calls for.
 */
final class InMemoryReportController implements IReportController {

    private static final String JASPER_DIR = "sf/jasper/";
    private static final String TEMPLATE = JASPER_DIR + "generic_tabular_report.jrxml";
    private static final String TEMP_DIR = "temp/";

    private final PayrollStubStore store;

    InMemoryReportController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public ReportHandle getAuditTrailReport(AuditTrailRequestDTO req) {
        List<PayslipRecordDTO> matched = new ArrayList<>();
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.periodNumber() < req.periodFrom() || p.periodNumber() > req.periodTo()) {
                continue;
            }
            EmployeeRecordDTO rec = store.employees.get(p.employeeId());
            if (rec == null) {
                continue;
            }
            boolean isDirector = rec.personalDetails().director();
            if (req.directorsFilter() == DirectorsFilter.EXCLUDE_DIRECTORS && isDirector) {
                continue;
            }
            if (req.directorsFilter() == DirectorsFilter.DIRECTORS_ONLY && !isDirector) {
                continue;
            }
            matched.add(p);
        }

        List<GenericReportRowDTO> rows = new ArrayList<>();
        if (req.summaryOnly()) {
            Map<EmployeeId, BigDecimal[]> totals = new LinkedHashMap<>();
            for (PayslipRecordDTO p : matched) {
                BigDecimal[] t = totals.computeIfAbsent(p.employeeId(), k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
                t[0] = t[0].add(p.gross());
                t[1] = t[1].add(p.paye());
                t[2] = t[2].add(p.prsiEmployee());
                t[3] = t[3].add(p.usc());
            }
            for (Map.Entry<EmployeeId, BigDecimal[]> entry : totals.entrySet()) {
                BigDecimal[] t = entry.getValue();
                rows.add(row(employeeName(entry.getKey()), "All periods", money(t[0]), money(t[1]), money(t[2]), money(t[3])));
            }
        } else {
            for (PayslipRecordDTO p : matched) {
                rows.add(row(employeeName(p.employeeId()), String.valueOf(p.periodNumber()), money(p.gross()), money(p.paye()), money(p.prsiEmployee()), money(p.usc())));
            }
        }
        if (req.sortAlphabetically()) {
            sortByCol1(rows);
        }
        return buildHandle("Payroll Summary / Audit Trail (Periods " + req.periodFrom() + "-" + req.periodTo() + ")",
                List.of("Employee", "Period", "Gross", "PAYE", "PRSI", "USC"), rows);
    }

    @Override
    public ReportHandle getTaxDetailsReport(TaxDetailsRequestDTO req) {
        Map<String, BigDecimal[]> byGroup = new LinkedHashMap<>();
        Map<String, java.util.Set<EmployeeId>> employeesByGroup = new LinkedHashMap<>();
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.periodNumber() < req.periodFrom() || p.periodNumber() > req.periodTo()) {
                continue;
            }
            String label = req.basis() == TaxDetailsBasis.QUARTERLY
                    ? "Q" + (((p.payDate().getMonthValue() - 1) / 3) + 1) + " " + p.payDate().getYear()
                    : p.payDate().getMonth() + " " + p.payDate().getYear();
            BigDecimal[] t = byGroup.computeIfAbsent(label, k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            t[0] = t[0].add(p.gross());
            t[1] = t[1].add(p.paye());
            t[2] = t[2].add(p.prsiEmployee());
            t[3] = t[3].add(p.usc());
            employeesByGroup.computeIfAbsent(label, k -> new java.util.LinkedHashSet<>()).add(p.employeeId());
        }
        List<GenericReportRowDTO> rows = new ArrayList<>();
        for (Map.Entry<String, BigDecimal[]> entry : byGroup.entrySet()) {
            BigDecimal[] t = entry.getValue();
            rows.add(row(entry.getKey(), String.valueOf(employeesByGroup.get(entry.getKey()).size()), money(t[0]), money(t[1]), money(t[2]), money(t[3])));
        }
        if (req.sortAlphabetically()) {
            sortByCol1(rows);
        }
        return buildHandle("Tax Details Report (" + req.basis() + ")",
                List.of("Period", "Employees", "Gross", "PAYE", "PRSI", "USC"), rows);
    }

    @Override
    public List<RegisterRowDTO> getEmployeeRegister(RegisterFilterDTO filter) {
        List<RegisterRowDTO> rows = new ArrayList<>();
        for (EmployeeRecordDTO rec : store.employees.values()) {
            PersonalDetailsDTO p = rec.personalDetails();
            boolean isFormer = store.leaveDates.containsKey(rec.id());
            // cbActiveOnly is the primary gate (hides leavers by default); cbIncludeLeavers
            // is an explicit opt-in override that re-admits them even while active-only is checked.
            if (isFormer && filter.activeOnly() && !filter.includeLeavers()) {
                continue;
            }
            if (filter.departmentIdOrNull() != null && !filter.departmentIdOrNull().equals(p.departmentId())) {
                continue;
            }
            rows.add(new RegisterRowDTO(rec.id(), p.worksNumber(), p.surname(), p.firstName(), departmentName(p.departmentId()), p.ppsNumber(), isFormer));
        }
        rows.sort(Comparator.comparing(RegisterRowDTO::surname));
        return rows;
    }

    @Override
    public ReportHandle getEmployeeRegisterReportHandle(RegisterFilterDTO filter) {
        List<GenericReportRowDTO> rows = new ArrayList<>();
        for (RegisterRowDTO r : getEmployeeRegister(filter)) {
            rows.add(row(r.worksNumber(), r.surname(), r.firstName(), r.departmentName(), r.ppsNumber(), r.isFormerEmployee() ? "Former" : "Active"));
        }
        return buildHandle("Register of Employees",
                List.of("Works No.", "Surname", "First Name", "Department", "PPS Number", "Status"), rows);
    }

    @Override
    public ReportHandle getAdditionsDeductionsReport(AddDedReportRequestDTO req) {
        List<GenericReportRowDTO> rows = new ArrayList<>();
        for (EmployeeRecordDTO rec : store.employees.values()) {
            String name = employeeName(rec.id());
            for (AdditionLineDTO a : rec.additions()) {
                if (matchesType(req.typeDescriptionOrNull(), a.description())) {
                    rows.add(row(name, "Addition", a.description(), money(a.amount()), a.taxable() ? "Taxable" : "Non-taxable", "-"));
                }
            }
            for (DeductionLineDTO d : rec.deductions()) {
                if (matchesType(req.typeDescriptionOrNull(), d.description())) {
                    rows.add(row(name, "Deduction", d.description(), d.amount() == null ? "Auto" : money(d.amount()), d.preTax() ? "Pre-tax" : "Post-tax", "-"));
                }
            }
        }
        if (req.sortAlphabetically()) {
            sortByCol1(rows);
        }
        return buildHandle("Additions/Deductions Report (Periods " + req.periodFrom() + "-" + req.periodTo() + ")",
                List.of("Employee", "Type", "Description", "Amount", "Basis", ""), rows);
    }

    @Override
    public ReportHandle getPensionReport(PensionSchemeType type, PensionReportRequestDTO req) {
        List<GenericReportRowDTO> rows = new ArrayList<>();
        for (EmployeeRecordDTO rec : store.employees.values()) {
            String name = employeeName(rec.id());
            for (DeductionLineDTO d : rec.deductions()) {
                if (matchesPensionScheme(type, d.description())) {
                    rows.add(row(name, d.description(), d.amount() == null ? "Auto" : money(d.amount()), d.preTax() ? "Pre-tax" : "Post-tax", "-", "-"));
                }
            }
        }
        if (req.sortAlphabetically()) {
            sortByCol1(rows);
        }
        return buildHandle("Pension Reports - " + type + " (Periods " + req.periodFrom() + "-" + req.periodTo() + ")",
                List.of("Employee", "Description", "Amount", "Basis", "", ""), rows);
    }

    @Override
    public ReportHandle getOtherReport(OtherReportType type, OtherReportRequestDTO req) {
        List<GenericReportRowDTO> rows = new ArrayList<>();
        List<String> headers;
        switch (type) {
            case ASC -> {
                headers = List.of("Employee", "Period", "ASC Amount", "", "", "");
                for (PayslipRecordDTO p : store.payslips.values()) {
                    if (p.periodNumber() < req.periodFrom() || p.periodNumber() > req.periodTo()) {
                        continue;
                    }
                    BigDecimal asc = lastAscStepValue(p);
                    if (asc != null) {
                        rows.add(row(employeeName(p.employeeId()), String.valueOf(p.periodNumber()), money(asc), "", "", ""));
                    }
                }
            }
            case HOURS_OVERTIME -> headers = List.of("Employee", "Standard Hours", "1/3 Hours", "1/2 Hours", "Double Time", "Period");
            case HOLIDAY_PAY -> headers = List.of("Employee", "Holiday Pay Amount", "Period", "", "", "");
            default -> headers = List.of("Employee", "Notional Pay (BIK)", "Period", "", "", "");
        }
        if (req.sortAlphabetically()) {
            sortByCol1(rows);
        }
        return buildHandle("Other Reports - " + type + " (Periods " + req.periodFrom() + "-" + req.periodTo() + ")", headers, rows);
    }

    @Override
    public ReportHandle getYearEndSummary(CompanyId id, int taxYear) {
        Map<EmployeeId, BigDecimal[]> totals = new LinkedHashMap<>();
        Map<EmployeeId, Integer> periodCounts = new LinkedHashMap<>();
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.payDate().getYear() != taxYear) {
                continue;
            }
            BigDecimal[] t = totals.computeIfAbsent(p.employeeId(), k -> new BigDecimal[] {BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            t[0] = t[0].add(p.gross());
            t[1] = t[1].add(p.paye());
            t[2] = t[2].add(p.prsiEmployee());
            t[3] = t[3].add(p.usc());
            periodCounts.merge(p.employeeId(), 1, Integer::sum);
        }
        List<GenericReportRowDTO> rows = new ArrayList<>();
        for (Map.Entry<EmployeeId, BigDecimal[]> entry : totals.entrySet()) {
            BigDecimal[] t = entry.getValue();
            rows.add(row(employeeName(entry.getKey()), String.valueOf(periodCounts.get(entry.getKey())), money(t[0]), money(t[1]), money(t[2]), money(t[3])));
        }
        sortByCol1(rows);
        return buildHandle("Year End Summary " + taxYear,
                List.of("Employee", "Periods Paid", "Gross", "PAYE", "PRSI", "USC"), rows);
    }

    @Override
    public BatchGenerationResult generateEmploymentDetailsSummary(EmploymentDetailsSummaryRequestDTO req) {
        List<EmployeeId> targets = req.batchMode() ? new ArrayList<>(store.employees.keySet()) : req.employeeIds();
        int generated = 0;
        int skipped = 0;
        List<String> reasons = new ArrayList<>();
        File dir = new File(TEMP_DIR);
        dir.mkdirs();

        for (EmployeeId id : targets) {
            BigDecimal gross = BigDecimal.ZERO;
            BigDecimal paye = BigDecimal.ZERO;
            BigDecimal prsi = BigDecimal.ZERO;
            BigDecimal usc = BigDecimal.ZERO;
            int periods = 0;
            for (PayslipRecordDTO p : store.payslips.values()) {
                if (!p.employeeId().equals(id) || p.payDate().getYear() != req.taxYear()) {
                    continue;
                }
                gross = gross.add(p.gross());
                paye = paye.add(p.paye());
                prsi = prsi.add(p.prsiEmployee());
                usc = usc.add(p.usc());
                periods++;
            }
            String name = employeeName(id);
            if (periods == 0) {
                skipped++;
                reasons.add(name + ": no pay recorded in " + req.taxYear());
                continue;
            }
            List<GenericReportRowDTO> rows = List.of(row(name, String.valueOf(periods), money(gross), money(paye), money(prsi), money(usc)));
            ReportHandle handle = buildHandle("Employment Details Summary " + req.taxYear() + " - " + name,
                    List.of("Employee", "Periods Paid", "Gross", "PAYE", "PRSI", "USC"), rows);
            try {
                JasperExportManager.exportReportToPdfFile(handle.print(), TEMP_DIR + "eds-" + req.taxYear() + "-" + slug(name) + ".pdf");
            } catch (Exception e) {
                skipped++;
                reasons.add(name + ": " + e.getMessage());
                continue;
            }
            generated++;
        }
        return new BatchGenerationResult(generated, skipped, reasons);
    }

    @Override
    public void printReport(ReportHandle h, boolean includeDepartmental) {
        try {
            new File(TEMP_DIR).mkdirs();
            JasperExportManager.exportReportToPdfFile(h.print(), TEMP_DIR + "report-" + slug(h.title()) + ".pdf");
        } catch (Exception e) {
            throw new RuntimeException("Print failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void copyReportToClipboard(ReportHandle h) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.join("\t", h.headers())).append('\n');
        for (List<String> row : h.rows()) {
            sb.append(String.join("\t", row)).append('\n');
        }
        Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(sb.toString()), null);
    }

    @Override
    public File exportReportHtml(ReportHandle h) {
        try {
            new File(TEMP_DIR).mkdirs();
            File file = new File(TEMP_DIR + "report-" + slug(h.title()) + ".html");
            JasperExportManager.exportReportToHtmlFile(h.print(), file.getPath());
            return file;
        } catch (Exception e) {
            throw new RuntimeException("HTML export failed: " + e.getMessage(), e);
        }
    }

    @Override
    public EmailSendResult emailReport(ReportHandle h) {
        // No real SMTP integration exists (same honest simplification as S-37's
        // SecurePayslipMailer stub) - simulated as one send to the company's
        // configured report-emailing address.
        return new EmailSendResult(1, 0);
    }

    private ReportHandle buildHandle(String title, List<String> headers, List<GenericReportRowDTO> rows) {
        try {
            JasperReport report = JasperCompileManager.compileReport(TEMPLATE);
            Map<String, Object> params = new HashMap<>();
            params.put("REPORT_TITLE", title);
            for (int i = 0; i < 6; i++) {
                params.put("COL" + (i + 1) + "_HEADER", i < headers.size() ? headers.get(i) : "");
            }
            JasperPrint print = JasperFillManager.fillReport(report, params, new JRBeanCollectionDataSource(rows));

            List<List<String>> rawRows = new ArrayList<>();
            for (GenericReportRowDTO r : rows) {
                rawRows.add(List.of(nz(r.getCol1()), nz(r.getCol2()), nz(r.getCol3()), nz(r.getCol4()), nz(r.getCol5()), nz(r.getCol6())));
            }
            return new ReportHandle(title, headers, rawRows, print);
        } catch (Exception e) {
            throw new RuntimeException("Report generation failed: " + e.getMessage(), e);
        }
    }

    private static GenericReportRowDTO row(String c1, String c2, String c3, String c4, String c5, String c6) {
        GenericReportRowDTO r = new GenericReportRowDTO();
        r.setCol1(c1);
        r.setCol2(c2);
        r.setCol3(c3);
        r.setCol4(c4);
        r.setCol5(c5);
        r.setCol6(c6);
        return r;
    }

    private static void sortByCol1(List<GenericReportRowDTO> rows) {
        rows.sort(Comparator.comparing(r -> nz(r.getCol1())));
    }

    private String employeeName(EmployeeId id) {
        EmployeeRecordDTO rec = store.employees.get(id);
        return rec == null ? "" : rec.personalDetails().surname() + ", " + rec.personalDetails().firstName();
    }

    private String departmentName(DepartmentId id) {
        if (id == null) {
            return "";
        }
        DepartmentDTO dept = store.departments.get(id);
        return dept == null ? "" : dept.name();
    }

    private static boolean matchesType(String typeDescriptionOrNull, String description) {
        return typeDescriptionOrNull == null || typeDescriptionOrNull.isBlank() || "All Types".equals(typeDescriptionOrNull)
                || typeDescriptionOrNull.equalsIgnoreCase(description);
    }

    private static boolean matchesPensionScheme(PensionSchemeType type, String description) {
        if (description == null) {
            return false;
        }
        String d = description.toUpperCase();
        return switch (type) {
            case CWPS -> d.contains("CWPS");
            case NECI -> d.contains("NECI");
            case NORMAL -> d.contains("PENSION") && !d.contains("CWPS") && !d.contains("NECI");
        };
    }

    private static BigDecimal lastAscStepValue(PayslipRecordDTO p) {
        BigDecimal last = null;
        for (CalculationStep step : p.steps()) {
            if ("C-05 ASC".equals(step.engineName())) {
                last = step.resultValue();
            }
        }
        return last;
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String slug(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
    }
}

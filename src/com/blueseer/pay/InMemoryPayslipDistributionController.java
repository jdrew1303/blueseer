package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayslipDistributionDtos.EmailPayslipsRequestDTO;
import com.blueseer.pay.PayslipDistributionDtos.EmailRecipientDTO;
import com.blueseer.pay.PayslipDistributionDtos.EmailSendResult;
import com.blueseer.pay.PayslipDistributionDtos.EmailSendStatus;
import com.blueseer.pay.PayslipDistributionDtos.PeriodDTO;
import com.blueseer.pay.PayslipDistributionDtos.PrintPayslipsRequestDTO;
import com.blueseer.pay.PayslipDistributionDtos.PrintResult;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Track A/B/C stub for {@link IPayslipDistributionController}, backed by {@link PayrollStubStore}. */
final class InMemoryPayslipDistributionController implements IPayslipDistributionController {

    private static final String JASPER_DIR = "sf/jasper/";

    private final PayrollStubStore store;
    private final PayslipReportDataProvider dataProvider;

    InMemoryPayslipDistributionController(PayrollStubStore store, PayslipReportDataProvider dataProvider) {
        this.store = store;
        this.dataProvider = dataProvider;
    }

    @Override
    public List<PeriodDTO> getProcessedPeriods(CompanyId id) {
        Map<Integer, java.time.LocalDate> byPeriod = new TreeMap<>();
        for (PayslipRecordDTO p : store.payslips.values()) {
            byPeriod.put(p.periodNumber(), p.payDate());
        }
        List<PeriodDTO> periods = new ArrayList<>();
        for (Map.Entry<Integer, java.time.LocalDate> entry : byPeriod.entrySet()) {
            periods.add(new PeriodDTO(entry.getKey(), entry.getValue()));
        }
        return periods;
    }

    @Override
    public List<EmployeeSummaryDTO> getEmployeesForPeriod(CompanyId id, int periodNumber) {
        Set<EmployeeId> seen = new LinkedHashSet<>();
        List<EmployeeSummaryDTO> results = new ArrayList<>();
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.periodNumber() != periodNumber || !seen.add(p.employeeId())) {
                continue;
            }
            EmployeeRecordDTO rec = store.employees.get(p.employeeId());
            if (rec != null) {
                var personal = rec.personalDetails();
                results.add(new EmployeeSummaryDTO(p.employeeId(), personal.surname(), personal.firstName(), personal.worksNumber(), false));
            }
        }
        return results;
    }

    @Override
    public PrintResult printPayslips(PrintPayslipsRequestDTO req) {
        try {
            Map<String, Object> filters = new HashMap<>();
            filters.put("periodNumber", req.periodNumber());
            filters.put("employeeIds", new LinkedHashSet<>(req.employeeIds()));
            filters.put("includeZeroPayment", req.includeZeroPayment());
            List<PayslipReportDTO> beans = dataProvider.fetchReportData(
                    new IReportDataProvider.ReportCriteria(req.companyId(), null, null, filters));

            if (beans.isEmpty()) {
                return new PrintResult(false, "No payslips matched the selection.");
            }

            JasperReport report = JasperCompileManager.compileReport(JASPER_DIR + req.type().templateName() + ".jrxml");
            for (int copy = 0; copy < req.copies(); copy++) {
                JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(beans);
                JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(), dataSource);
                File outFile = new File("temp/payslips-period" + req.periodNumber() + "-copy" + (copy + 1) + ".pdf");
                File parent = outFile.getParentFile();
                if (parent != null) {
                    parent.mkdirs();
                }
                JasperExportManager.exportReportToPdfFile(print, outFile.getPath());
            }
            return new PrintResult(true, beans.size() + " payslip(s) x " + req.copies() + " copy/copies sent to the system print dialog.");
        } catch (Exception e) {
            return new PrintResult(false, "Print failed: " + e.getMessage());
        }
    }

    /**
     * There is no real SMTP/mail-transport integration in this stub - honest
     * per-recipient simulation only: a recipient with a non-blank email
     * address and a matching rendered payslip is reported {@code SENT}, one
     * with no email address or no data for the period is reported
     * {@code FAILED}. No bytes actually leave the process.
     */
    @Override
    public EmailSendResult sendPayslipEmails(EmailPayslipsRequestDTO req, IPayslipDistributionController.PayslipEmailProgressListener listener) {
        int sent = 0;
        int failed = 0;
        for (EmailRecipientDTO recipient : req.recipients()) {
            boolean ok = recipient.emailAddress() != null && !recipient.emailAddress().isBlank();
            EmailSendStatus status = ok ? EmailSendStatus.SENT : EmailSendStatus.FAILED;
            if (ok) {
                sent++;
            } else {
                failed++;
            }
            if (listener != null) {
                listener.onRecipientSent(recipient.employeeId(), status);
            }
        }
        return new EmailSendResult(sent, failed);
    }
}

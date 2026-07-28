package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollStubStore.PsrBatchRecord;
import com.blueseer.pay.RemittanceDtos.DateRange;
import com.blueseer.pay.RemittanceDtos.PaymentDueDateDTO;
import com.blueseer.pay.RemittanceDtos.PaymentRecordRowDTO;
import com.blueseer.pay.RemittanceDtos.RemittanceSummaryDTO;
import com.blueseer.pay.RemittanceDtos.ReturnSearchResultDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Track A/B/C stub for {@link IRemittanceController}, backed by {@link
 * PayrollStubStore}. Monthly remittance is grouped by the calendar month of
 * each finalised payslip's {@code payDate}, due the 23rd of the following
 * month (Revenue's own ROS-filing extension date - the paper/non-ROS
 * deadline is the 14th, but ROS filing is the practical default for
 * software-driven payroll). No LPT figure is tracked per payslip in this
 * module, so the remittance total is PAYE + PRSI (employee) + USC only -
 * the same honest category limitation as S-62's journal mapping.
 */
final class InMemoryRemittanceController implements IRemittanceController {

    private static final int ROS_FILING_DAY = 23;

    private final PayrollStubStore store;

    InMemoryRemittanceController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public RemittanceSummaryDTO getRemittanceSummary(CompanyId id) {
        Map<YearMonth, BigDecimal> totals = monthlyTotals();
        if (totals.isEmpty()) {
            return new RemittanceSummaryDTO(BigDecimal.ZERO, null, "No remittance due - no finalised payslips yet.");
        }
        YearMonth latest = ((TreeMap<YearMonth, BigDecimal>) totals).lastKey();
        BigDecimal amount = totals.get(latest);
        LocalDate dueDate = dueDateFor(latest);
        String status = dueDate.isBefore(LocalDate.now()) ? "Overdue" : "Upcoming";
        return new RemittanceSummaryDTO(amount, dueDate, status);
    }

    @Override
    public List<PaymentDueDateDTO> getPaymentDueDates(CompanyId id, int taxYear) {
        List<PaymentDueDateDTO> rows = new ArrayList<>();
        for (Map.Entry<YearMonth, BigDecimal> entry : monthlyTotals().entrySet()) {
            if (entry.getKey().getYear() != taxYear) {
                continue;
            }
            rows.add(new PaymentDueDateDTO(entry.getKey().toString(), "PAYE/PRSI/USC Remittance (P30)", dueDateFor(entry.getKey())));
        }
        return rows;
    }

    @Override
    public List<PaymentRecordRowDTO> getPaymentsRecord(CompanyId id, DateRange range) {
        List<PaymentRecordRowDTO> rows = new ArrayList<>();
        for (PaymentRecordRowDTO row : store.revenuePayments) {
            boolean afterFrom = range == null || range.from() == null || !row.date().isBefore(range.from());
            boolean beforeTo = range == null || range.to() == null || !row.date().isAfter(range.to());
            if (afterFrom && beforeTo) {
                rows.add(row);
            }
        }
        return rows;
    }

    @Override
    public List<ReturnSearchResultDTO> searchReturns(CompanyId id, String query) {
        String needle = query == null ? "" : query.trim().toLowerCase();
        List<ReturnSearchResultDTO> results = new ArrayList<>();
        for (PsrBatchRecord batch : store.psrBatches.values()) {
            String period = "Period ending " + batch.payDate();
            String refOrEmpty = batch.revenueReferenceNumberOrNull() == null ? "" : batch.revenueReferenceNumberOrNull();
            if (needle.isEmpty() || period.toLowerCase().contains(needle) || refOrEmpty.toLowerCase().contains(needle)
                    || batch.status().toLowerCase().contains(needle)) {
                results.add(new ReturnSearchResultDTO("PSR", period, batch.payDate(), batch.status()));
            }
        }
        return results;
    }

    private TreeMap<YearMonth, BigDecimal> monthlyTotals() {
        TreeMap<YearMonth, BigDecimal> totals = new TreeMap<>();
        for (PayslipRecordDTO p : store.payslips.values()) {
            YearMonth month = YearMonth.from(p.payDate());
            BigDecimal amount = p.paye().add(p.prsiEmployee()).add(p.usc());
            totals.merge(month, amount, BigDecimal::add);
        }
        return totals;
    }

    private static LocalDate dueDateFor(YearMonth payMonth) {
        return payMonth.plusMonths(1).atDay(Math.min(ROS_FILING_DAY, payMonth.plusMonths(1).lengthOfMonth()));
    }
}

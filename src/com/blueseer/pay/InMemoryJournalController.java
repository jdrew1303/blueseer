package com.blueseer.pay;

import com.blueseer.pay.JournalDtos.AccountingTarget;
import com.blueseer.pay.JournalDtos.JournalMappingRowDTO;
import com.blueseer.pay.JournalDtos.MappingCompletenessDTO;
import com.blueseer.pay.JournalDtos.NativeGlPostResult;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Track A/B/C stub for {@link IJournalController}, backed by {@link
 * PayrollStubStore}. Per-period category totals are summed directly from
 * {@link PayrollStubStore#payslips} rather than a separate journal-staging
 * table - this module tracks gross/PAYE/PRSI-employee/USC/net per payslip
 * but not a separate employer-PRSI or per-scheme pension split, so the
 * journal's category list is limited to what is genuinely available rather
 * than fabricating a fuller chart-of-accounts breakdown.
 */
final class InMemoryJournalController implements IJournalController {

    static final List<String> PAYROLL_CATEGORIES = List.of("Gross Pay", "PAYE", "PRSI (Employee)", "USC", "Net Pay");

    private final PayrollStubStore store;

    InMemoryJournalController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public List<JournalMappingRowDTO> getMapping(CompanyId id, AccountingTarget target) {
        Map<String, String> existing = store.journalMappings.getOrDefault(target, Map.of());
        return PAYROLL_CATEGORIES.stream()
                .map(cat -> new JournalMappingRowDTO(cat, existing.getOrDefault(cat, "")))
                .toList();
    }

    @Override
    public SaveResult saveMapping(CompanyId id, AccountingTarget target, List<JournalMappingRowDTO> rows) {
        Map<String, String> mapping = new LinkedHashMap<>();
        for (JournalMappingRowDTO row : rows) {
            mapping.put(row.payrollCategory(), row.glAccountCode() == null ? "" : row.glAccountCode());
        }
        store.journalMappings.put(target, mapping);
        return new SaveResult(true, "Mapping saved for " + target.label() + ".");
    }

    @Override
    public MappingCompletenessDTO checkMappingCompleteness(CompanyId id, AccountingTarget target) {
        Map<String, String> mapping = store.journalMappings.getOrDefault(target, Map.of());
        List<String> missing = PAYROLL_CATEGORIES.stream()
                .filter(cat -> mapping.getOrDefault(cat, "").isBlank())
                .toList();
        return new MappingCompletenessDTO(missing.isEmpty(), missing);
    }

    @Override
    public byte[] createExportFile(CompanyId id, int periodNumber, AccountingTarget target) {
        Map<String, String> mapping = store.journalMappings.getOrDefault(target, Map.of());
        Map<String, BigDecimal> totals = periodTotals(periodNumber);

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (PrintWriter writer = new PrintWriter(out, false, StandardCharsets.UTF_8)) {
            writer.println("Payroll Category,GL Account Code,Debit,Credit");
            for (String category : PAYROLL_CATEGORIES) {
                BigDecimal amount = totals.getOrDefault(category, BigDecimal.ZERO);
                String glCode = mapping.getOrDefault(category, "");
                boolean isDebit = category.equals("Gross Pay");
                writer.println(escapeCsv(category) + "," + escapeCsv(glCode) + ","
                        + (isDebit ? amount.toPlainString() : "0.00") + ","
                        + (isDebit ? "0.00" : amount.toPlainString()));
            }
        }
        return out.toByteArray();
    }

    @Override
    public NativeGlPostResult postToNativeGl(CompanyId id, int periodNumber) {
        MappingCompletenessDTO completeness = checkMappingCompleteness(id, AccountingTarget.NATIVE_BLUESEER_GL);
        if (!completeness.complete()) {
            return new NativeGlPostResult(false, null, "Mapping incomplete for Native BlueSeer GL - missing: " + completeness.missingCategories());
        }
        // Track A stub: com.blueseer.fgl's GL posting isn't wired into this
        // module yet, so this generates a batch reference and records intent
        // without touching glic_accts/gltrans_mstr.
        String ref = "GLBATCH-" + store.nextGlBatchRef();
        return new NativeGlPostResult(true, ref, "Posted period " + periodNumber + " to the native GL as batch " + ref + ".");
    }

    private Map<String, BigDecimal> periodTotals(int periodNumber) {
        Map<String, BigDecimal> totals = new LinkedHashMap<>();
        for (String category : PAYROLL_CATEGORIES) {
            totals.put(category, BigDecimal.ZERO);
        }
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.periodNumber() != periodNumber) {
                continue;
            }
            totals.merge("Gross Pay", p.gross(), BigDecimal::add);
            totals.merge("PAYE", p.paye(), BigDecimal::add);
            totals.merge("PRSI (Employee)", p.prsiEmployee(), BigDecimal::add);
            totals.merge("USC", p.usc(), BigDecimal::add);
            totals.merge("Net Pay", p.net(), BigDecimal::add);
        }
        return totals;
    }

    private static String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        return value.contains(",") ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
    }
}

package com.blueseer.pay;

import java.util.List;

/** DTOs backing S-62 Payroll Journal Mapping and S-63 Journal Export. */
public final class JournalDtos {

    private JournalDtos() {
    }

    public enum AccountingTarget {
        QUICKBOOKS("Quickbooks"), SAGE_LINE_50("Sage Line 50"), XERO_ONLINE("Xero Online"), NATIVE_BLUESEER_GL("Native BlueSeer GL");

        private final String label;

        AccountingTarget(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        public static AccountingTarget fromLabel(String label) {
            for (AccountingTarget t : values()) {
                if (t.label.equals(label)) {
                    return t;
                }
            }
            throw new IllegalArgumentException("Unknown accounting target label: " + label);
        }
    }

    public record JournalMappingRowDTO(String payrollCategory, String glAccountCode) {
    }

    public record MappingCompletenessDTO(boolean complete, List<String> missingCategories) {
    }

    public record NativeGlPostResult(boolean success, String glBatchReferenceOrNull, String message) {
    }
}

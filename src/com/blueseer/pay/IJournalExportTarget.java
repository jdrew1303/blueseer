package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.List;

/**
 * §4.1 target abstraction for S-63's Journal Export action - one
 * implementation for CSV mapping (Quickbooks/Sage/Xero, per S-62's mapping
 * table) and one native implementation posting directly into
 * {@code com.blueseer.fgl}, selected per {@code JournalMappingService}'s
 * configured target.
 */
public interface IJournalExportTarget {

    JournalExportResult export(List<JournalLineDTO> lines);

    record JournalLineDTO(String payrollCategory, String glAccountCode, BigDecimal debit, BigDecimal credit) {
    }

    record JournalExportResult(boolean success, String detailMessageOrNull) {
    }
}

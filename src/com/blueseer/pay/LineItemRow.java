package com.blueseer.pay;

import com.blueseer.pay.ITaxYearRules.AscGroup;
import com.blueseer.pay.PayrollIds.LineItemId;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Mutable row backing S-07/S-08's {@code tblAdditions}/{@code tblDeductions}
 * table models - a common shape for both {@code AdditionLineDTO} and
 * {@code DeductionLineDTO} so one table-model/panel implementation serves
 * both tabs. {@code flag} is "Taxable?" on S-07 and "Pre-Tax?" on S-08.
 */
final class LineItemRow {

    LineItemId id;
    String description = "";
    boolean flag;
    BigDecimal amount;
    LocalDate endDate;
    AscGroup ascGroup;
    BigDecimal ascOverrideAmount;
    BigDecimal ascOverridePercentage;

    LineItemRow(LineItemId id) {
        this.id = id;
    }

    boolean isAsc() {
        return "ASC".equals(description);
    }
}

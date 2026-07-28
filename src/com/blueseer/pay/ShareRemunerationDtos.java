package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.time.LocalDate;

/** DTOs backing S-26 Share-Based Remuneration (C-16). */
public final class ShareRemunerationDtos {

    private ShareRemunerationDtos() {
    }

    public enum SettlementType { SHARE_SETTLED, CASH_SETTLED }

    public record ShareVestingDTO(
            EmployeeId employeeId,
            LocalDate vestingDate,
            BigDecimal numberOfShares,
            BigDecimal marketValuePerShare,
            SettlementType settlementType,
            boolean sharesInEmployingCompanyOrParent,
            LocalDate settlementDateOrNull) {
    }

    /**
     * @param scheduledRemittanceDate C-16's full remittance-date logic (which
     *                                can fall later than the vesting date
     *                                when settlement happens within 60 days)
     *                                is out of scope for this pass - the
     *                                vesting date is echoed back as a
     *                                placeholder rather than computed.
     */
    public record ShareVestingPreviewDTO(BigDecimal taxableValue, boolean employerPrsiExempt, LocalDate scheduledRemittanceDate) {
    }
}

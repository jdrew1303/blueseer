package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.PayslipId;

/** DTOs backing S-49 Leaver (mid pay period). */
public final class LeaverDtos {

    private LeaverDtos() {
    }

    public record OffCycleFinalisationResult(boolean success, PayslipId payslipIdOrNull, int periodNumber, String message) {
    }
}

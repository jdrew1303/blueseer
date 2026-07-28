package com.blueseer.pay;

import java.math.BigDecimal;

/** DTOs backing S-64 Changing an Employee's Pay Frequency. */
public final class PayFrequencyDtos {

    private PayFrequencyDtos() {
    }

    public record FrequencyChangePreviewDTO(
            PayFrequency oldFrequency,
            PayFrequency newFrequency,
            int periodsCompletedUnderOldFrequency,
            BigDecimal cumulativeGrossPayToDate,
            BigDecimal cumulativeTaxPaidToDate,
            int remainingPeriodsUnderNewFrequency,
            String explanation) {
    }
}

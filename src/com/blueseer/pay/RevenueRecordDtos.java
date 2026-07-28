package com.blueseer.pay;

/** DTOs backing S-35/S-70 (both share {@link IRevenueRecordController#compareRecord}). */
public final class RevenueRecordDtos {

    private RevenueRecordDtos() {
    }

    public record RecordComparisonRowDTO(String field, String localValue, String revenueValue, boolean match) {
    }
}

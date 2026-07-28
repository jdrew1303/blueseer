package com.blueseer.pay;

import java.util.List;

/** DTOs backing S-65 Start New Tax Year. */
public final class TaxYearRolloverDtos {

    private TaxYearRolloverDtos() {
    }

    public record ChecklistRowDTO(String label, boolean satisfied, String detail) {
    }

    public record YearEndChecklistDTO(List<ChecklistRowDTO> rows) {
        public boolean allSatisfied() {
            return rows.stream().allMatch(ChecklistRowDTO::satisfied);
        }
    }

    public record TaxYearRolloverResult(boolean success, String message) {
    }
}

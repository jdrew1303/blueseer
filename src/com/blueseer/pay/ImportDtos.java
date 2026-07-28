package com.blueseer.pay;

import java.util.List;
import java.util.Map;

/** DTOs backing S-28/S-29 (the shared {@code ImportHoursWizard}). */
public final class ImportDtos {

    private ImportDtos() {
    }

    /** S-28 maps only hours; S-29 maps a full period's worth of pay data - same wizard, different target-field list. */
    public enum ImportProfile { HOURS_ONLY, FULL_PERIOD }

    /** Target field names offered by {@code tblColumnMapping}'s combo editor, per {@link ImportProfile}. */
    public static List<String> targetFields(ImportProfile profile) {
        return switch (profile) {
            case HOURS_ONLY -> List.of("(ignore)", "WorksNumber", "StandardHours", "TimeAndAThirdHours", "TimeAndAHalfHours", "DoubleTimeHours");
            case FULL_PERIOD -> List.of("(ignore)", "WorksNumber", "StandardHours", "TimeAndAThirdHours", "TimeAndAHalfHours",
                    "DoubleTimeHours", "BasicPay", "HolidayPayAmount");
        };
    }

    /** {@code sourceColumnHeader -> targetFieldName}, entries omitted or {@code "(ignore)"} are not imported. */
    public record ColumnMappingDTO(Map<String, String> sourceToTarget) {
    }

    public record ImportRowResultDTO(int rowNumber, Map<String, String> rawValues, boolean valid, String errorOrNull) {
    }

    public record ImportValidationResultDTO(List<ImportRowResultDTO> rows) {
    }

    public record ImportCommitResultDTO(int committedCount, int excludedCount, List<String> messages) {
    }
}

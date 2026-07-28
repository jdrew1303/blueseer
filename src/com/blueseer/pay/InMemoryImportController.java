package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.ImportDtos.ColumnMappingDTO;
import com.blueseer.pay.ImportDtos.ImportCommitResultDTO;
import com.blueseer.pay.ImportDtos.ImportProfile;
import com.blueseer.pay.ImportDtos.ImportRowResultDTO;
import com.blueseer.pay.ImportDtos.ImportValidationResultDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Track A stub for {@link IImportController} - a plain comma-split parser
 * (no quoted-field/escaping support), which is enough for the flat
 * hours/full-period exports this screen targets.
 */
final class InMemoryImportController implements IImportController {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final List<String> NUMERIC_FIELDS = List.of(
            "StandardHours", "TimeAndAThirdHours", "TimeAndAHalfHours", "DoubleTimeHours", "BasicPay", "HolidayPayAmount");

    private final PayrollStubStore store;
    private final IEmployeeRepository employeeRepository;
    private final IPayEntryController payEntryController;

    InMemoryImportController(PayrollStubStore store, IEmployeeRepository employeeRepository, IPayEntryController payEntryController) {
        this.store = store;
        this.employeeRepository = employeeRepository;
        this.payEntryController = payEntryController;
    }

    @Override
    public List<String> parseFileHeaders(File f, ImportProfile profile) {
        List<String> lines = readLines(f);
        if (lines.isEmpty()) {
            return List.of();
        }
        return splitCsvLine(lines.get(0));
    }

    @Override
    public ImportValidationResultDTO validateImport(File f, ColumnMappingDTO mapping, ImportProfile profile) {
        return validateRows(f, mapping);
    }

    @Override
    public ImportCommitResultDTO commitImport(File f, ColumnMappingDTO mapping, ImportProfile profile) {
        ImportValidationResultDTO validation = validateRows(f, mapping);
        int committed = 0;
        int excluded = 0;
        List<String> messages = new ArrayList<>();
        for (ImportRowResultDTO row : validation.rows()) {
            if (!row.valid()) {
                excluded++;
                messages.add("Row " + row.rowNumber() + " excluded: " + row.errorOrNull());
                continue;
            }
            EmployeeId employeeId = findByWorksNumber(row.rawValues().get("WorksNumber"));
            int period = store.currentPeriodNumber;
            PayEntryDTO existing = payEntryController.loadPayEntry(employeeId, period);
            PayEntryDTO updated = new PayEntryDTO(employeeId, period, existing.hourlyRate(),
                    numericOrExisting(row, "StandardHours", existing.standardHours()),
                    numericOrExisting(row, "TimeAndAThirdHours", existing.timeAndAThirdHours()),
                    numericOrExisting(row, "TimeAndAHalfHours", existing.timeAndAHalfHours()),
                    numericOrExisting(row, "DoubleTimeHours", existing.doubleTimeHours()),
                    numericOrExisting(row, "BasicPay", existing.basicPay()),
                    numericOrExisting(row, "HolidayPayAmount", existing.holidayPayAmount()),
                    existing.additionalWeeksSpread(), existing.leaving(), existing.leaveDateOrNull(), existing.noteOrNull());
            payEntryController.savePayEntry(updated);
            committed++;
        }
        return new ImportCommitResultDTO(committed, excluded, messages);
    }

    private ImportValidationResultDTO validateRows(File f, ColumnMappingDTO mapping) {
        List<String> lines = readLines(f);
        List<ImportRowResultDTO> results = new ArrayList<>();
        if (lines.isEmpty()) {
            return new ImportValidationResultDTO(results);
        }
        List<String> headers = splitCsvLine(lines.get(0));
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) {
                continue;
            }
            List<String> values = splitCsvLine(lines.get(i));
            Map<String, String> raw = new LinkedHashMap<>();
            for (int c = 0; c < headers.size() && c < values.size(); c++) {
                String target = mapping.sourceToTarget().get(headers.get(c));
                if (target != null && !"(ignore)".equals(target)) {
                    raw.put(target, values.get(c));
                }
            }
            String error = validateRow(raw);
            results.add(new ImportRowResultDTO(i, raw, error == null, error));
        }
        return new ImportValidationResultDTO(results);
    }

    private String validateRow(Map<String, String> raw) {
        String worksNumber = raw.get("WorksNumber");
        if (worksNumber == null || worksNumber.isBlank()) {
            return "WorksNumber is required (check the column mapping)";
        }
        if (findByWorksNumber(worksNumber) == null) {
            return "No active employee found with Works Number '" + worksNumber + "'";
        }
        for (String field : NUMERIC_FIELDS) {
            String value = raw.get(field);
            if (value != null && !value.isBlank()) {
                try {
                    new BigDecimal(value.trim());
                } catch (NumberFormatException e) {
                    return field + " value '" + value + "' is not numeric";
                }
            }
        }
        return null;
    }

    private EmployeeId findByWorksNumber(String worksNumber) {
        if (worksNumber == null) {
            return null;
        }
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            if (!summary.isFormerEmployee() && worksNumber.trim().equalsIgnoreCase(summary.worksNumber())) {
                return summary.id();
            }
        }
        return null;
    }

    private static BigDecimal numericOrExisting(ImportRowResultDTO row, String field, BigDecimal existingValue) {
        String value = row.rawValues().get(field);
        return value == null || value.isBlank() ? existingValue : new BigDecimal(value.trim());
    }

    private static List<String> readLines(File f) {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new FileReader(f))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            // an unreadable/missing file simply yields no headers/rows - the
            // wizard's own "choose-file" step already guards against this
            // via JFileChooser, so this is a defensive fallback, not the
            // primary error path
        }
        return lines;
    }

    private static List<String> splitCsvLine(String line) {
        List<String> values = new ArrayList<>();
        for (String part : line.split(",", -1)) {
            values.add(part.trim());
        }
        return values;
    }
}

package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.DeductionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.LineItemId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Track A stub for {@link IAdditionDeductionService}, backed by {@link PayrollStubStore}. */
final class InMemoryAdditionDeductionService implements IAdditionDeductionService {

    // "ASC" is pinned first whenever the partial text is a prefix of it, per S-08.
    private static final List<String> KNOWN_DESCRIPTIONS = List.of("ASC", "Pension", "Union Dues", "Bonus", "Travel & Subsistence");

    private final PayrollStubStore store;

    InMemoryAdditionDeductionService(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public SaveResult saveAdditions(EmployeeId id, List<AdditionLineDTO> lines) {
        EmployeeRecordDTO existing = store.employees.get(id);
        if (existing != null) {
            store.employees.put(id, new EmployeeRecordDTO(existing.id(), existing.personalDetails(), existing.revenueDetails(),
                    List.copyOf(lines), existing.deductions(), existing.cumulatives(), existing.hrDetails(), existing.csoDetails()));
        }
        return new SaveResult(false, null);
    }

    @Override
    public SaveResult saveDeductions(EmployeeId id, List<DeductionLineDTO> lines) {
        EmployeeRecordDTO existing = store.employees.get(id);
        if (existing != null) {
            store.employees.put(id, new EmployeeRecordDTO(existing.id(), existing.personalDetails(), existing.revenueDetails(),
                    existing.additions(), List.copyOf(lines), existing.cumulatives(), existing.hrDetails(), existing.csoDetails()));
        }
        return new SaveResult(false, null);
    }

    @Override
    public List<String> suggestDescriptions(CompanyId companyId, String partial) {
        String needle = partial == null ? "" : partial.toLowerCase();
        Set<String> matches = new LinkedHashSet<>();
        if (!needle.isEmpty() && "asc".startsWith(needle)) {
            matches.add("ASC");
        }
        for (String candidate : KNOWN_DESCRIPTIONS) {
            if (needle.isEmpty() || candidate.toLowerCase().contains(needle)) {
                matches.add(candidate);
            }
        }
        return new ArrayList<>(matches);
    }

    @Override
    public void setAdditionDeductionEndDate(EmployeeId id, LineItemId lineId, LocalDate endDateOrNull) {
        EmployeeRecordDTO existing = store.employees.get(id);
        if (existing == null) {
            return;
        }
        List<AdditionLineDTO> updatedAdditions = new ArrayList<>();
        for (AdditionLineDTO line : existing.additions()) {
            updatedAdditions.add(line.id().equals(lineId)
                    ? new AdditionLineDTO(line.id(), line.description(), line.taxable(), line.amount(), endDateOrNull)
                    : line);
        }
        List<DeductionLineDTO> updatedDeductions = new ArrayList<>();
        for (DeductionLineDTO line : existing.deductions()) {
            updatedDeductions.add(line.id().equals(lineId)
                    ? new DeductionLineDTO(line.id(), line.description(), line.preTax(), line.amount(), endDateOrNull,
                            line.ascGroupOrNull(), line.ascOverrideAmountOrNull(), line.ascOverridePercentageOrNull())
                    : line);
        }
        store.employees.put(id, new EmployeeRecordDTO(existing.id(), existing.personalDetails(), existing.revenueDetails(),
                updatedAdditions, updatedDeductions, existing.cumulatives(), existing.hrDetails(), existing.csoDetails()));
    }
}

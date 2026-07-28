package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.CsoDetailsDTO;
import com.blueseer.pay.EmployeeDtos.CumulativesDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.EmployeeDtos.HrDetailsDTO;
import com.blueseer.pay.EmployeeDtos.PersonalDetailsDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Track A stub for {@link IEmployeeRepository}, backed by {@link PayrollStubStore}. */
final class InMemoryEmployeeRepository implements IEmployeeRepository {

    private final PayrollStubStore store;

    InMemoryEmployeeRepository(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public List<EmployeeSummaryDTO> searchEmployees(CompanyId companyId, String query) {
        List<EmployeeSummaryDTO> results = new ArrayList<>();
        String needle = query == null ? "" : query.toLowerCase();
        for (EmployeeRecordDTO rec : store.employees.values()) {
            PersonalDetailsDTO p = rec.personalDetails();
            if (needle.isEmpty() || p.surname().toLowerCase().contains(needle) || p.firstName().toLowerCase().contains(needle)) {
                boolean isFormer = store.leaveDates.containsKey(rec.id());
                results.add(new EmployeeSummaryDTO(rec.id(), p.surname(), p.firstName(), p.worksNumber(), isFormer));
            }
        }
        return results;
    }

    @Override
    public EmployeeRecordDTO loadEmployeeRecord(EmployeeId id) {
        return store.employees.get(id);
    }

    @Override
    public boolean canDeleteEmployee(EmployeeId id) {
        return true;
    }

    @Override
    public void deleteEmployee(EmployeeId id) {
        store.employees.remove(id);
    }

    @Override
    public SaveResult savePersonalDetails(PersonalDetailsDTO data) {
        boolean wasCreate = !store.employees.containsKey(data.id());
        EmployeeRecordDTO existing = store.employees.get(data.id());
        EmployeeRecordDTO updated = wasCreate
                ? new EmployeeRecordDTO(data.id(), data, null, List.of(), List.of(),
                        new CumulativesDTO(null, null, null, null), new HrDetailsDTO("", "", "", ""), new CsoDetailsDTO("", ""))
                : new EmployeeRecordDTO(existing.id(), data, existing.revenueDetails(), existing.additions(), existing.deductions(),
                        existing.cumulatives(), existing.hrDetails(), existing.csoDetails());
        store.employees.put(data.id(), updated);
        return new SaveResult(wasCreate, null);
    }

    @Override
    public SaveResult saveMidYearCumulatives(EmployeeId id, CumulativesDTO data) {
        EmployeeRecordDTO existing = store.employees.get(id);
        if (existing != null) {
            store.employees.put(id, new EmployeeRecordDTO(existing.id(), existing.personalDetails(), existing.revenueDetails(),
                    existing.additions(), existing.deductions(), data, existing.hrDetails(), existing.csoDetails()));
        }
        return new SaveResult(false, null);
    }

    @Override
    public LocalDate getLeaveDate(EmployeeId id) {
        return store.leaveDates.get(id);
    }

    @Override
    public PersonalDetailsDTO prepareRejoinerRecord(EmployeeId formerEmployeeId) {
        EmployeeRecordDTO former = store.employees.get(formerEmployeeId);
        if (former == null) {
            return null;
        }
        PersonalDetailsDTO p = former.personalDetails();
        EmployeeId freshId = new EmployeeId(System.currentTimeMillis());
        // Name/address/PPS only - additions, deductions, and revenue details
        // are deliberately left for the user to re-enter (S-13).
        return new PersonalDetailsDTO(freshId, p.surname(), p.firstName(), p.address(),
                p.dateOfBirth(), p.email(), "", false, null,
                p.ppsNumber(), "(auto-populated on save)", "",
                null, null, null, "", "", "", "", "");
    }

    @Override
    public SaveResult saveHrDetails(EmployeeId id, HrDetailsDTO data) {
        EmployeeRecordDTO existing = store.employees.get(id);
        if (existing != null) {
            store.employees.put(id, new EmployeeRecordDTO(existing.id(), existing.personalDetails(), existing.revenueDetails(),
                    existing.additions(), existing.deductions(), existing.cumulatives(), data, existing.csoDetails()));
        }
        return new SaveResult(false, null);
    }

    @Override
    public SaveResult saveCsoDetails(EmployeeId id, CsoDetailsDTO data) {
        EmployeeRecordDTO existing = store.employees.get(id);
        if (existing != null) {
            store.employees.put(id, new EmployeeRecordDTO(existing.id(), existing.personalDetails(), existing.revenueDetails(),
                    existing.additions(), existing.deductions(), existing.cumulatives(), existing.hrDetails(), data));
        }
        return new SaveResult(false, null);
    }
}

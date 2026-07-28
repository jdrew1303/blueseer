package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.DepartmentDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.DepartmentId;

import java.util.ArrayList;
import java.util.List;

/** Track A stub for {@link IDepartmentController}, backed by {@link PayrollStubStore}. */
final class InMemoryDepartmentController implements IDepartmentController {

    private final PayrollStubStore store;

    InMemoryDepartmentController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public List<DepartmentDTO> listDepartments(CompanyId id) {
        return new ArrayList<>(store.departments.values());
    }

    @Override
    public DepartmentDTO saveDepartment(DepartmentDTO dept) {
        DepartmentId id = dept.id() != null ? dept.id() : new DepartmentId(System.currentTimeMillis());
        DepartmentDTO saved = new DepartmentDTO(id, dept.code(), dept.name());
        store.departments.put(id, saved);
        return saved;
    }

    @Override
    public boolean canDeleteDepartment(DepartmentId id) {
        return employeeCount(id) == 0;
    }

    @Override
    public void deleteDepartment(DepartmentId id) {
        store.departments.remove(id);
    }

    private int employeeCount(DepartmentId id) {
        int count = 0;
        for (EmployeeRecordDTO rec : store.employees.values()) {
            if (id.equals(rec.personalDetails().departmentId())) {
                count++;
            }
        }
        return count;
    }
}

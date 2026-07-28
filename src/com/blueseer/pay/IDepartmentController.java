package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.DepartmentDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.DepartmentId;

import java.util.List;

/** Controller backing S-09 Departments Maintenance. */
public interface IDepartmentController {

    List<DepartmentDTO> listDepartments(CompanyId id);

    DepartmentDTO saveDepartment(DepartmentDTO dept);

    /** True only if the department's employee count is zero. */
    boolean canDeleteDepartment(DepartmentId id);

    void deleteDepartment(DepartmentId id);
}

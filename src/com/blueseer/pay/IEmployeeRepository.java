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
import java.util.List;

/**
 * §4.1 repository over {@code emp_mstr} (extended) and the new
 * {@code emp_pay_ie}-family tables. Backs S-04/S-05/S-10/S-11/S-12's
 * IEmployeeMaintenanceController implementation - this interface is the
 * data layer beneath that Controller, not the Controller itself.
 */
public interface IEmployeeRepository {

    List<EmployeeSummaryDTO> searchEmployees(CompanyId companyId, String query);

    EmployeeRecordDTO loadEmployeeRecord(EmployeeId id);

    /** True only if the employee has no finalised payslips this tax year (S-04 btDelete guard). */
    boolean canDeleteEmployee(EmployeeId id);

    void deleteEmployee(EmployeeId id);

    SaveResult savePersonalDetails(PersonalDetailsDTO data);

    SaveResult saveMidYearCumulatives(EmployeeId id, CumulativesDTO data);

    /** Nullable - populated once a leaver event (S-48/S-49) has been processed elsewhere. */
    LocalDate getLeaveDate(EmployeeId id);

    SaveResult saveHrDetails(EmployeeId id, HrDetailsDTO data);

    SaveResult saveCsoDetails(EmployeeId id, CsoDetailsDTO data);

    /**
     * S-13 Leavers Re-joining - returns a {@link PersonalDetailsDTO} pre-filled
     * with name/address/PPS only (and a fresh Employment ID placeholder),
     * never a full {@code EmployeeRecordDTO}: additions, deductions, and
     * revenue details are deliberately left for the user to re-enter rather
     * than carried forward from the former employment.
     */
    PersonalDetailsDTO prepareRejoinerRecord(EmployeeId formerEmployeeId);
}

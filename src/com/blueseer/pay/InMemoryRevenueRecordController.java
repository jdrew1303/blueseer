package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.PersonalDetailsDTO;
import com.blueseer.pay.EmployeeDtos.RevenueDetailsDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.RevenueRecordDtos.RecordComparisonRowDTO;

import java.util.ArrayList;
import java.util.List;

/**
 * Track A stub for {@link IRevenueRecordController}, backed by {@link
 * PayrollStubStore}. There is no real ROS connection yet (§3.3 RPN/EDI is
 * deferred), so "Revenue Value" mirrors "Local Value" for every field - an
 * honest reflection of having nothing to actually compare against yet,
 * rather than fabricating a discrepancy.
 */
final class InMemoryRevenueRecordController implements IRevenueRecordController {

    private final PayrollStubStore store;

    InMemoryRevenueRecordController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public List<RecordComparisonRowDTO> compareRecord(EmployeeId idOrNull) {
        return idOrNull == null ? compareEmployer() : compareEmployee(idOrNull);
    }

    private List<RecordComparisonRowDTO> compareEmployer() {
        long activeEmployees = store.employees.keySet().stream().filter(id -> !store.leaveDates.containsKey(id)).count();
        List<RecordComparisonRowDTO> rows = new ArrayList<>();
        rows.add(mirrored("PAYE Registration Status", "Registered"));
        rows.add(mirrored("Active Employee Count", String.valueOf(activeEmployees)));
        return rows;
    }

    private List<RecordComparisonRowDTO> compareEmployee(EmployeeId id) {
        EmployeeRecordDTO rec = store.employees.get(id);
        List<RecordComparisonRowDTO> rows = new ArrayList<>();
        if (rec == null) {
            return rows;
        }
        PersonalDetailsDTO p = rec.personalDetails();
        rows.add(mirrored("Surname", p.surname()));
        rows.add(mirrored("First Name", p.firstName()));
        rows.add(mirrored("PPS Number", p.ppsNumber()));
        rows.add(mirrored("Employment ID", p.employmentId()));
        RevenueDetailsDTO r = rec.revenueDetails();
        rows.add(mirrored("PRSI Class", r == null ? "" : r.prsiClass()));
        return rows;
    }

    private static RecordComparisonRowDTO mirrored(String field, String value) {
        return new RecordComparisonRowDTO(field, value, value, true);
    }
}

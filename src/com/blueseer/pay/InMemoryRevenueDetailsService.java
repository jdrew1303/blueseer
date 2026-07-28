package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmergencyStatusDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.RevenueDetailsDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

/** Track A stub for {@link IRevenueDetailsService}, backed by {@link PayrollStubStore}. */
final class InMemoryRevenueDetailsService implements IRevenueDetailsService {

    private final PayrollStubStore store;

    InMemoryRevenueDetailsService(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public SaveResult saveRevenueDetails(RevenueDetailsDTO data) {
        EmployeeRecordDTO existing = store.employees.get(data.id());
        if (existing != null) {
            store.employees.put(data.id(), new EmployeeRecordDTO(existing.id(), existing.personalDetails(), data,
                    existing.additions(), existing.deductions(), existing.cumulatives(), existing.hrDetails(), existing.csoDetails()));
        }
        return new SaveResult(false, null);
    }

    @Override
    public EmergencyStatusDTO getCurrentEmergencyStatus(EmployeeId id) {
        boolean noRpnYet = !store.appliedRpnData.containsKey(id);
        return new EmergencyStatusDTO(noRpnYet, noRpnYet ? "No RPN received yet for this employment" : null);
    }
}

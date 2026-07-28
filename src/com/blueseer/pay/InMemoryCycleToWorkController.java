package com.blueseer.pay;

import com.blueseer.pay.CycleToWorkDtos.CycleEligibilityDTO;
import com.blueseer.pay.CycleToWorkDtos.CycleToWorkDTO;
import com.blueseer.pay.EmployeeDtos.DeductionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.LineItemId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Track A stub for {@link ICycleToWorkController}, backed by {@link PayrollStubStore}. */
final class InMemoryCycleToWorkController implements ICycleToWorkController {

    private final PayrollStubStore store;
    private final IAdditionDeductionService additionDeductionService;

    InMemoryCycleToWorkController(PayrollStubStore store, IAdditionDeductionService additionDeductionService) {
        this.store = store;
        this.additionDeductionService = additionDeductionService;
    }

    @Override
    public CycleEligibilityDTO checkEligibility(EmployeeId id) {
        LocalDate lastArrangement = store.lastCycleToWorkDate.get(id);
        boolean eligible = CycleToWorkCalculator.isEligible(lastArrangement, LocalDate.now());
        return new CycleEligibilityDTO(eligible, CycleToWorkCalculator.exemptionLimit(false), lastArrangement);
    }

    @Override
    public SaveResult saveSacrificeArrangement(CycleToWorkDTO draft) {
        LocalDate lastArrangement = store.lastCycleToWorkDate.get(draft.employeeId());
        if (!CycleToWorkCalculator.isEligible(lastArrangement, LocalDate.now())) {
            return new SaveResult(false, "Not eligible - the employee already had a Cycle to Work arrangement within the last 4 years (last: " + lastArrangement + ")");
        }
        EmployeeRecordDTO rec = store.employees.get(draft.employeeId());
        if (rec == null) {
            return new SaveResult(false, "Employee not found");
        }
        List<DeductionLineDTO> updated = new ArrayList<>(rec.deductions());
        updated.add(new DeductionLineDTO(new LineItemId(System.currentTimeMillis()), "Cycle to Work", true,
                draft.salaryForgonePerPeriod(), null, null, null, null));
        SaveResult result = additionDeductionService.saveDeductions(draft.employeeId(), updated);
        store.lastCycleToWorkDate.put(draft.employeeId(), draft.sacrificeStartDate());
        return result;
    }
}

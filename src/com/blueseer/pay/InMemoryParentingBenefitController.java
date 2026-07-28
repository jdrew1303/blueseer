package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.ParentingBenefitDtos.ParentingBenefitDTO;
import com.blueseer.pay.PayrollIds.LineItemId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Track A/B/C stub for {@link IParentingBenefitController}, backed by {@link PayrollStubStore}. */
final class InMemoryParentingBenefitController implements IParentingBenefitController {

    private final PayrollStubStore store;
    private final IAdditionDeductionService additionDeductionService;

    InMemoryParentingBenefitController(PayrollStubStore store, IAdditionDeductionService additionDeductionService) {
        this.store = store;
        this.additionDeductionService = additionDeductionService;
    }

    @Override
    public SaveResult saveLeaveRecord(ParentingBenefitDTO draft) {
        EmployeeRecordDTO rec = store.employees.get(draft.employeeId());
        if (rec == null) {
            return new SaveResult(false, "Employee not found");
        }
        BigDecimal topUp = draft.employerTopUpAmountPerPeriod();
        if (topUp != null && topUp.compareTo(BigDecimal.ZERO) > 0) {
            List<AdditionLineDTO> updated = new ArrayList<>(rec.additions());
            updated.add(new AdditionLineDTO(new LineItemId(System.currentTimeMillis()), "Employer Top-Up - " + draft.benefitType(),
                    true, topUp, draft.leaveEndDate()));
            return additionDeductionService.saveAdditions(draft.employeeId(), updated);
        }
        return new SaveResult(true, "Leave record saved.");
    }
}

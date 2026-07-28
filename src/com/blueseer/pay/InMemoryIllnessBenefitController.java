package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.IllnessBenefitDtos.IllnessBenefitDTO;
import com.blueseer.pay.PayrollIds.LineItemId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Track A/B/C stub for {@link IIllnessBenefitController}, backed by {@link PayrollStubStore}. */
final class InMemoryIllnessBenefitController implements IIllnessBenefitController {

    private final PayrollStubStore store;
    private final IAdditionDeductionService additionDeductionService;

    InMemoryIllnessBenefitController(PayrollStubStore store, IAdditionDeductionService additionDeductionService) {
        this.store = store;
        this.additionDeductionService = additionDeductionService;
    }

    @Override
    public SaveResult saveIllnessBenefitRecord(IllnessBenefitDTO draft) {
        EmployeeRecordDTO rec = store.employees.get(draft.employeeId());
        if (rec == null) {
            return new SaveResult(false, "Employee not found");
        }
        if (draft.dspMandateReimbursementAmountOrNull() != null) {
            store.illnessBenefitLedgerNotes.put(draft.employeeId(), draft.dspMandateReimbursementAmountOrNull());
        }
        BigDecimal employerPaid = draft.employerPaidAmountThisPeriod();
        if (employerPaid != null && employerPaid.compareTo(BigDecimal.ZERO) > 0) {
            List<AdditionLineDTO> updated = new ArrayList<>(rec.additions());
            updated.add(new AdditionLineDTO(new LineItemId(System.currentTimeMillis()), "Employer-Paid Sick Leave", true, employerPaid, null));
            return additionDeductionService.saveAdditions(draft.employeeId(), updated);
        }
        return new SaveResult(true, "Illness Benefit record saved.");
    }
}

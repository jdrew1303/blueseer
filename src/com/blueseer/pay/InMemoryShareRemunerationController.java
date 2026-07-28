package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayrollIds.LineItemId;
import com.blueseer.pay.ShareRemunerationDtos.ShareVestingDTO;
import com.blueseer.pay.ShareRemunerationDtos.ShareVestingPreviewDTO;

import java.util.ArrayList;
import java.util.List;

/** Track A stub for {@link IShareRemunerationController}, backed by {@link PayrollStubStore}. */
final class InMemoryShareRemunerationController implements IShareRemunerationController {

    private final PayrollStubStore store;
    private final IAdditionDeductionService additionDeductionService;

    InMemoryShareRemunerationController(PayrollStubStore store, IAdditionDeductionService additionDeductionService) {
        this.store = store;
        this.additionDeductionService = additionDeductionService;
    }

    @Override
    public ShareVestingPreviewDTO previewTaxableValue(ShareVestingDTO draft) {
        return ShareRemunerationCalculator.preview(draft);
    }

    @Override
    public SaveResult saveVestingEvent(ShareVestingDTO draft) {
        EmployeeRecordDTO rec = store.employees.get(draft.employeeId());
        if (rec == null) {
            return new SaveResult(false, "Employee not found");
        }
        ShareVestingPreviewDTO preview = ShareRemunerationCalculator.preview(draft);
        List<AdditionLineDTO> updated = new ArrayList<>(rec.additions());
        updated.add(new AdditionLineDTO(new LineItemId(System.currentTimeMillis()), "Share Vesting", true, preview.taxableValue(), null));
        return additionDeductionService.saveAdditions(draft.employeeId(), updated);
    }
}

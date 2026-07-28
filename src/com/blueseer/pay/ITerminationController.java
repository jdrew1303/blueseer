package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.TerminationDtos.LumpSumExemptionResultDTO;
import com.blueseer.pay.TerminationDtos.TerminationLumpSumDTO;

/** Controller backing S-50 Termination Lump Sum. */
public interface ITerminationController {

    /** Backs {@code TerminationLumpSumCalculator}/C-10. */
    LumpSumExemptionResultDTO previewLumpSumExemption(TerminationLumpSumDTO draft);

    /**
     * Routes the taxable balance into the employee's current final pay entry
     * (S-18 or S-49, whichever finalises next) as PAYE/USC-chargeable but
     * PRSI-exempt, per C-10 Step 9.
     */
    void applyToFinalPayslip(EmployeeId id, LumpSumExemptionResultDTO result);
}

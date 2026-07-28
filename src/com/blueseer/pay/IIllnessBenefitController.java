package com.blueseer.pay;

import com.blueseer.pay.IllnessBenefitDtos.IllnessBenefitDTO;

/** Controller backing S-57 Illness Benefit Handling (C-22). */
public interface IIllnessBenefitController {

    /**
     * Persists the record. For {@code "EMPLOYER_PAYS_FULL_OR_PARTIAL_SALARY"}
     * and {@code "EMPLOYER_RECEIVES_MANDATE_REIMBURSEMENT"},
     * {@code employerPaidAmountThisPeriod} surfaces in S-18's additions
     * summary as ordinary taxable pay. {@code dspMandateReimbursementAmountOrNull},
     * if present, is saved as a standalone ledger note only - never passed to
     * {@code IPayEntryController}/{@code IPayrollCalculationService}, per C-22 Step 3.
     */
    SaveResult saveIllnessBenefitRecord(IllnessBenefitDTO draft);
}

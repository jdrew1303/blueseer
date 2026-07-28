package com.blueseer.pay;

import com.blueseer.pay.ParentingBenefitDtos.ParentingBenefitDTO;

/** Controller backing S-58 Parenting Benefits (C-18). */
public interface IParentingBenefitController {

    /**
     * Persists the leave record. {@code employerTopUpAmountPerPeriod}, once
     * saved, surfaces automatically in S-18's additions summary for the
     * relevant periods as ordinary taxable pay (C-18 Step 2) - never treated
     * specially. The DSP benefit itself is never added to gross pay (C-18
     * Step 1 is always zero).
     */
    SaveResult saveLeaveRecord(ParentingBenefitDTO draft);
}

package com.blueseer.pay;

import com.blueseer.pay.CycleToWorkDtos.CycleEligibilityDTO;
import com.blueseer.pay.CycleToWorkDtos.CycleToWorkDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

/** Controller backing S-27 Cycle to Work Scheme. */
public interface ICycleToWorkController {

    CycleEligibilityDTO checkEligibility(EmployeeId id);

    /** Validates eligibility (C-11) and, if eligible, persists the arrangement as a recurring deduction line feeding S-08's table. */
    SaveResult saveSacrificeArrangement(CycleToWorkDTO draft);
}

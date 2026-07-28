package com.blueseer.pay;

import com.blueseer.pay.AutoEnrolmentDtos.AeContributionPeriodDTO;
import com.blueseer.pay.AutoEnrolmentDtos.AeEligibilityStatusDTO;
import com.blueseer.pay.AutoEnrolmentDtos.AecsSubmissionResult;
import com.blueseer.pay.AutoEnrolmentDtos.AepnCorrectionDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.util.List;

/** Controller backing S-61 Auto-Enrolment (MyFuture Fund). All four methods are collectively backed by C-14. */
public interface IAutoEnrolmentController {

    AeEligibilityStatusDTO getEligibilityStatus(EmployeeId id);

    /** Persists the opt-out/suspend flag or existing-pension-coverage flag, per the employee-initiated confirmation dialog. */
    SaveResult setParticipationFlags(EmployeeId id, boolean hasExistingPensionCoverage, boolean optedOutOrSuspended);

    List<AeContributionPeriodDTO> getContributionHistory(EmployeeId id);

    /** Backs {@code AecsSubmissionGateway} - an EDI call to NAERSA. */
    AecsSubmissionResult submitAecs(CompanyId id, int periodNumber);

    /** Backs {@code AepnHandler}. */
    void handleAepnCorrection(AepnCorrectionDTO correction);
}

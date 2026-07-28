package com.blueseer.pay;

import com.blueseer.pay.LeaverDtos.OffCycleFinalisationResult;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.time.LocalDate;

/** Controller backing S-49 Leaver (mid pay period). */
public interface ILeaverController {

    PayEntryDTO loadMidPeriodLeaverEntry(EmployeeId id);

    /** Backs {@code OffCycleFinaliser} - generates and persists a final payslip outside the normal period cycle. */
    OffCycleFinalisationResult finaliseMidPeriodLeaver(EmployeeId id, LocalDate leaveDate, PayEntryDTO finalPay);
}

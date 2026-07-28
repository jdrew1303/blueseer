package com.blueseer.pay;

import com.blueseer.pay.LeaverDtos.OffCycleFinalisationResult;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.time.LocalDate;

/** Track A/B/C stub for {@link ILeaverController}, backed by {@link PayrollStubStore}. */
final class InMemoryLeaverController implements ILeaverController {

    private final PayrollStubStore store;
    private final IPayrollCalculationService calcService;

    InMemoryLeaverController(PayrollStubStore store, IPayrollCalculationService calcService) {
        this.store = store;
        this.calcService = calcService;
    }

    @Override
    public PayEntryDTO loadMidPeriodLeaverEntry(EmployeeId id) {
        PayEntryDTO existing = store.draftPayEntries.get(id);
        if (existing != null) {
            return existing;
        }
        return new PayEntryDTO(id, store.currentPeriodNumber, null, null, null, null, null,
                null, null, 0, true, null, null);
    }

    @Override
    public OffCycleFinalisationResult finaliseMidPeriodLeaver(EmployeeId id, LocalDate leaveDate, PayEntryDTO finalPay) {
        PayEntryDTO withLeaveDate = new PayEntryDTO(finalPay.employeeId(), finalPay.periodNumber(),
                finalPay.hourlyRate(), finalPay.standardHours(), finalPay.timeAndAThirdHours(),
                finalPay.timeAndAHalfHours(), finalPay.doubleTimeHours(), finalPay.basicPay(),
                finalPay.holidayPayAmount(), finalPay.additionalWeeksSpread(), true, leaveDate, finalPay.noteOrNull());
        return OffCycleFinaliser.finalise(store, calcService, id, leaveDate, withLeaveDate);
    }
}

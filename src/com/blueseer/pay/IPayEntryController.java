package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.BatchSaveResult;
import com.blueseer.pay.PayProcessingDtos.NetToGrossResultDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryRowDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.DepartmentId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.util.List;

/**
 * Controller backing S-18 Weekly/Monthly/Fortnightly Input, S-19 Quick Edit
 * Entry, and S-20 Net to Gross Payments - one Controller for all three per
 * the roadmap's explicit "shared service, two views" note for S-18/S-19,
 * extended to S-20 since it also reads/writes the same per-period draft.
 */
public interface IPayEntryController {

    /**
     * The period every "no period specified" caller should operate on -
     * screens resolve this fresh every time they're shown rather than
     * caching it, since S-22's finalisation advances it out from under any
     * already-open S-18/S-19/S-20 instance (MainFrame reuses one cached
     * panel per menu item for the life of the session).
     */
    int getCurrentPeriodNumber(CompanyId id);

    PayEntryDTO loadPayEntry(EmployeeId id, int periodNumber);

    /** Routes through {@link GrossPayAssembler}, not duplicated view-side math. */
    BigDecimal previewGrossPay(PayEntryDTO draft);

    SaveResult savePayEntry(PayEntryDTO draft);

    /** Feeds {@code lblHolidaySpreadWarning}, backed by C-17. */
    List<Integer> previewHolidaySpreadLockedWeeks(EmployeeId id, int additionalWeeks);

    List<PayEntryRowDTO> loadQuickEditGrid(CompanyId id, int periodNumber, DepartmentId filterOrNull);

    /** Uses the same {@link GrossPayAssembler} as S-18. */
    BatchSaveResult saveQuickEditBatch(List<PayEntryRowDTO> rows);

    /** The DTO includes the full iteration trail per C-20's audit-trail requirement. */
    NetToGrossResultDTO solveNetToGross(EmployeeId id, int periodNumber, BigDecimal targetNet);

    /**
     * S-25 Directors Fees - a fee is just flat pay for the period, so this
     * composes the existing load/save pair rather than needing its own
     * storage; the PRSI Class S routing it triggers is decided by {@code
     * PayslipEngineChain} from the employee's own director flag, not by
     * anything this method records, so no implementation override is needed.
     */
    default SaveResult saveDirectorFee(EmployeeId id, int periodNumber, BigDecimal feeAmount) {
        PayEntryDTO existing = loadPayEntry(id, periodNumber);
        PayEntryDTO updated = new PayEntryDTO(id, periodNumber, existing.hourlyRate(), existing.standardHours(),
                existing.timeAndAThirdHours(), existing.timeAndAHalfHours(), existing.doubleTimeHours(),
                feeAmount, existing.holidayPayAmount(), existing.additionalWeeksSpread(),
                existing.leaving(), existing.leaveDateOrNull(), existing.noteOrNull());
        return savePayEntry(updated);
    }
}

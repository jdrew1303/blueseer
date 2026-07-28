package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.CumulativesDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.LeaverDtos.OffCycleFinalisationResult;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.PayslipId;
import com.blueseer.pay.PayrollIds.PsrBatchId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * S-49's "generate a final payslip outside the normal pay period" action -
 * mirrors {@link PayPeriodFinaliser}'s per-employee body (persist payslip,
 * roll cumulatives, clear the draft) but for exactly one employee, against
 * the period counter as it stands right now rather than advancing it or
 * touching any other employee's draft.
 */
final class OffCycleFinaliser {

    private OffCycleFinaliser() {
    }

    static OffCycleFinalisationResult finalise(PayrollStubStore store, IPayrollCalculationService calcService,
            EmployeeId id, LocalDate leaveDate, PayEntryDTO finalPay) {
        EmployeeRecordDTO rec = store.employees.get(id);
        if (rec == null) {
            return new OffCycleFinalisationResult(false, null, store.currentPeriodNumber, "Employee not found.");
        }

        int period = store.currentPeriodNumber;
        BigDecimal prsiExempt = store.terminationLumpSumTaxable.remove(id);
        PayslipEngineChain.ChainResult chain = PayslipEngineChain.run(rec, finalPay, period, leaveDate, calcService,
                prsiExempt == null ? BigDecimal.ZERO : prsiExempt, store.employeeFrequencyOverride.get(id), store.appliedRpnData.get(id));

        PayslipId payslipId = new PayslipId(store.nextPayslipId());
        store.payslips.put(payslipId, new PayslipRecordDTO(payslipId, id, period, leaveDate,
                chain.gross(), chain.paye().amount(), chain.prsi().employee().amount(),
                chain.usc().amount(), chain.net(), chain.allSteps()));

        CumulativesDTO cum = rec.cumulatives();
        CumulativesDTO updatedCum = new CumulativesDTO(
                nz(cum == null ? null : cum.priorGrossPay()).add(chain.gross()),
                nz(cum == null ? null : cum.priorTaxPaid()).add(chain.paye().amount()),
                nz(cum == null ? null : cum.priorPrsiPaid()).add(chain.prsi().employee().amount()),
                nz(cum == null ? null : cum.priorUscPaid()).add(chain.usc().amount()));
        store.employees.put(id, new EmployeeRecordDTO(id, rec.personalDetails(), rec.revenueDetails(),
                rec.additions(), rec.deductions(), updatedCum, rec.hrDetails(), rec.csoDetails()));

        store.leaveDates.put(id, leaveDate);
        store.draftPayEntries.remove(id);

        PsrBatchId psrId = new PsrBatchId(store.nextPsrBatchId());
        store.psrBatches.put(psrId, new PayrollStubStore.PsrBatchRecord(psrId, leaveDate, List.of(payslipId), "Due", null));

        return new OffCycleFinalisationResult(true, payslipId, period, "Final payslip generated.");
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

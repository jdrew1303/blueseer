package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.CumulativesDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayProcessingDtos.FinalisationResult;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.PayslipId;
import com.blueseer.pay.PayrollIds.PsrBatchId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * S-22's "Update Payslips" action: locks the period being processed, runs
 * {@link PayslipEngineChain} for every employee with a draft entry, persists
 * a {@link PayslipRecordDTO} per employee (so S-23 has something to read
 * back rather than recompute), rolls each employee's cumulatives forward,
 * clears the drafts, advances the shared period counter, and auto-generates
 * the pending PSR batch (§3.6 S-31) in "Due" status.
 */
final class PayPeriodFinaliser {

    private PayPeriodFinaliser() {
    }

    static FinalisationResult finalise(PayrollStubStore store, IPayrollCalculationService calcService,
            LocalDate payDate, String weeklyReferenceNoteOrNull) {
        int period = store.currentPeriodNumber;
        int created = 0;
        List<PayslipId> payslipIdsThisRun = new ArrayList<>();

        for (EmployeeRecordDTO rec : new ArrayList<>(store.employees.values())) {
            if (store.leaveDates.containsKey(rec.id())) {
                continue;
            }
            PayEntryDTO draft = store.draftPayEntries.get(rec.id());
            if (draft == null) {
                continue;
            }

            java.math.BigDecimal prsiExempt = store.terminationLumpSumTaxable.remove(rec.id());
            PayslipEngineChain.ChainResult chain = PayslipEngineChain.run(rec, draft, period, payDate, calcService,
                    prsiExempt == null ? java.math.BigDecimal.ZERO : prsiExempt, store.employeeFrequencyOverride.get(rec.id()),
                    store.appliedRpnData.get(rec.id()));

            PayslipId id = new PayslipId(store.nextPayslipId());
            store.payslips.put(id, new PayslipRecordDTO(id, rec.id(), period, payDate,
                    chain.gross(), chain.paye().amount(), chain.prsi().employee().amount(),
                    chain.usc().amount(), chain.net(), chain.allSteps()));
            payslipIdsThisRun.add(id);

            CumulativesDTO cum = rec.cumulatives();
            CumulativesDTO updatedCum = new CumulativesDTO(
                    nz(cum == null ? null : cum.priorGrossPay()).add(chain.gross()),
                    nz(cum == null ? null : cum.priorTaxPaid()).add(chain.paye().amount()),
                    nz(cum == null ? null : cum.priorPrsiPaid()).add(chain.prsi().employee().amount()),
                    nz(cum == null ? null : cum.priorUscPaid()).add(chain.usc().amount()));
            store.employees.put(rec.id(), new EmployeeRecordDTO(rec.id(), rec.personalDetails(), rec.revenueDetails(),
                    rec.additions(), rec.deductions(), updatedCum, rec.hrDetails(), rec.csoDetails()));

            if (draft.leaving() && draft.leaveDateOrNull() != null) {
                store.leaveDates.put(rec.id(), draft.leaveDateOrNull());
            }
            created++;
        }

        store.draftPayEntries.clear();
        store.lastPeriodUpdatedLabel = "Period " + period + " (" + payDate + ")";
        store.currentPeriodNumber = period + 1;

        if (!payslipIdsThisRun.isEmpty()) {
            PsrBatchId psrId = new PsrBatchId(store.nextPsrBatchId());
            store.psrBatches.put(psrId, new PayrollStubStore.PsrBatchRecord(psrId, payDate, payslipIdsThisRun, "Due", null));
        }

        String message = created == 0
                ? "No pay entries were recorded for this period - nothing to finalise."
                : "Files successfully prepared.";
        return new FinalisationResult(true, created, message);
    }

    private static java.math.BigDecimal nz(java.math.BigDecimal value) {
        return value == null ? java.math.BigDecimal.ZERO : value;
    }
}

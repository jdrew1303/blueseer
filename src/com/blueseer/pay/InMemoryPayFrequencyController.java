package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayFrequencyDtos.FrequencyChangePreviewDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Track A/B/C stub for {@link IPayFrequencyController}, backed by {@link
 * PayrollStubStore}. This module has no persisted per-employee frequency
 * field or per-employee annual credit/cut-off tracking (RPN retrieval,
 * S-14/S-15, is a known gap elsewhere in this build) - the preview below is
 * therefore limited to what genuinely exists: {@link PayProcessingDtos
 * .PayEntryDTO}'s shared period counter and each employee's {@code
 * CumulativesDTO} prior-to-date figures, not a full RPN-driven credit/
 * cut-off reallocation.
 */
final class InMemoryPayFrequencyController implements IPayFrequencyController {

    private final PayrollStubStore store;

    InMemoryPayFrequencyController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public PayFrequency getCurrentFrequency(EmployeeId id) {
        PayFrequency override = store.employeeFrequencyOverride.get(id);
        if (override != null) {
            return override;
        }
        EmployeeRecordDTO rec = store.employees.get(id);
        return rec == null ? PayFrequency.MONTHLY : GrossPayAssembler.inferFrequency(rec.personalDetails());
    }

    @Override
    public FrequencyChangePreviewDTO previewFrequencyChange(EmployeeId id, PayFrequency newFrequency) {
        PayFrequency oldFrequency = getCurrentFrequency(id);
        EmployeeRecordDTO rec = store.employees.get(id);
        BigDecimal cumulativeGross = rec == null ? BigDecimal.ZERO : rec.cumulatives().priorGrossPay();
        BigDecimal cumulativeTax = rec == null ? BigDecimal.ZERO : rec.cumulatives().priorTaxPaid();

        int periodsCompleted = Math.max(0, store.currentPeriodNumber - 1);
        int equivalentPeriodsUnderNew = (int) Math.round(
                periodsCompleted * (double) newFrequency.periodsPerYear() / oldFrequency.periodsPerYear());
        int remainingUnderNew = Math.max(0, newFrequency.periodsPerYear() - equivalentPeriodsUnderNew);

        String explanation = String.format(
                "%d period(s) completed under %s. Cumulative gross pay to date (%s) and tax paid to date (%s) "
                        + "carry forward unchanged - they are not recalculated by this change. Under %s, "
                        + "approximately %d period(s) remain in the tax year; each subsequent period's PAYE/USC "
                        + "will use the %s-scaled RPN allowance from that point on, per cumulative-basis mechanics.",
                periodsCompleted, oldFrequency, cumulativeGross.toPlainString(), cumulativeTax.toPlainString(),
                newFrequency, remainingUnderNew, newFrequency);

        return new FrequencyChangePreviewDTO(oldFrequency, newFrequency, periodsCompleted, cumulativeGross,
                cumulativeTax, remainingUnderNew, explanation);
    }

    @Override
    public SaveResult applyFrequencyChange(EmployeeId id, PayFrequency newFrequency) {
        if (!store.employees.containsKey(id)) {
            return new SaveResult(false, "Employee not found");
        }
        store.employeeFrequencyOverride.put(id, newFrequency);
        return new SaveResult(true, "Pay frequency changed to " + newFrequency + ".");
    }
}

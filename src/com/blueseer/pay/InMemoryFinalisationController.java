package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayProcessingDtos.AnomalyDTO;
import com.blueseer.pay.PayProcessingDtos.CalendarPeriodDTO;
import com.blueseer.pay.PayProcessingDtos.FinalisationResult;
import com.blueseer.pay.PayProcessingDtos.FinalisationSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.time.LocalDate;
import java.util.List;

/** Track A stub for {@link IFinalisationController}, backed by {@link PayrollStubStore}. */
final class InMemoryFinalisationController implements IFinalisationController {

    private static final int TAX_YEAR = 2026;

    private final PayrollStubStore store;
    private final IPayrollCalculationService calcService;

    InMemoryFinalisationController(PayrollStubStore store, IPayrollCalculationService calcService) {
        this.store = store;
        this.calcService = calcService;
    }

    @Override
    public FinalisationSummaryDTO getFinalisationSummary(CompanyId id) {
        List<CalendarPeriodDTO> calendar = PayrollCalendarService.calendar(PayFrequency.WEEKLY, TAX_YEAR, store.currentPeriodNumber);
        LocalDate suggestedPayDate = store.currentPeriodNumber <= calendar.size()
                ? calendar.get(store.currentPeriodNumber - 1).to()
                : LocalDate.now();
        boolean weeklyInUse = false;
        for (EmployeeRecordDTO rec : store.employees.values()) {
            if (!store.leaveDates.containsKey(rec.id())
                    && GrossPayAssembler.inferFrequency(rec.personalDetails(), store.employeeFrequencyOverride.get(rec.id())) == PayFrequency.WEEKLY) {
                weeklyInUse = true;
                break;
            }
        }
        return new FinalisationSummaryDTO(store.lastRpnImportDate, store.lastPeriodUpdatedLabel,
                store.currentPeriodNumber, suggestedPayDate, weeklyInUse);
    }

    @Override
    public List<AnomalyDTO> detectAnomalies(CompanyId id, int periodNumber) {
        return ComputationalAnomalyDetector.detect(store, periodNumber);
    }

    @Override
    public FinalisationResult finalisePayPeriod(CompanyId id, LocalDate payDate, String weeklyReferenceNoteOrNull) {
        return PayPeriodFinaliser.finalise(store, calcService, payDate, weeklyReferenceNoteOrNull);
    }
}

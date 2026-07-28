package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayProcessingDtos.CalendarPeriodDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Track A stub for {@link IPayrollCalendarController}, backed by {@link PayrollStubStore}. */
final class InMemoryPayrollCalendarController implements IPayrollCalendarController {

    private static final int TAX_YEAR = 2026;

    private final PayrollStubStore store;

    InMemoryPayrollCalendarController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public List<CalendarPeriodDTO> getCalendar(CompanyId id, PayFrequency freq, int taxYear) {
        return PayrollCalendarService.calendar(freq, taxYear, store.currentPeriodNumber);
    }

    @Override
    public List<PayFrequency> getFrequenciesInUse(CompanyId id) {
        Set<PayFrequency> freqs = new LinkedHashSet<>();
        for (EmployeeRecordDTO rec : store.employees.values()) {
            if (store.leaveDates.containsKey(rec.id())) {
                continue;
            }
            freqs.add(GrossPayAssembler.inferFrequency(rec.personalDetails(), store.employeeFrequencyOverride.get(rec.id())));
        }
        if (freqs.isEmpty()) {
            freqs.add(PayFrequency.WEEKLY);
        }
        return new ArrayList<>(freqs);
    }

    @Override
    public boolean isWeek53Period(CompanyId id, int periodNumber) {
        List<CalendarPeriodDTO> weekly = PayrollCalendarService.calendar(PayFrequency.WEEKLY, TAX_YEAR, periodNumber);
        if (periodNumber < 1 || periodNumber > weekly.size()) {
            return false;
        }
        CalendarPeriodDTO period = weekly.get(periodNumber - 1);
        long days = java.time.temporal.ChronoUnit.DAYS.between(period.from(), period.to()) + 1;
        return days < 7;
    }
}

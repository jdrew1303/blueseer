package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.CalendarPeriodDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.util.List;

/** Controller backing S-17 Payroll Calendar. */
public interface IPayrollCalendarController {

    List<CalendarPeriodDTO> getCalendar(CompanyId id, PayFrequency freq, int taxYear);

    /** Drives S-17's {@code cbPayFrequency}, which the spec says is "only shown if the company runs more than one frequency." */
    List<PayFrequency> getFrequenciesInUse(CompanyId id);

    /** S-30: backs the {@code Week53Banner} embedded in S-18/S-19/S-22, per C-21. */
    boolean isWeek53Period(CompanyId id, int periodNumber);
}

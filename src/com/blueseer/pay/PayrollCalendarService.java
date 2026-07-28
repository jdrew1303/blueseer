package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.CalendarPeriodDTO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * C-19 statutory week/period boundaries for one tax year, per pay
 * frequency. Weekly/Fortnightly use Revenue's own fixed-calendar-block
 * convention (blocks counted from 1 January, not from the first Monday),
 * which is what naturally produces a short final period (roadmap S-30
 * "Week 53 handling") whenever the tax year's day count doesn't divide
 * evenly into 7- or 14-day blocks - this class is the thing that "produces"
 * that period, per S-30's own cross-reference. Monthly uses literal
 * calendar months.
 */
final class PayrollCalendarService {

    private PayrollCalendarService() {
    }

    static List<CalendarPeriodDTO> calendar(PayFrequency frequency, int taxYear, int currentPeriodNumber) {
        LocalDate jan1 = LocalDate.of(taxYear, 1, 1);
        LocalDate dec31 = LocalDate.of(taxYear, 12, 31);
        return switch (frequency) {
            case WEEKLY -> fixedBlocks(jan1, dec31, 7, currentPeriodNumber);
            case FORTNIGHTLY -> fixedBlocks(jan1, dec31, 14, currentPeriodNumber);
            case MONTHLY -> monthlyBlocks(taxYear, currentPeriodNumber);
        };
    }

    private static List<CalendarPeriodDTO> fixedBlocks(LocalDate jan1, LocalDate dec31, int blockDays, int currentPeriodNumber) {
        List<CalendarPeriodDTO> periods = new ArrayList<>();
        int periodNumber = 1;
        LocalDate from = jan1;
        while (!from.isAfter(dec31)) {
            LocalDate to = from.plusDays(blockDays - 1);
            if (to.isAfter(dec31)) {
                to = dec31;
            }
            periods.add(new CalendarPeriodDTO(periodNumber, from, to, periodNumber == currentPeriodNumber));
            from = to.plusDays(1);
            periodNumber++;
        }
        return periods;
    }

    private static List<CalendarPeriodDTO> monthlyBlocks(int taxYear, int currentPeriodNumber) {
        List<CalendarPeriodDTO> periods = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate from = LocalDate.of(taxYear, month, 1);
            LocalDate to = from.withDayOfMonth(from.lengthOfMonth());
            periods.add(new CalendarPeriodDTO(month, from, to, month == currentPeriodNumber));
        }
        return periods;
    }
}

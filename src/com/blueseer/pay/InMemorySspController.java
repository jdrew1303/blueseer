package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.LineItemId;
import com.blueseer.pay.SspDtos.SspClaimDTO;
import com.blueseer.pay.SspDtos.SspPreviewDTO;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Track A/B/C stub for {@link ISspController}, backed by {@link
 * PayrollStubStore}. S-56's own component tree has no field for
 * {@code payCalculationCategory}/hourly-rate/scheduled-hours - this
 * controller derives category and the day-rate base from whichever of the
 * employee's own hourly-rate/fixed-pay fields is populated (the same
 * mutually-exclusive pair {@link GrossPayAssembler#inferFrequency} already
 * uses), assuming a standard 8-hour working day for hourly employees and a
 * 260-working-day year (52 weeks &times; 5 days) to convert a salaried
 * employee's monthly pay to a daily figure - an honest stub approximation,
 * not a real "normal daily hours last worked" lookup, since Track A doesn't
 * retain per-day worked-hours history.
 */
final class InMemorySspController implements ISspController {

    private static final BigDecimal STANDARD_DAILY_HOURS = new BigDecimal("8");
    private static final BigDecimal WORKING_DAYS_PER_YEAR = new BigDecimal("260");

    private final PayrollStubStore store;
    private final IAdditionDeductionService additionDeductionService;
    private final ITaxYearRules.SslRates rates = new Paye2026Rules().sslRates();

    InMemorySspController(PayrollStubStore store, IAdditionDeductionService additionDeductionService) {
        this.store = store;
        this.additionDeductionService = additionDeductionService;
    }

    @Override
    public SspPreviewDTO previewSspEntitlement(SspClaimDTO draft) {
        EmployeeRecordDTO rec = store.employees.get(draft.employeeId());
        if (rec == null) {
            return new SspPreviewDTO(false, 0, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        LocalDate startDate = rec.revenueDetails() == null ? null : rec.revenueDetails().startDate();
        LocalDate illnessStart = draft.illnessStartDate() == null ? LocalDate.now() : draft.illnessStartDate();
        boolean qualifyingServiceMet = startDate != null && !illnessStart.isBefore(startDate.plusWeeks(13));

        BigDecimal hourlyRate = rec.personalDetails().hourlyRate();
        String category;
        BigDecimal normalDailyHoursLastWorkedPay = BigDecimal.ZERO;
        BigDecimal fixedHourlyRate = BigDecimal.ZERO;
        BigDecimal scheduledHours = BigDecimal.ZERO;
        if (hourlyRate != null) {
            category = "FIXED_RATE_VARIABLE_HOURS";
            fixedHourlyRate = hourlyRate;
            scheduledHours = STANDARD_DAILY_HOURS;
        } else {
            category = "FIXED_PERIOD";
            BigDecimal monthlyPay = rec.personalDetails().fixedPay() == null ? BigDecimal.ZERO : rec.personalDetails().fixedPay();
            normalDailyHoursLastWorkedPay = monthlyPay.multiply(BigDecimal.valueOf(12))
                    .divide(WORKING_DAYS_PER_YEAR, 2, RoundingMode.HALF_UP);
        }

        int daysUsed = store.sslDaysUsedThisYear.getOrDefault(draft.employeeId(), 0);
        int daysClaimed = Math.max(0, draft.daysClaimedThisInstance());

        SspEntitlementEngine.SspContext ctx = new SspEntitlementEngine.SspContext(
                category, normalDailyHoursLastWorkedPay, fixedHourlyRate, scheduledHours, BigDecimal.ZERO,
                daysUsed, qualifyingServiceMet, draft.hasMedicalCertificate());
        SspEntitlementEngine.SspResult perDay = SspEntitlementEngine.calculate(ctx, rates);

        int payableDays = Math.min(daysClaimed, perDay.sslDaysRemaining());
        BigDecimal totalPay = perDay.sslPayThisDay().multiply(BigDecimal.valueOf(Math.max(0, payableDays)));

        return new SspPreviewDTO(qualifyingServiceMet, perDay.sslDaysRemaining(), perDay.dailyRateBase(), totalPay, perDay.steps());
    }

    @Override
    public SaveResult recordSickLeave(SspClaimDTO draft) {
        EmployeeRecordDTO rec = store.employees.get(draft.employeeId());
        if (rec == null) {
            return new SaveResult(false, "Employee not found");
        }
        if (!draft.hasMedicalCertificate()) {
            return new SaveResult(false, "A medical certificate is required from day 1 of every instance.");
        }
        SspPreviewDTO preview = previewSspEntitlement(draft);
        if (!preview.qualifyingServiceMet()) {
            return new SaveResult(false, "Employee has not completed 13 continuous weeks' qualifying service.");
        }
        int daysUsed = store.sslDaysUsedThisYear.getOrDefault(draft.employeeId(), 0);
        int payableDays = Math.min(Math.max(0, draft.daysClaimedThisInstance()), preview.daysRemainingThisYear());
        store.sslDaysUsedThisYear.put(draft.employeeId(), daysUsed + payableDays);

        if (preview.sslPayThisInstance().compareTo(BigDecimal.ZERO) > 0) {
            List<AdditionLineDTO> updated = new ArrayList<>(rec.additions());
            updated.add(new AdditionLineDTO(new LineItemId(System.currentTimeMillis()), "Statutory Sick Pay", true,
                    preview.sslPayThisInstance(), null));
            return additionDeductionService.saveAdditions(draft.employeeId(), updated);
        }
        return new SaveResult(true, "Sick leave recorded (no SSP days remaining this year - DSP Illness Benefit applies instead).");
    }
}

package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.TerminationDtos.LumpSumExemptionResultDTO;
import com.blueseer.pay.TerminationDtos.TerminationLumpSumDTO;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;

/**
 * Track A/B/C stub for {@link ITerminationController}, backed by {@link
 * PayrollStubStore}. Two values C-10 needs have no real source in this
 * module and are honestly defaulted rather than fabricated: {@code
 * eligibleForIncreasedExemption} (TDM's 10-year-lookback condition - no
 * relief history is tracked, so every employee is treated as never having
 * claimed increased relief before) and {@code priorLifetimeReliefClaimed}
 * (assumed zero for the same reason). S-50's own component tree in the
 * screen spec doesn't expose either as an input field, so there is nowhere
 * for a user to override them either.
 */
final class InMemoryTerminationController implements ITerminationController {

    private final PayrollStubStore store;
    private final ITaxYearRules.TerminationLumpSumRates rates = new Paye2026Rules().terminationLumpSumRates();

    InMemoryTerminationController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public LumpSumExemptionResultDTO previewLumpSumExemption(TerminationLumpSumDTO draft) {
        EmployeeRecordDTO rec = store.employees.get(draft.employeeId());
        LocalDate leaveDate = store.leaveDates.getOrDefault(draft.employeeId(), LocalDate.now());
        LocalDate startDate = rec == null || rec.revenueDetails() == null ? leaveDate : rec.revenueDetails().startDate();
        int completeYears = startDate == null ? 0 : Math.max(0, Period.between(startDate, leaveDate).getYears());

        BigDecimal averageAnnual36MonthPay = averageAnnualRemunerationLast36Months(draft.employeeId(), leaveDate);
        boolean eligibleForIncreasedExemption = true;
        BigDecimal priorLifetimeReliefClaimed = BigDecimal.ZERO;

        TerminationLumpSumCalculator.TerminationLumpSumContext ctx = new TerminationLumpSumCalculator.TerminationLumpSumContext(
                nz(draft.exGratiaLumpSumAmount()), completeYears, averageAnnual36MonthPay,
                eligibleForIncreasedExemption, nz(draft.relevantCapitalSum()), priorLifetimeReliefClaimed);
        TerminationLumpSumCalculator.TerminationLumpSumResult result = TerminationLumpSumCalculator.calculate(ctx, rates);

        return new LumpSumExemptionResultDTO(
                completeYears, averageAnnual36MonthPay, eligibleForIncreasedExemption,
                result.standardBasicExemption(), result.increasedExemption(), result.totalBasicExemption(),
                result.scsb(), result.finalExemptionApplied(), result.taxableAmount(), result.steps());
    }

    @Override
    public void applyToFinalPayslip(EmployeeId id, LumpSumExemptionResultDTO result) {
        store.terminationLumpSumTaxable.put(id, result.taxableAmount());
    }

    /**
     * TDM §3.5's real 36-month lookback needs pay history predating this
     * system's own tracking, which doesn't exist for any Track A demo
     * employee - this sums whatever finalised payslips actually exist for
     * the employee within the window and divides by 3, an honest
     * under-representation for anyone with less than 3 years of in-system
     * history rather than a fabricated full-history average.
     */
    private BigDecimal averageAnnualRemunerationLast36Months(EmployeeId id, LocalDate leaveDate) {
        LocalDate windowStart = leaveDate.minusMonths(36);
        BigDecimal total = BigDecimal.ZERO;
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.employeeId().equals(id) && !p.payDate().isBefore(windowStart) && !p.payDate().isAfter(leaveDate)) {
                total = total.add(p.gross());
            }
        }
        return total.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

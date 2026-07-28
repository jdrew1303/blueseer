package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.CumulativesDTO;
import com.blueseer.pay.EmployeeDtos.DeductionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.PersonalDetailsDTO;
import com.blueseer.pay.PayProcessingDtos.CalendarPeriodDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Runs the C-01/C-02/C-03 engine chain for one employee/period, shared by
 * S-18's live preview, S-21's Payroll Preview, S-22's Finalisation, S-23's
 * Payslip Workings, and C-20's Net-to-Gross solver - one place that decides
 * how a {@link PayEntryDTO} plus an employee's standing additions/deductions
 * become the figures those five screens all show, so they can never disagree
 * with each other.
 *
 * <p>Resolves {@link PayeCalculator.CalculationBasis#EMERGENCY} (and its USC
 * equivalent) for any employee with no applied RPN data yet ({@code
 * rpnDataOrNull == null} - see {@code PayrollStubStore.appliedRpnData}),
 * matching {@link PayeCalculator}'s own documented behaviour for "a
 * brand-new employee with no RPN yet" rather than inventing a
 * Cumulative-basis credit. Once S-14/S-15 (roadmap §3.3) have applied real
 * RPN data for an employee, this switches to {@code CUMULATIVE} using the
 * RPN-supplied credit/cut-off. There is still no real Revenue test-
 * environment connection behind that RPN data (see {@code
 * InMemoryRpnGateway}'s own class comment) - only the basis-selection wiring
 * itself is real.
 *
 * <p>This also processes every employee against one shared period counter
 * regardless of their individual pay frequency ({@link
 * GrossPayAssembler#inferFrequency}) - a real payroll run partitions Weekly
 * and Monthly employees into separate concurrent runs with independent
 * period counters, but that partitioning is Track E wiring left for when
 * S-17's calendar is backed by a real per-frequency run record rather than
 * this stub's single {@code PayrollStubStore.currentPeriodNumber}.
 */
final class PayslipEngineChain {

    private static final int TAX_YEAR = 2026;

    private PayslipEngineChain() {
    }

    record ChainResult(
            BigDecimal gross,
            EngineResult paye,
            PrsiCalculator.PrsiResult prsi,
            EngineResult usc,
            BigDecimal net,
            List<CalculationStep> allSteps) {
    }

    static ChainResult run(EmployeeRecordDTO rec, PayEntryDTO draft, int periodNumber, LocalDate payDate,
            IPayrollCalculationService calcService, PayFrequency frequencyOverrideOrNull, RpnDtos.RpnDataDTO rpnDataOrNull) {
        return run(rec, draft, periodNumber, payDate, calcService, BigDecimal.ZERO, frequencyOverrideOrNull, rpnDataOrNull);
    }

    /**
     * @param prsiExemptTaxableAddition C-10 Step 9: a termination lump sum's
     *                                  taxable balance is charged to PAYE/USC
     *                                  but "not regarded as reckonable income
     *                                  for the purpose of PRSI" - added to the
     *                                  PAYE/USC gross below but deliberately
     *                                  excluded from {@code payeGross} where
     *                                  it feeds the PRSI calculator.
     * @param frequencyOverrideOrNull   S-64's explicit per-employee frequency
     *                                  change (see {@code PayrollStubStore
     *                                  .employeeFrequencyOverride}), when the
     *                                  caller has resolved one - overrides
     *                                  {@link GrossPayAssembler#inferFrequency}'s
     *                                  hourly-rate/fixed-pay heuristic.
     * @param rpnDataOrNull             S-14/S-15's applied tax credit/cut-off
     *                                  (see {@code PayrollStubStore
     *                                  .appliedRpnData}) - once present,
     *                                  switches this employee from the
     *                                  brand-new-employee EMERGENCY default
     *                                  to CUMULATIVE basis using these
     *                                  RPN-supplied figures, per S-06's own
     *                                  framing. A Week 53 period still forces
     *                                  WEEK1 on top of this, per C-21.
     */
    static ChainResult run(EmployeeRecordDTO rec, PayEntryDTO draft, int periodNumber, LocalDate payDate,
            IPayrollCalculationService calcService, BigDecimal prsiExemptTaxableAddition, PayFrequency frequencyOverrideOrNull,
            RpnDtos.RpnDataDTO rpnDataOrNull) {
        PersonalDetailsDTO personal = rec.personalDetails();
        PayFrequency frequency = GrossPayAssembler.inferFrequency(personal, frequencyOverrideOrNull);

        BigDecimal baseGross = GrossPayAssembler.computeGrossPay(draft);
        BigDecimal taxableAdditions = sumTaxableAdditions(rec.additions());
        BigDecimal preTaxDeductions = sumPreTaxDeductions(rec.deductions());
        BigDecimal prsiExempt = nz(prsiExemptTaxableAddition);

        BigDecimal prsiBaseGross = baseGross.add(taxableAdditions).subtract(preTaxDeductions).max(BigDecimal.ZERO);
        BigDecimal payeGross = prsiBaseGross.add(prsiExempt);
        BigDecimal uscableGross = baseGross.add(taxableAdditions).add(prsiExempt).max(BigDecimal.ZERO);

        CumulativesDTO cum = rec.cumulatives();
        BigDecimal cumGrossPrior = nz(cum == null ? null : cum.priorGrossPay());
        BigDecimal cumTaxPrior = nz(cum == null ? null : cum.priorTaxPaid());
        BigDecimal cumUscPrior = nz(cum == null ? null : cum.priorUscPaid());

        // S-30/C-21: a Week 53 period is required by Revenue guidance (TDM
        // Part 42-04-07) to run on a plain Week 1 basis using the latest RPN
        // - overriding whatever basis every other period uses (Emergency, in
        // this Track A stub, per the class comment above).
        boolean isWeek53 = isShortFinalPeriod(frequency, periodNumber);

        boolean hasPps = personal.ppsNumber() != null && !personal.ppsNumber().isBlank();
        // S-14/S-15: once an RPN has been retrieved and applied, the
        // employee moves off the brand-new-employee EMERGENCY default onto
        // CUMULATIVE, using the RPN-supplied credit/cut-off - Week 53 still
        // forces WEEK1 on top of either, per C-21.
        PayeCalculator.CalculationBasis payeBasis;
        BigDecimal annualTaxCredit;
        BigDecimal annualCutOffPoint;
        if (isWeek53) {
            payeBasis = PayeCalculator.CalculationBasis.WEEK1;
            annualTaxCredit = BigDecimal.ZERO;
            annualCutOffPoint = BigDecimal.ZERO;
        } else if (rpnDataOrNull != null) {
            payeBasis = PayeCalculator.CalculationBasis.CUMULATIVE;
            annualTaxCredit = nz(rpnDataOrNull.annualTaxCredit());
            annualCutOffPoint = nz(rpnDataOrNull.annualCutOffPoint());
        } else {
            payeBasis = PayeCalculator.CalculationBasis.EMERGENCY;
            annualTaxCredit = BigDecimal.ZERO;
            annualCutOffPoint = BigDecimal.ZERO;
        }
        PayeCalculator.PayeContext payeCtx = new PayeCalculator.PayeContext(
                payeBasis, payeGross, frequency.periodsPerYear(), periodNumber,
                cumGrossPrior, cumTaxPrior, annualTaxCredit, annualCutOffPoint, hasPps, periodNumber);
        EngineResult paye = calcService.calculatePaye(TAX_YEAR, frequency, payeCtx);

        // C-13: proprietary directors route through Class S (flat rate, no
        // employer contribution) instead of Class A - see ClassSPrsiCalculator's
        // class comment for why this reuses the "director" flag rather than a
        // dedicated proprietary-director field the employee record doesn't have.
        PrsiCalculator.PrsiResult prsi;
        if (personal.director()) {
            ClassSPrsiCalculator.ClassSResult classS = ClassSPrsiCalculator.calculate(prsiBaseGross);
            prsi = new PrsiCalculator.PrsiResult(classS.employee(), classS.employer());
        } else {
            PrsiCalculator.PrsiContext prsiCtx = new PrsiCalculator.PrsiContext(prsiBaseGross);
            prsi = calcService.calculatePrsi(TAX_YEAR, payDate, frequency, prsiCtx);
        }

        UscCalculator.CalculationBasis uscBasis = isWeek53 ? UscCalculator.CalculationBasis.WEEK1
                : (rpnDataOrNull != null ? UscCalculator.CalculationBasis.CUMULATIVE : UscCalculator.CalculationBasis.EMERGENCY);
        UscCalculator.UscContext uscCtx = new UscCalculator.UscContext(
                uscBasis, uscableGross, frequency.periodsPerYear(), periodNumber,
                cumUscPrior, cumUscPrior, false, false);
        EngineResult usc = calcService.calculateUsc(TAX_YEAR, uscCtx);

        BigDecimal net = baseGross.add(taxableAdditions).add(prsiExempt)
                .subtract(preTaxDeductions)
                .subtract(paye.amount())
                .subtract(prsi.employee().amount())
                .subtract(usc.amount())
                .setScale(2, RoundingMode.HALF_UP);

        List<CalculationStep> allSteps = new ArrayList<>();
        allSteps.addAll(paye.steps());
        allSteps.addAll(prsi.employee().steps());
        allSteps.addAll(usc.steps());

        return new ChainResult(baseGross.add(taxableAdditions).add(prsiExempt), paye, prsi, usc, net, allSteps);
    }

    private static BigDecimal sumTaxableAdditions(List<AdditionLineDTO> additions) {
        BigDecimal total = BigDecimal.ZERO;
        if (additions != null) {
            for (AdditionLineDTO a : additions) {
                if (a.taxable() && a.amount() != null) {
                    total = total.add(a.amount());
                }
            }
        }
        return total;
    }

    private static BigDecimal sumPreTaxDeductions(List<DeductionLineDTO> deductions) {
        BigDecimal total = BigDecimal.ZERO;
        if (deductions != null) {
            for (DeductionLineDTO d : deductions) {
                if (d.preTax() && d.amount() != null) {
                    total = total.add(d.amount());
                }
            }
        }
        return total;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /**
     * True for a Weekly/Fortnightly period shorter than its normal block
     * (roadmap's "Week 53" - {@link PayrollCalendarService}'s class comment
     * explains why this is what naturally produces one). Monthly has no
     * equivalent concept, since calendar months are never "short" the way a
     * fixed 7/14-day block can be.
     */
    private static boolean isShortFinalPeriod(PayFrequency frequency, int periodNumber) {
        if (frequency == PayFrequency.MONTHLY) {
            return false;
        }
        List<CalendarPeriodDTO> calendar = PayrollCalendarService.calendar(frequency, TAX_YEAR, periodNumber);
        if (periodNumber < 1 || periodNumber > calendar.size()) {
            return false;
        }
        CalendarPeriodDTO period = calendar.get(periodNumber - 1);
        long days = ChronoUnit.DAYS.between(period.from(), period.to()) + 1;
        int blockDays = frequency == PayFrequency.WEEKLY ? 7 : 14;
        return days < blockDays;
    }
}

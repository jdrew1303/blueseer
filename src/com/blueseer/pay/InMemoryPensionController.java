package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.DeductionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.LineItemId;
import com.blueseer.pay.PensionDtos.CwpsPreviewDTO;
import com.blueseer.pay.PensionDtos.NeciPreviewDTO;
import com.blueseer.pay.PensionDtos.PensionDeductionDTO;
import com.blueseer.pay.PensionDtos.PensionReliefPreviewDTO;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Track A/B/C stub for {@link IPensionController}, backed by {@link
 * PayrollStubStore}. {@code netRelevantEarningsOrRemuneration} (C-15) and
 * {@code pensionableEarningsThisPeriod} (C-24) are derived from whichever of
 * the employee's own fixed-pay/hourly-rate fields is populated - the same
 * mutually-exclusive pair {@link GrossPayAssembler#inferFrequency} already
 * uses - since no period pay entry is threaded through this screen.
 */
final class InMemoryPensionController implements IPensionController {

    private static final BigDecimal NOMINAL_WEEKLY_HOURS = new BigDecimal("40");

    private final PayrollStubStore store;
    private final IAdditionDeductionService additionDeductionService;
    private final ITaxYearRules rules = new Paye2026Rules();

    InMemoryPensionController(PayrollStubStore store, IAdditionDeductionService additionDeductionService) {
        this.store = store;
        this.additionDeductionService = additionDeductionService;
    }

    @Override
    public PensionReliefPreviewDTO previewPensionRelief(PensionDeductionDTO draft) {
        EmployeeRecordDTO rec = store.employees.get(draft.employeeId());
        if (rec == null) {
            return new PensionReliefPreviewDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, List.of());
        }
        int age = employeeAge(rec);
        BigDecimal periodEarnings = periodEarnings(rec);
        BigDecimal proposed = draft.percentageMode()
                ? periodEarnings.multiply(nz(draft.contributionValue()))
                : nz(draft.contributionValue());
        BigDecimal cumulative = store.pensionCumulativeReliefYtd.getOrDefault(draft.employeeId(), BigDecimal.ZERO);

        StandardPensionCalculator.StandardPensionContext ctx = new StandardPensionCalculator.StandardPensionContext(
                age, periodEarnings, proposed, cumulative);
        StandardPensionCalculator.StandardPensionResult result = StandardPensionCalculator.calculate(ctx, rules.standardPensionRates());

        return new PensionReliefPreviewDTO(result.ageRelatedPercentageLimit(), result.remainingReliefHeadroomForYear(),
                result.taxRelievablePortionThisPeriod(), result.nonRelievableExcess(), result.steps());
    }

    @Override
    public CwpsPreviewDTO previewCwpsContribution(EmployeeId id) {
        return previewCwpsContribution(id, false, false);
    }

    private CwpsPreviewDTO previewCwpsContribution(EmployeeId id, boolean includeHealthTrust, boolean includeBenevolentFund) {
        ITaxYearRules.CwpsRates rates = rules.cwpsRates();
        CwpsCalculator.CwpsResult result = CwpsCalculator.calculate(
                new CwpsCalculator.CwpsContext(includeHealthTrust, includeBenevolentFund), rates);
        return new CwpsPreviewDTO(true,
                rates.memberPensionWeekly(), rates.memberDeathInServiceWeekly(), rates.memberSickPayWeekly(),
                rates.employerPensionWeekly(), rates.employerDeathInServiceWeekly(), rates.employerSickPayWeekly(),
                rates.memberHealthTrustWeekly(), rates.memberBenevolentFundWeekly(),
                result.memberTotalDeduction(), result.employerMandatoryTotal());
    }

    @Override
    public NeciPreviewDTO previewNeciContribution(EmployeeId id, BigDecimal pensionableEarnings) {
        // S-59's own spec sources tbPensionableEarnings "from the pay entry" -
        // this Track A stub has no live pay-entry threaded into this screen,
        // so a null/zero caller-supplied figure falls back to the employee's
        // own fixed-pay/hourly-rate derived period earnings instead.
        BigDecimal earnings = pensionableEarnings;
        if (earnings == null || earnings.compareTo(BigDecimal.ZERO) == 0) {
            EmployeeRecordDTO rec = store.employees.get(id);
            earnings = rec == null ? BigDecimal.ZERO : periodEarnings(rec);
        }
        NeciCalculator.NeciResult result = NeciCalculator.calculate(earnings, rules.neciRates());
        return new NeciPreviewDTO(result.memberContributionFinal(), result.employerContributionFinal());
    }

    @Override
    public SaveResult savePensionDeduction(PensionDeductionDTO draft) {
        EmployeeRecordDTO rec = store.employees.get(draft.employeeId());
        if (rec == null) {
            return new SaveResult(false, "Employee not found");
        }
        BigDecimal amount;
        String description;
        switch (draft.schemeType()) {
            case "STANDARD" -> {
                PensionReliefPreviewDTO preview = previewPensionRelief(draft);
                BigDecimal proposed = draft.percentageMode()
                        ? periodEarnings(rec).multiply(nz(draft.contributionValue()))
                        : nz(draft.contributionValue());
                amount = proposed;
                description = "Pension - Standard";
                store.pensionCumulativeReliefYtd.merge(draft.employeeId(), preview.taxRelievablePortion(), BigDecimal::add);
            }
            case "CWPS" -> {
                CwpsPreviewDTO preview = previewCwpsContribution(draft.employeeId(), draft.includeHealthTrust(), draft.includeBenevolentFund());
                amount = preview.memberTotalDeduction();
                description = "Pension - CWPS";
            }
            case "NECI" -> {
                NeciPreviewDTO preview = previewNeciContribution(draft.employeeId(), periodEarnings(rec));
                amount = preview.memberContribution();
                description = "Pension - NECI";
            }
            default -> {
                return new SaveResult(false, "Unknown scheme type: " + draft.schemeType());
            }
        }
        List<DeductionLineDTO> updated = new ArrayList<>(rec.deductions());
        updated.add(new DeductionLineDTO(new LineItemId(System.currentTimeMillis()), description, true, amount, null, null, null, null));
        return additionDeductionService.saveDeductions(draft.employeeId(), updated);
    }

    @Override
    public SaveResult savePensionTracingNumber(EmployeeId id, String tracingNumber) {
        if (!store.employees.containsKey(id)) {
            return new SaveResult(false, "Employee not found");
        }
        store.pensionTracingNumbers.put(id, tracingNumber);
        return new SaveResult(true, "Pension tracing number saved.");
    }

    private static int employeeAge(EmployeeRecordDTO rec) {
        LocalDate dob = rec.personalDetails().dateOfBirth();
        return dob == null ? 40 : Period.between(dob, LocalDate.now()).getYears();
    }

    private static BigDecimal periodEarnings(EmployeeRecordDTO rec) {
        BigDecimal fixedPay = rec.personalDetails().fixedPay();
        if (fixedPay != null) {
            return fixedPay;
        }
        BigDecimal hourlyRate = rec.personalDetails().hourlyRate();
        if (hourlyRate != null) {
            return hourlyRate.multiply(NOMINAL_WEEKLY_HOURS).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

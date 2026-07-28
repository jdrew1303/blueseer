package com.blueseer.pay;

import com.blueseer.pay.AutoEnrolmentDtos.AeContributionPeriodDTO;
import com.blueseer.pay.AutoEnrolmentDtos.AeEligibilityStatusDTO;
import com.blueseer.pay.AutoEnrolmentDtos.AecsSubmissionResult;
import com.blueseer.pay.AutoEnrolmentDtos.AepnCorrectionDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;

/**
 * Track A/B/C stub for {@link IAutoEnrolmentController}, backed by {@link
 * PayrollStubStore}. {@code aggregateAnnualEarningsAcrossEmployments} is
 * genuinely a NAERSA-side, cross-employer figure in production (per C-14's
 * own state context map note); this stub approximates it from this single
 * employment's own annualised pay, since Track A has no multi-employer data
 * model. 2026 is scheme Year 1, per the calc-engine spec.
 */
final class InMemoryAutoEnrolmentController implements IAutoEnrolmentController {

    private static final int SCHEME_YEAR_2026 = 1;

    private final PayrollStubStore store;
    private final ITaxYearRules rules = new Paye2026Rules();

    InMemoryAutoEnrolmentController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public AeEligibilityStatusDTO getEligibilityStatus(EmployeeId id) {
        EmployeeRecordDTO rec = store.employees.get(id);
        ITaxYearRules.AutoEnrolmentRates rates = rules.autoEnrolmentRates(SCHEME_YEAR_2026);
        String tierLabel = "Year " + SCHEME_YEAR_2026 + ": " + percent(rates.employeeContributionRate()) + " / "
                + percent(rates.employerContributionRate()) + " / " + percent(rates.employeeContributionRate().divide(rates.stateTopUpDivisor(), 4, RoundingMode.HALF_UP));
        if (rec == null) {
            return new AeEligibilityStatusDTO("Not Eligible", tierLabel, false, false);
        }
        boolean hasCoverage = store.aeHasExistingPensionCoverage.getOrDefault(id, false);
        boolean optedOut = store.aeOptedOutOrSuspended.getOrDefault(id, false);
        boolean eligible = AutoEnrolmentCalculator.isEligible(employeeAge(rec), annualisedEarnings(rec), hasCoverage);

        String status;
        if (optedOut) {
            status = eligible ? "Not Eligible" : "Not Eligible";
        } else if (hasCoverage) {
            status = "Exempt (existing pension coverage)";
        } else if (eligible) {
            status = "Eligible";
        } else {
            status = "Not Eligible";
        }
        return new AeEligibilityStatusDTO(status, tierLabel, hasCoverage, optedOut);
    }

    @Override
    public SaveResult setParticipationFlags(EmployeeId id, boolean hasExistingPensionCoverage, boolean optedOutOrSuspended) {
        if (!store.employees.containsKey(id)) {
            return new SaveResult(false, "Employee not found");
        }
        store.aeHasExistingPensionCoverage.put(id, hasExistingPensionCoverage);
        store.aeOptedOutOrSuspended.put(id, optedOutOrSuspended);
        return new SaveResult(true, "Auto-Enrolment participation flags updated.");
    }

    @Override
    public List<AeContributionPeriodDTO> getContributionHistory(EmployeeId id) {
        EmployeeRecordDTO rec = store.employees.get(id);
        if (rec == null) {
            return List.of();
        }
        boolean hasCoverage = store.aeHasExistingPensionCoverage.getOrDefault(id, false);
        boolean optedOut = store.aeOptedOutOrSuspended.getOrDefault(id, false);
        ITaxYearRules.AutoEnrolmentRates rates = rules.autoEnrolmentRates(SCHEME_YEAR_2026);
        boolean eligible = AutoEnrolmentCalculator.isEligible(employeeAge(rec), annualisedEarnings(rec), hasCoverage);

        List<AeContributionPeriodDTO> history = new ArrayList<>();
        BigDecimal cumulative = BigDecimal.ZERO;
        for (PayslipRecordDTO payslip : store.payslips.values()) {
            if (!payslip.employeeId().equals(id)) {
                continue;
            }
            AutoEnrolmentCalculator.AutoEnrolmentContext ctx = new AutoEnrolmentCalculator.AutoEnrolmentContext(
                    employeeAge(rec), annualisedEarnings(rec), hasCoverage, false, optedOut, payslip.gross(), cumulative);
            AutoEnrolmentCalculator.AutoEnrolmentResult result = AutoEnrolmentCalculator.calculate(ctx, rates);
            cumulative = cumulative.add(result.assessableGrossPayThisPeriod());
            BigDecimal total = result.employeeAeContributionThisPeriod().add(result.employerAeContributionThisPeriod()).add(result.stateTopUpThisPeriod());
            history.add(new AeContributionPeriodDTO(payslip.periodNumber(), result.employeeAeContributionThisPeriod(),
                    result.employerAeContributionThisPeriod(), result.stateTopUpThisPeriod(), total));
        }
        return history;
    }

    @Override
    public AecsSubmissionResult submitAecs(CompanyId id, int periodNumber) {
        return new AecsSubmissionResult(true, "AECS submission for period " + periodNumber + " queued (stub EDI transport - com.blueseer.edi not wired in this build).");
    }

    @Override
    public void handleAepnCorrection(AepnCorrectionDTO correction) {
        store.aepnPendingNoteOrNull.put(correction.employeeId(), correction.note());
    }

    private BigDecimal annualisedEarnings(EmployeeRecordDTO rec) {
        BigDecimal fixedPay = rec.personalDetails().fixedPay();
        if (fixedPay != null) {
            return fixedPay.multiply(BigDecimal.valueOf(12));
        }
        BigDecimal hourlyRate = rec.personalDetails().hourlyRate();
        if (hourlyRate != null) {
            return hourlyRate.multiply(new BigDecimal("40")).multiply(BigDecimal.valueOf(52)).setScale(2, RoundingMode.HALF_UP);
        }
        return BigDecimal.ZERO;
    }

    private static int employeeAge(EmployeeRecordDTO rec) {
        LocalDate dob = rec.personalDetails().dateOfBirth();
        return dob == null ? 40 : Period.between(dob, LocalDate.now()).getYears();
    }

    private static String percent(BigDecimal rate) {
        return rate.multiply(BigDecimal.valueOf(100)).stripTrailingZeros().toPlainString() + "%";
    }
}

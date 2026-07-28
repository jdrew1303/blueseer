package com.blueseer.pay;

import com.blueseer.pay.BikDtos.BikBenefitDTO;
import com.blueseer.pay.BikDtos.BikBenefitPreviewDTO;
import com.blueseer.pay.BikDtos.BikCarDTO;
import com.blueseer.pay.BikDtos.BikLoanDTO;
import com.blueseer.pay.BikDtos.BikLoanSubPeriodDTO;
import com.blueseer.pay.BikDtos.BikPreviewDTO;
import com.blueseer.pay.BikDtos.BikVanDTO;
import com.blueseer.pay.BikDtos.BikVehicleDTO;
import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.LineItemId;
import com.blueseer.pay.ty2026.Paye2026Rules;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/** Track A/B/C stub for {@link IBikController}, backed by {@link PayrollStubStore}. */
final class InMemoryBikController implements IBikController {

    private static final LocalDate TAX_YEAR_START = LocalDate.of(2026, 1, 1);
    private static final LocalDate TAX_YEAR_END = LocalDate.of(2026, 12, 31);

    private final PayrollStubStore store;
    private final IAdditionDeductionService additionDeductionService;
    private final ITaxYearRules rules = new Paye2026Rules();

    InMemoryBikController(PayrollStubStore store, IAdditionDeductionService additionDeductionService) {
        this.store = store;
        this.additionDeductionService = additionDeductionService;
    }

    @Override
    public BikPreviewDTO previewCarBik(BikCarDTO draft) {
        int[] days = daysAvailable(draft.availableFrom(), draft.availableTo());
        BikCarCalculator.BikCarContext ctx = new BikCarCalculator.BikCarContext(
                nz(draft.omvOriginal()), nz(draft.co2EmissionsGramsPerKm()), nz(draft.actualBusinessKilometres()),
                days[0], days[1], draft.isElectricVehicle(), nz(draft.amountMadeGoodByEmployee()),
                draft.qualifiesFor20PercentReduction());
        BikCarCalculator.BikCarResult result = BikCarCalculator.calculate(ctx, rules.bikCarRates());
        return new BikPreviewDTO(result.vehicleCategory(), result.ratePercentAtActualMileage(), result.reducedOmv(),
                result.cashEquivalentForPeriod(), result.finalBikChargeable(), result.steps());
    }

    @Override
    public BikPreviewDTO previewVanBik(BikVanDTO draft) {
        int[] days = daysAvailable(draft.availableFrom(), draft.availableTo());
        BikVanCalculator.BikVanContext ctx = new BikVanCalculator.BikVanContext(
                nz(draft.omvOriginal()), nz(draft.amountMadeGoodByEmployee()), days[0], days[1],
                draft.qualifiesForLimitedPrivateUseExemption());
        BikVanCalculator.BikVanResult result = BikVanCalculator.calculate(ctx, rules.bikCarRates().temporaryOmvReduction(), rules.bikVanRate());
        return new BikPreviewDTO(null, null, result.reducedOmv(), result.cashEquivalentForPeriod(), result.finalBikChargeable(), result.steps());
    }

    @Override
    public SaveResult saveBikVehicleEntry(BikVehicleDTO data) {
        EmployeeRecordDTO rec = store.employees.get(data.employeeId());
        if (rec == null) {
            return new SaveResult(false, "Employee not found");
        }
        BigDecimal finalBik;
        String description;
        LocalDate endDate;
        if (data.carOrNull() != null) {
            finalBik = previewCarBik(data.carOrNull()).finalBikChargeable();
            description = "BIK - Company Car";
            endDate = data.carOrNull().availableTo();
        } else if (data.vanOrNull() != null) {
            finalBik = previewVanBik(data.vanOrNull()).finalBikChargeable();
            description = "BIK - Company Van";
            endDate = data.vanOrNull().availableTo();
        } else {
            return new SaveResult(false, "No vehicle entry supplied");
        }
        List<AdditionLineDTO> updated = new ArrayList<>(rec.additions());
        updated.add(new AdditionLineDTO(new LineItemId(System.currentTimeMillis()), description, true, finalBik, endDate));
        return additionDeductionService.saveAdditions(data.employeeId(), updated);
    }

    @Override
    public BikPreviewDTO previewLoanBik(BikLoanDTO draft) {
        List<BikLoanCalculator.SubPeriodInput> subPeriods = new ArrayList<>();
        for (BikLoanSubPeriodDTO sp : draft.subPeriods()) {
            subPeriods.add(new BikLoanCalculator.SubPeriodInput(nz(sp.openingPrincipalBalance()), sp.daysInSubPeriod(), nz(sp.actualInterestPaidForSubPeriod())));
        }
        BikLoanCalculator.BikLoanContext ctx = new BikLoanCalculator.BikLoanContext(
                draft.loanCategory(), subPeriods, TAX_YEAR_START.lengthOfYear(),
                draft.isJointLoanWithNonEmployee(), draft.isMarriedOrCivilPartnerJointLoan(), nz(draft.employeeSharePercentage()));
        BikLoanCalculator.BikLoanResult result = BikLoanCalculator.calculate(ctx, rules.bikLoanRates());
        return new BikPreviewDTO(null, null, result.summedAnnualTaxableBenefit(), result.summedAnnualTaxableBenefit(),
                result.finalAnnualNotionalPay(), result.steps());
    }

    @Override
    public SaveResult saveLoanEntry(BikLoanDTO data) {
        EmployeeRecordDTO rec = store.employees.get(data.employeeId());
        if (rec == null) {
            return new SaveResult(false, "Employee not found");
        }
        BigDecimal annualNotionalPay = previewLoanBik(data).finalBikChargeable();
        List<AdditionLineDTO> updated = new ArrayList<>(rec.additions());
        updated.add(new AdditionLineDTO(new LineItemId(System.currentTimeMillis()), "BIK - Preferential Loan", true, annualNotionalPay, null));
        return additionDeductionService.saveAdditions(data.employeeId(), updated);
    }

    @Override
    public BikBenefitPreviewDTO previewBenefitBik(BikBenefitDTO draft) {
        if (!draft.smallBenefitExemptionEligible()) {
            return new BikBenefitPreviewDTO(BigDecimal.ZERO, nz(draft.benefitAmount()));
        }
        BikBenefitCalculator.UsageState usage = new BikBenefitCalculator.UsageState(
                store.bikBenefitCountThisYear.getOrDefault(draft.employeeId(), 0),
                store.bikBenefitValueThisYear.getOrDefault(draft.employeeId(), BigDecimal.ZERO));
        BikBenefitCalculator.BenefitResult result = BikBenefitCalculator.calculate(nz(draft.benefitAmount()), usage);
        return new BikBenefitPreviewDTO(result.remainingAnnualAllowance(), result.taxableAmount());
    }

    @Override
    public SaveResult saveBenefitEntry(BikBenefitDTO data) {
        EmployeeRecordDTO rec = store.employees.get(data.employeeId());
        if (rec == null) {
            return new SaveResult(false, "Employee not found");
        }
        BikBenefitPreviewDTO preview = previewBenefitBik(data);
        boolean qualified = data.smallBenefitExemptionEligible() && preview.taxableAmount().compareTo(BigDecimal.ZERO) == 0;
        if (qualified) {
            store.bikBenefitCountThisYear.merge(data.employeeId(), 1, Integer::sum);
            store.bikBenefitValueThisYear.merge(data.employeeId(), nz(data.benefitAmount()), BigDecimal::add);
        }
        if (preview.taxableAmount().compareTo(BigDecimal.ZERO) > 0) {
            List<AdditionLineDTO> updated = new ArrayList<>(rec.additions());
            String description = data.benefitDescription() == null || data.benefitDescription().isBlank()
                    ? "BIK - Benefit" : "BIK - " + data.benefitDescription();
            updated.add(new AdditionLineDTO(new LineItemId(System.currentTimeMillis()), description, true, preview.taxableAmount(), null));
            return additionDeductionService.saveAdditions(data.employeeId(), updated);
        }
        return new SaveResult(true, "Benefit recorded, fully covered by the Small Benefit Exemption - no taxable amount added.");
    }

    /** {@code [daysAvailable, daysInYear]}, clamped to the 2026 tax year. */
    private static int[] daysAvailable(LocalDate availableFrom, LocalDate availableTo) {
        LocalDate from = availableFrom == null || availableFrom.isBefore(TAX_YEAR_START) ? TAX_YEAR_START : availableFrom;
        LocalDate to = availableTo == null || availableTo.isAfter(TAX_YEAR_END) ? TAX_YEAR_END : availableTo;
        int daysInYear = TAX_YEAR_START.lengthOfYear();
        if (to.isBefore(from)) {
            return new int[] {0, daysInYear};
        }
        int daysAvailable = (int) (ChronoUnit.DAYS.between(from, to) + 1);
        return new int[] {daysAvailable, daysInYear};
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

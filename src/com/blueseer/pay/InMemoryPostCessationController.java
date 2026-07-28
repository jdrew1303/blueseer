package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.EmployeeDtos.PersonalDetailsDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.PayslipId;
import com.blueseer.pay.PostCessationDtos.PostCessationResult;
import com.blueseer.pay.PostCessationDtos.TaxYearScope;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Track A/B/C stub for {@link IPostCessationController}, backed by {@link
 * PayrollStubStore}. A post-cessation payment is treated as a single
 * standalone Emergency-basis payment (period 1 of {@link PayFrequency#WEEKLY}'s
 * 52) run through PAYE/PRSI/USC - unlike S-50's termination lump sum, TDM
 * guidance for a late payment of ordinary relevant emoluments does not
 * exempt it from PRSI, so no bypass routing applies here.
 */
final class InMemoryPostCessationController implements IPostCessationController {

    private static final int CURRENT_TAX_YEAR = PayrollEngineFactory.registeredTaxYears()
            .get(PayrollEngineFactory.registeredTaxYears().size() - 1);

    private final PayrollStubStore store;
    private final IPayrollCalculationService calcService;

    InMemoryPostCessationController(PayrollStubStore store, IPayrollCalculationService calcService) {
        this.store = store;
        this.calcService = calcService;
    }

    @Override
    public List<EmployeeSummaryDTO> getEligibleFormerEmployees(CompanyId id, TaxYearScope scope) {
        int year = scope.resolveYear(CURRENT_TAX_YEAR);
        List<EmployeeSummaryDTO> results = new ArrayList<>();
        for (var entry : store.leaveDates.entrySet()) {
            if (entry.getValue().getYear() != year) {
                continue;
            }
            EmployeeRecordDTO rec = store.employees.get(entry.getKey());
            if (rec == null) {
                continue;
            }
            PersonalDetailsDTO p = rec.personalDetails();
            results.add(new EmployeeSummaryDTO(entry.getKey(), p.surname(), p.firstName(), p.worksNumber(), true));
        }
        return results;
    }

    @Override
    public PostCessationResult processPayment(EmployeeId id, BigDecimal amount, LocalDate paymentDate, TaxYearScope scope) {
        int taxYear = scope.resolveYear(CURRENT_TAX_YEAR);
        EmployeeRecordDTO rec = store.employees.get(id);
        if (rec == null) {
            return new PostCessationResult(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "Employee not found.");
        }
        boolean hasPps = rec.personalDetails().ppsNumber() != null && !rec.personalDetails().ppsNumber().isBlank();

        try {
            PayeCalculator.PayeContext payeCtx = new PayeCalculator.PayeContext(
                    PayeCalculator.CalculationBasis.EMERGENCY, amount, 52, 1,
                    BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, hasPps, 1);
            EngineResult paye = calcService.calculatePaye(taxYear, PayFrequency.WEEKLY, payeCtx);

            PrsiCalculator.PrsiContext prsiCtx = new PrsiCalculator.PrsiContext(amount);
            PrsiCalculator.PrsiResult prsi = calcService.calculatePrsi(taxYear, paymentDate, PayFrequency.WEEKLY, prsiCtx);

            UscCalculator.UscContext uscCtx = new UscCalculator.UscContext(
                    UscCalculator.CalculationBasis.EMERGENCY, amount, 52, 1,
                    BigDecimal.ZERO, BigDecimal.ZERO, false, false);
            EngineResult usc = calcService.calculateUsc(taxYear, uscCtx);

            BigDecimal net = amount.subtract(paye.amount()).subtract(prsi.employee().amount()).subtract(usc.amount());

            List<CalculationStep> steps = new ArrayList<>();
            steps.addAll(paye.steps());
            steps.addAll(prsi.employee().steps());
            steps.addAll(usc.steps());

            PayslipId payslipId = new PayslipId(store.nextPayslipId());
            store.payslips.put(payslipId, new PayslipRecordDTO(payslipId, id, store.currentPeriodNumber, paymentDate,
                    amount, paye.amount(), prsi.employee().amount(), usc.amount(), net, steps));

            return new PostCessationResult(true, paye.amount(), prsi.employee().amount(), usc.amount(), net, "Payment processed.");
        } catch (IllegalStateException e) {
            return new PostCessationResult(false, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO,
                    "No tax rules registered for " + taxYear + ": " + e.getMessage());
        }
    }
}

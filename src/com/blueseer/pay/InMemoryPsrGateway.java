package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.PersonalDetailsDTO;
import com.blueseer.pay.EmployeeDtos.RevenueDetailsDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.LineItemId;
import com.blueseer.pay.PayrollIds.PsrBatchId;
import com.blueseer.pay.PsrDtos.CorrectionDetailsDTO;
import com.blueseer.pay.PsrDtos.CorrectionResult;
import com.blueseer.pay.PsrDtos.CorrectionType;
import com.blueseer.pay.PsrDtos.PsrControlRowDTO;
import com.blueseer.pay.PsrDtos.PsrLineItemDTO;
import com.blueseer.pay.PsrDtos.PsrRecheckResultDTO;
import com.blueseer.pay.PsrDtos.PsrSubmissionResult;
import com.blueseer.pay.PsrDtos.PsrSummaryDTO;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Track A stub for {@link IPsrGateway}, backed by {@link PayrollStubStore}.
 * PSR batches are created automatically by {@link PayPeriodFinaliser} in
 * "Due" status - this class is what moves them to "Filed" and applies
 * corrections against the employee record / next live run.
 */
final class InMemoryPsrGateway implements IPsrGateway {

    private final PayrollStubStore store;
    private final IEmployeeRepository employeeRepository;
    private final IRevenueDetailsService revenueDetailsService;
    private final IAdditionDeductionService additionDeductionService;

    InMemoryPsrGateway(PayrollStubStore store, IEmployeeRepository employeeRepository,
            IRevenueDetailsService revenueDetailsService, IAdditionDeductionService additionDeductionService) {
        this.store = store;
        this.employeeRepository = employeeRepository;
        this.revenueDetailsService = revenueDetailsService;
        this.additionDeductionService = additionDeductionService;
    }

    @Override
    public PsrSummaryDTO getPendingPsrSummary(CompanyId id) {
        PayrollStubStore.PsrBatchRecord pending = firstDueBatch();
        if (pending == null) {
            return new PsrSummaryDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }
        BigDecimal paye = BigDecimal.ZERO;
        BigDecimal usc = BigDecimal.ZERO;
        BigDecimal prsi = BigDecimal.ZERO;
        for (PsrLineItemDTO line : detailFor(pending)) {
            paye = paye.add(line.paye());
            prsi = prsi.add(line.prsi());
            usc = usc.add(line.usc());
        }
        return new PsrSummaryDTO(paye, usc, prsi, BigDecimal.ZERO);
    }

    @Override
    public List<PsrLineItemDTO> getPsrDetail(CompanyId id) {
        PayrollStubStore.PsrBatchRecord pending = firstDueBatch();
        return pending == null ? List.of() : detailFor(pending);
    }

    @Override
    public PsrSubmissionResult submitPsr(CompanyId id) {
        PayrollStubStore.PsrBatchRecord pending = firstDueBatch();
        if (pending == null) {
            return new PsrSubmissionResult(false, null, BigDecimal.ZERO);
        }
        return submitBatch(pending.id());
    }

    @Override
    public List<PsrControlRowDTO> getPsrControlPanelRows(CompanyId id) {
        List<PsrControlRowDTO> rows = new ArrayList<>();
        for (PayrollStubStore.PsrBatchRecord batch : store.psrBatches.values()) {
            int returned = "Filed".equals(batch.status()) ? batch.payslipIds().size() : 0;
            rows.add(new PsrControlRowDTO(batch.id(), batch.payDate(), batch.payslipIds().size(), returned, batch.status()));
        }
        return rows;
    }

    @Override
    public void markPsrAsSent(PsrBatchId id) {
        PayrollStubStore.PsrBatchRecord batch = store.psrBatches.get(id);
        if (batch != null) {
            String reference = batch.revenueReferenceNumberOrNull() != null ? batch.revenueReferenceNumberOrNull() : newReference();
            store.psrBatches.put(id, new PayrollStubStore.PsrBatchRecord(id, batch.payDate(), batch.payslipIds(), "Filed", reference));
        }
    }

    @Override
    public void markPsrAsNotSent(PsrBatchId id) {
        PayrollStubStore.PsrBatchRecord batch = store.psrBatches.get(id);
        if (batch != null) {
            store.psrBatches.put(id, new PayrollStubStore.PsrBatchRecord(id, batch.payDate(), batch.payslipIds(), "Due", null));
        }
    }

    @Override
    public PsrSubmissionResult submitPsrBatch(PsrBatchId id) {
        return submitBatch(id);
    }

    @Override
    public CorrectionResult applyCorrection(CorrectionType type, CorrectionDetailsDTO details) {
        if (details instanceof CorrectionDetailsDTO.PaymentWasDifferent d) {
            return applyPaymentWasDifferent(d);
        }
        if (details instanceof CorrectionDetailsDTO.WrongPpsNumber d) {
            return applyWrongPpsNumber(d);
        }
        if (details instanceof CorrectionDetailsDTO.WrongPrsiClass d) {
            return applyWrongPrsiClass(d);
        }
        return new CorrectionResult(false, "Unsupported correction type: " + type);
    }

    @Override
    public List<PsrRecheckResultDTO> recheckSubmissions(CompanyId id) {
        List<PsrRecheckResultDTO> results = new ArrayList<>();
        for (PayrollStubStore.PsrBatchRecord batch : store.psrBatches.values()) {
            // Track A has no real EDI channel to re-query yet - rechecking
            // simply re-reports the locally stored status, which is exactly
            // what a genuine "no changes found" recheck outcome looks like.
            results.add(new PsrRecheckResultDTO(batch.id(), batch.payDate(), batch.status(), batch.status()));
        }
        return results;
    }

    private PayrollStubStore.PsrBatchRecord firstDueBatch() {
        for (PayrollStubStore.PsrBatchRecord batch : store.psrBatches.values()) {
            if ("Due".equals(batch.status())) {
                return batch;
            }
        }
        return null;
    }

    private List<PsrLineItemDTO> detailFor(PayrollStubStore.PsrBatchRecord batch) {
        List<PsrLineItemDTO> lines = new ArrayList<>();
        for (var payslipId : batch.payslipIds()) {
            PayslipRecordDTO payslip = store.payslips.get(payslipId);
            if (payslip != null) {
                lines.add(new PsrLineItemDTO(payslip.employeeId(), payslip.paye(), payslip.prsiEmployee(), payslip.usc(), BigDecimal.ZERO));
            }
        }
        return lines;
    }

    private PsrSubmissionResult submitBatch(PsrBatchId id) {
        PayrollStubStore.PsrBatchRecord batch = store.psrBatches.get(id);
        if (batch == null) {
            return new PsrSubmissionResult(false, null, BigDecimal.ZERO);
        }
        String reference = newReference();
        store.psrBatches.put(id, new PayrollStubStore.PsrBatchRecord(id, batch.payDate(), batch.payslipIds(), "Filed", reference));
        BigDecimal total = BigDecimal.ZERO;
        for (PsrLineItemDTO line : detailFor(batch)) {
            total = total.add(line.paye()).add(line.prsi()).add(line.usc()).add(line.lpt());
        }
        return new PsrSubmissionResult(true, reference, total);
    }

    private String newReference() {
        return "REV-" + store.nextPsrBatchId();
    }

    /**
     * "Follow the money" per C-33's spec note - never mutates the already-
     * finalised historical payslip, only injects a one-off taxable addition
     * into the employee's *next* live run for the difference.
     */
    private CorrectionResult applyPaymentWasDifferent(CorrectionDetailsDTO.PaymentWasDifferent d) {
        PayslipRecordDTO original = null;
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.employeeId().equals(d.employeeId()) && p.payDate().equals(d.originalPeriod())) {
                original = p;
                break;
            }
        }
        if (original == null) {
            return new CorrectionResult(false, "No finalised payslip found for that employee/period");
        }
        BigDecimal difference = d.actualAmountPaid().subtract(original.net());
        EmployeeRecordDTO rec = store.employees.get(d.employeeId());
        if (rec == null) {
            return new CorrectionResult(false, "Employee not found");
        }
        List<AdditionLineDTO> updated = new ArrayList<>(rec.additions());
        updated.add(new AdditionLineDTO(new LineItemId(System.currentTimeMillis()), "PSR Correction - Payment Adjustment", true, difference, null));
        additionDeductionService.saveAdditions(d.employeeId(), updated);
        return new CorrectionResult(true, "Adjustment of " + difference + " will be applied to the next live run.");
    }

    private CorrectionResult applyWrongPpsNumber(CorrectionDetailsDTO.WrongPpsNumber d) {
        EmployeeRecordDTO rec = store.employees.get(d.employeeId());
        if (rec == null) {
            return new CorrectionResult(false, "Employee not found");
        }
        PersonalDetailsDTO p = rec.personalDetails();
        PersonalDetailsDTO updated = new PersonalDetailsDTO(p.id(), p.surname(), p.firstName(), p.address(), p.dateOfBirth(),
                p.email(), p.payslipPassword(), p.director(), p.departmentId(), d.correctPpsNumber(), p.employmentId(),
                p.worksNumber(), p.hourlyRate(), p.fixedPay(), p.payMethod(), p.bank(), p.branch(), p.sortCode(),
                p.accountNumber(), p.creditUnionRef());
        employeeRepository.savePersonalDetails(updated);
        return new CorrectionResult(true, "PPS Number corrected.");
    }

    private CorrectionResult applyWrongPrsiClass(CorrectionDetailsDTO.WrongPrsiClass d) {
        EmployeeRecordDTO rec = store.employees.get(d.employeeId());
        if (rec == null || rec.revenueDetails() == null) {
            return new CorrectionResult(false, "Employee not found");
        }
        RevenueDetailsDTO r = rec.revenueDetails();
        RevenueDetailsDTO updated = new RevenueDetailsDTO(r.id(), r.startDate(), r.startWeek(), d.correctPrsiClass(), r.exemptionCodes());
        revenueDetailsService.saveRevenueDetails(updated);
        return new CorrectionResult(true, "PRSI Class corrected.");
    }
}

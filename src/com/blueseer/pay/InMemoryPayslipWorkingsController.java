package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayProcessingDtos.CalculationStepDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Track A stub for {@link IPayslipWorkingsController}, backed by {@link PayrollStubStore}. */
final class InMemoryPayslipWorkingsController implements IPayslipWorkingsController {

    private final PayrollStubStore store;
    private final IPayrollCalculationService calcService;

    InMemoryPayslipWorkingsController(PayrollStubStore store, IPayrollCalculationService calcService) {
        this.store = store;
        this.calcService = calcService;
    }

    @Override
    public List<CalculationStepDTO> getWorkings(EmployeeId employeeId, int periodNumber) {
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.employeeId().equals(employeeId) && p.periodNumber() == periodNumber) {
                return toDto(p.steps());
            }
        }
        EmployeeRecordDTO rec = store.employees.get(employeeId);
        PayEntryDTO draft = store.draftPayEntries.get(employeeId);
        if (rec == null || draft == null) {
            return List.of();
        }
        PayslipEngineChain.ChainResult chain = PayslipEngineChain.run(rec, draft, periodNumber, LocalDate.now(), calcService,
                store.employeeFrequencyOverride.get(employeeId), store.appliedRpnData.get(employeeId));
        return toDto(chain.allSteps());
    }

    private static List<CalculationStepDTO> toDto(List<CalculationStep> steps) {
        List<CalculationStepDTO> result = new ArrayList<>();
        for (CalculationStep s : steps) {
            result.add(new CalculationStepDTO(s.engineName(), s.stepLabel(), s.formula(), s.resultValue()));
        }
        return result;
    }
}

package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayProcessingDtos.BatchSaveResult;
import com.blueseer.pay.PayProcessingDtos.NetToGrossResultDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryRowDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.DepartmentId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Track A stub for {@link IPayEntryController}, backed by {@link PayrollStubStore}. */
final class InMemoryPayEntryController implements IPayEntryController {

    private final PayrollStubStore store;
    private final IPayrollCalculationService calculationService;

    InMemoryPayEntryController(PayrollStubStore store, IPayrollCalculationService calculationService) {
        this.store = store;
        this.calculationService = calculationService;
    }

    @Override
    public int getCurrentPeriodNumber(CompanyId id) {
        return store.currentPeriodNumber;
    }

    @Override
    public PayEntryDTO loadPayEntry(EmployeeId id, int periodNumber) {
        PayEntryDTO draft = store.draftPayEntries.get(id);
        if (draft != null && draft.periodNumber() == periodNumber) {
            return draft;
        }
        EmployeeRecordDTO rec = store.employees.get(id);
        BigDecimal hourlyRate = rec == null ? null : rec.personalDetails().hourlyRate();
        BigDecimal basicPay = rec == null ? null : rec.personalDetails().fixedPay();
        return new PayEntryDTO(id, periodNumber, hourlyRate, null, null, null, null, basicPay, null, 0, false, null, null);
    }

    @Override
    public BigDecimal previewGrossPay(PayEntryDTO draft) {
        return GrossPayAssembler.computeGrossPay(draft);
    }

    @Override
    public SaveResult savePayEntry(PayEntryDTO draft) {
        store.draftPayEntries.put(draft.employeeId(), draft);
        if (draft.leaving() && draft.leaveDateOrNull() != null) {
            store.leaveDates.put(draft.employeeId(), draft.leaveDateOrNull());
        }
        return new SaveResult(false, null);
    }

    @Override
    public List<Integer> previewHolidaySpreadLockedWeeks(EmployeeId id, int additionalWeeks) {
        List<Integer> locked = new ArrayList<>();
        int from = store.currentPeriodNumber + 1;
        for (int i = 0; i < additionalWeeks; i++) {
            locked.add(from + i);
        }
        return locked;
    }

    @Override
    public List<PayEntryRowDTO> loadQuickEditGrid(CompanyId companyId, int periodNumber, DepartmentId filterOrNull) {
        List<PayEntryRowDTO> rows = new ArrayList<>();
        for (EmployeeRecordDTO rec : store.employees.values()) {
            if (store.leaveDates.containsKey(rec.id())) {
                continue;
            }
            if (filterOrNull != null && !filterOrNull.equals(rec.personalDetails().departmentId())) {
                continue;
            }
            PayEntryDTO draft = loadPayEntry(rec.id(), periodNumber);
            String name = rec.personalDetails().surname() + ", " + rec.personalDetails().firstName();
            rows.add(new PayEntryRowDTO(rec.id(), name, draft.standardHours(), draft.basicPay(), GrossPayAssembler.computeGrossPay(draft)));
        }
        return rows;
    }

    @Override
    public BatchSaveResult saveQuickEditBatch(List<PayEntryRowDTO> rows) {
        int saved = 0;
        for (PayEntryRowDTO row : rows) {
            PayEntryDTO existing = loadPayEntry(row.employeeId(), store.currentPeriodNumber);
            PayEntryDTO updated = new PayEntryDTO(row.employeeId(), store.currentPeriodNumber, existing.hourlyRate(),
                    row.hours(), existing.timeAndAThirdHours(), existing.timeAndAHalfHours(), existing.doubleTimeHours(),
                    row.basicPay(), existing.holidayPayAmount(), existing.additionalWeeksSpread(),
                    existing.leaving(), existing.leaveDateOrNull(), existing.noteOrNull());
            store.draftPayEntries.put(row.employeeId(), updated);
            saved++;
        }
        return new BatchSaveResult(saved, List.of());
    }

    @Override
    public NetToGrossResultDTO solveNetToGross(EmployeeId id, int periodNumber, BigDecimal targetNet) {
        EmployeeRecordDTO rec = store.employees.get(id);
        return NetToGrossSolver.solve(rec, periodNumber, targetNet, calculationService,
                store.employeeFrequencyOverride.get(id), store.appliedRpnData.get(id));
    }
}

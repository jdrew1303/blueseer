package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayProcessingDtos.PreviewRowDTO;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * S-21 Payroll Preview: runs {@link PayslipEngineChain} for every employee
 * with a draft pay entry this period - a read-only look at what S-22 would
 * persist if run right now, without locking anything or advancing the
 * period counter.
 */
final class PayrollPreviewAggregator {

    private PayrollPreviewAggregator() {
    }

    static List<PreviewRowDTO> aggregate(PayrollStubStore store, IPayrollCalculationService calcService, int periodNumber) {
        List<PreviewRowDTO> rows = new ArrayList<>();
        for (Map.Entry<PayrollIds.EmployeeId, PayEntryDTO> entry : store.draftPayEntries.entrySet()) {
            EmployeeRecordDTO rec = store.employees.get(entry.getKey());
            if (rec == null) {
                continue;
            }
            PayslipEngineChain.ChainResult chain = PayslipEngineChain.run(rec, entry.getValue(), periodNumber, LocalDate.now(), calcService,
                    store.employeeFrequencyOverride.get(entry.getKey()), store.appliedRpnData.get(entry.getKey()));
            String name = rec.personalDetails().surname() + ", " + rec.personalDetails().firstName();
            rows.add(new PreviewRowDTO(rec.id(), name, chain.gross(), chain.paye().amount(),
                    chain.prsi().employee().amount(), chain.usc().amount(), chain.net()));
        }
        return rows;
    }
}

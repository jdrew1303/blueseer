package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.PreviewRowDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.util.List;

/** Track A stub for {@link IPayrollPreviewController}, backed by {@link PayrollStubStore}. */
final class InMemoryPayrollPreviewController implements IPayrollPreviewController {

    private final PayrollStubStore store;
    private final IPayrollCalculationService calcService;

    InMemoryPayrollPreviewController(PayrollStubStore store, IPayrollCalculationService calcService) {
        this.store = store;
        this.calcService = calcService;
    }

    @Override
    public int getCurrentPeriodNumber(CompanyId id) {
        return store.currentPeriodNumber;
    }

    @Override
    public List<PreviewRowDTO> getPreview(CompanyId id, int periodNumber) {
        return PayrollPreviewAggregator.aggregate(store, calcService, periodNumber);
    }
}

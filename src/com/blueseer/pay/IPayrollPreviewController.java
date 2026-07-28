package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.PreviewRowDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.util.List;

/** Controller backing S-21 Payroll Preview. */
public interface IPayrollPreviewController {

    /** Resolved fresh on every show, per {@link IPayEntryController#getCurrentPeriodNumber}'s note. */
    int getCurrentPeriodNumber(CompanyId id);

    List<PreviewRowDTO> getPreview(CompanyId id, int periodNumber);
}

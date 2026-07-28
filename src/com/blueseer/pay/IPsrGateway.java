package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.PsrBatchId;
import com.blueseer.pay.PsrDtos.CorrectionDetailsDTO;
import com.blueseer.pay.PsrDtos.CorrectionResult;
import com.blueseer.pay.PsrDtos.CorrectionType;
import com.blueseer.pay.PsrDtos.PsrControlRowDTO;
import com.blueseer.pay.PsrDtos.PsrLineItemDTO;
import com.blueseer.pay.PsrDtos.PsrSubmissionResult;
import com.blueseer.pay.PsrDtos.PsrSummaryDTO;

import java.util.List;

/** §4.1 gateway (prepare/submit/control-panel/correction) over the {@code com.blueseer.edi} PSR transport. Backs S-31/S-32/S-33. */
public interface IPsrGateway {

    PsrSummaryDTO getPendingPsrSummary(CompanyId id);

    List<PsrLineItemDTO> getPsrDetail(CompanyId id);

    PsrSubmissionResult submitPsr(CompanyId id);

    List<PsrControlRowDTO> getPsrControlPanelRows(CompanyId id);

    void markPsrAsSent(PsrBatchId id);

    void markPsrAsNotSent(PsrBatchId id);

    /** Submits each selected outstanding PSR in sequence, per the exemplar's own stated procedure - not parallel. */
    PsrSubmissionResult submitPsrBatch(PsrBatchId id);

    /** Single polymorphic entry point for S-33; {@code details} is a sealed type, one variant per applicable {@link CorrectionType}. */
    CorrectionResult applyCorrection(CorrectionType type, CorrectionDetailsDTO details);

    /** S-34 - backs {@code PsrRecheckService}. */
    List<PsrDtos.PsrRecheckResultDTO> recheckSubmissions(CompanyId id);
}

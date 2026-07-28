package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.AnomalyDTO;
import com.blueseer.pay.PayProcessingDtos.FinalisationResult;
import com.blueseer.pay.PayProcessingDtos.FinalisationSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.time.LocalDate;
import java.util.List;

/** Controller backing S-22 Finalise Pay Period and S-24 Computational Anomaly. */
public interface IFinalisationController {

    FinalisationSummaryDTO getFinalisationSummary(CompanyId id);

    /** Called before S-22's own confirmation dialog; backed by {@link ComputationalAnomalyDetector}. */
    List<AnomalyDTO> detectAnomalies(CompanyId id, int periodNumber);

    /**
     * Expected behaviour: invokes {@link PayPeriodFinaliser}, which locks
     * the period, runs the full engine chain per employee, persists payslip
     * records, and (once §3.6 lands) auto-generates the pending PSR.
     */
    FinalisationResult finalisePayPeriod(CompanyId id, LocalDate payDate, String weeklyReferenceNoteOrNull);
}

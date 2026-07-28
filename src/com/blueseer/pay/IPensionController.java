package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PensionDtos.CwpsPreviewDTO;
import com.blueseer.pay.PensionDtos.NeciPreviewDTO;
import com.blueseer.pay.PensionDtos.PensionDeductionDTO;
import com.blueseer.pay.PensionDtos.PensionReliefPreviewDTO;

import java.math.BigDecimal;

/** Controller backing S-59 Pension Deduction Setup and S-60 Pension Tracing Number Entry. */
public interface IPensionController {

    /** Backs C-15 (Standard scheme only). */
    PensionReliefPreviewDTO previewPensionRelief(PensionDeductionDTO draft);

    /** Backs C-23; takes no earnings input, since CWPS's amounts don't depend on what the employee earns. */
    CwpsPreviewDTO previewCwpsContribution(EmployeeId id);

    /** Backs C-24. */
    NeciPreviewDTO previewNeciContribution(EmployeeId id, BigDecimal pensionableEarnings);

    /** Persists as a recurring deduction feeding S-08, using whichever engine (C-15/C-23/C-24) matches {@code schemeType}. */
    SaveResult savePensionDeduction(PensionDeductionDTO draft);

    /** Backs S-60, embedded within S-59's panel. */
    SaveResult savePensionTracingNumber(EmployeeId id, String tracingNumber);
}

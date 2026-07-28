package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.CalculationStepDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.util.List;

/**
 * Controller backing S-23 Payslip Workings. Keyed by (employee, period)
 * rather than a {@code PayslipId} - Track A has no persisted payslip yet for
 * a period that hasn't been through S-22, so this resolves a finalised
 * payslip's persisted trail when one exists, or recomputes live from the
 * current draft when it doesn't (S-21's pre-finalisation preview rows).
 */
public interface IPayslipWorkingsController {

    List<CalculationStepDTO> getWorkings(EmployeeId employeeId, int periodNumber);
}

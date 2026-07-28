package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmergencyStatusDTO;
import com.blueseer.pay.EmployeeDtos.RevenueDetailsDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

/**
 * §4.1 service over the {@code emp_rpn_ie}-family table. Backs S-06 -
 * deliberately has no method touching tax credits or cut-off points; those
 * arrive only via {@link IRpnGateway}.
 */
public interface IRevenueDetailsService {

    SaveResult saveRevenueDetails(RevenueDetailsDTO data);

    /** Drives S-06's lblEmergencyBanner; polled on tab load and after any RPN import. */
    EmergencyStatusDTO getCurrentEmergencyStatus(EmployeeId id);
}

package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.RpnDtos.BulkRpnResultDTO;
import com.blueseer.pay.RpnDtos.RpnDataDTO;
import com.blueseer.pay.RpnDtos.RpnDiffDTO;
import com.blueseer.pay.RpnDtos.RpnLogEntryDTO;
import com.blueseer.pay.RpnDtos.RpnLogFilterDTO;
import com.blueseer.pay.RpnDtos.RpnRequestOutcome;

import java.util.List;

/** §4.1 gateway (request + bulk retrieve) over the {@code com.blueseer.edi} RPN transport. Backs S-14/S-15/S-16. */
public interface IRpnGateway {

    RpnRequestOutcome requestNewStarterRpn(EmployeeId id);

    RpnRequestOutcome confirmEmploymentRegistration(EmployeeId id);

    /** Persists to the emp_rpn_ie-family table and clears the emergency-basis flag S-06's banner reads. */
    void applyRpnData(EmployeeId id, RpnDataDTO data);

    BulkRpnResultDTO retrieveBulkRpns(CompanyId id);

    void applyBulkRpnChanges(List<RpnDiffDTO> selected);

    List<RpnLogEntryDTO> getRpnLog(RpnLogFilterDTO filter);

    /** Also consumed by S-18/S-19/S-22 at the point of pay entry, not only S-16. */
    boolean isRpnOverdueForCurrentPeriod(CompanyId id);
}

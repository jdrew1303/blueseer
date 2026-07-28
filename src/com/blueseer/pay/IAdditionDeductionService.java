package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.DeductionLineDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.LineItemId;

import java.time.LocalDate;
import java.util.List;

/** §4.1 service backing S-07/S-08/S-07a. Each save replaces the full line set - the table is the source of truth, not a diff/patch. */
public interface IAdditionDeductionService {

    SaveResult saveAdditions(EmployeeId id, List<AdditionLineDTO> lines);

    SaveResult saveDeductions(EmployeeId id, List<DeductionLineDTO> lines);

    /** Shared by S-07/S-08's Description column autocomplete, scoped per-employer. */
    List<String> suggestDescriptions(CompanyId companyId, String partial);

    /** Backs S-07a's end-date utility for a single addition/deduction line. */
    void setAdditionDeductionEndDate(EmployeeId id, LineItemId lineId, LocalDate endDateOrNull);
}

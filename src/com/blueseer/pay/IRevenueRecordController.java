package com.blueseer.pay;

import com.blueseer.pay.RevenueRecordDtos.RecordComparisonRowDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.util.List;

/**
 * Controller backing S-35 Check Revenue Record - the roadmap explicitly
 * shares this single method with S-70 (Query Revenue Record) rather than
 * duplicating the comparison logic between the two screens.
 *
 * @param idOrNull {@code null} compares the employer-level record; a
 *                 specific {@link EmployeeId} compares that one employee's record
 */
public interface IRevenueRecordController {

    List<RecordComparisonRowDTO> compareRecord(EmployeeId idOrNull);
}

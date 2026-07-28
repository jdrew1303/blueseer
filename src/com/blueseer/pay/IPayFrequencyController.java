package com.blueseer.pay;

import com.blueseer.pay.PayFrequencyDtos.FrequencyChangePreviewDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

/** Controller backing S-64 Changing an Employee's Pay Frequency. */
public interface IPayFrequencyController {

    PayFrequency getCurrentFrequency(EmployeeId id);

    FrequencyChangePreviewDTO previewFrequencyChange(EmployeeId id, PayFrequency newFrequency);

    /** Backs {@code PayFrequencyChangeService}. */
    SaveResult applyFrequencyChange(EmployeeId id, PayFrequency newFrequency);
}

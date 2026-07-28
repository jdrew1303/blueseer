package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;

/** DTOs backing S-57 Illness Benefit Handling (C-22). */
public final class IllnessBenefitDtos {

    private IllnessBenefitDtos() {
    }

    /** {@code "NO_EMPLOYER_SICK_PAY"} \| {@code "EMPLOYER_PAYS_FULL_OR_PARTIAL_SALARY"} \| {@code "EMPLOYER_RECEIVES_MANDATE_REIMBURSEMENT"}. */
    public record IllnessBenefitDTO(
            EmployeeId employeeId,
            String employerPolicyVariant,
            BigDecimal employerPaidAmountThisPeriod,
            BigDecimal dspMandateReimbursementAmountOrNull) {
    }
}

package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.PersonalDetailsDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Assembles gross pay from the S-18/S-19 entry fields - the single source of
 * truth both screens route every gross-pay preview and save through, per
 * S-19's explicit "shared service, two views" requirement in the roadmap, so
 * a value entered in either screen for the same employee/period always
 * produces the identical figure.
 */
final class GrossPayAssembler {

    private static final BigDecimal FOUR = BigDecimal.valueOf(4);
    private static final BigDecimal THREE = BigDecimal.valueOf(3);
    private static final BigDecimal ONE_AND_A_HALF = new BigDecimal("1.5");
    private static final BigDecimal TWO = BigDecimal.valueOf(2);

    private GrossPayAssembler() {
    }

    /**
     * Employees don't carry an explicit pay-frequency field yet (§3.3 RPN is
     * deferred, and frequency would otherwise arrive with the RPN/registration
     * data) - inferred instead from which of S-05's mutually-exclusive
     * hourly-rate/fixed-pay fields is populated, matching how those two
     * fields are already presented as alternatives on the employee record.
     */
    static PayFrequency inferFrequency(PersonalDetailsDTO personal) {
        return inferFrequency(personal, null);
    }

    /**
     * As {@link #inferFrequency(PersonalDetailsDTO)}, but honouring S-64's
     * explicit per-employee override (see {@code PayrollStubStore
     * .employeeFrequencyOverride}) when one has been set - that override can
     * select {@link PayFrequency#FORTNIGHTLY}, which the hourly-rate/fixed-
     * pay heuristic alone can never produce.
     */
    static PayFrequency inferFrequency(PersonalDetailsDTO personal, PayFrequency overrideOrNull) {
        if (overrideOrNull != null) {
            return overrideOrNull;
        }
        return personal != null && personal.fixedPay() != null ? PayFrequency.MONTHLY : PayFrequency.WEEKLY;
    }

    static BigDecimal computeGrossPay(PayEntryDTO draft) {
        BigDecimal rate = nz(draft.hourlyRate());
        BigDecimal timeAndAThirdMultiplier = FOUR.divide(THREE, 10, RoundingMode.HALF_UP);
        BigDecimal totalHours = nz(draft.standardHours())
                .add(nz(draft.timeAndAThirdHours()).multiply(timeAndAThirdMultiplier))
                .add(nz(draft.timeAndAHalfHours()).multiply(ONE_AND_A_HALF))
                .add(nz(draft.doubleTimeHours()).multiply(TWO));
        BigDecimal hoursPay = rate.multiply(totalHours);
        return hoursPay.add(nz(draft.basicPay())).add(nz(draft.holidayPayAmount())).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

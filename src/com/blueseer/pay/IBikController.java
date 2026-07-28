package com.blueseer.pay;

import com.blueseer.pay.BikDtos.BikBenefitDTO;
import com.blueseer.pay.BikDtos.BikBenefitPreviewDTO;
import com.blueseer.pay.BikDtos.BikCarDTO;
import com.blueseer.pay.BikDtos.BikLoanDTO;
import com.blueseer.pay.BikDtos.BikPreviewDTO;
import com.blueseer.pay.BikDtos.BikVanDTO;
import com.blueseer.pay.BikDtos.BikVehicleDTO;

/** Controller backing S-53 BIK Cars &amp; Vans, S-54 BIK Preferential Loans, S-55 BIK Annual/One-Off Benefits. */
public interface IBikController {

    /** Backs {@code BikCarCalculator}/C-07. */
    BikPreviewDTO previewCarBik(BikCarDTO draft);

    /** Backs {@code BikVanCalculator}/C-08. */
    BikPreviewDTO previewVanBik(BikVanDTO draft);

    /** Persists the vehicle record and schedules its BIK as a recurring taxable addition. */
    SaveResult saveBikVehicleEntry(BikVehicleDTO data);

    /** Backs {@code BikLoanCalculator}/C-09. */
    BikPreviewDTO previewLoanBik(BikLoanDTO draft);

    SaveResult saveLoanEntry(BikLoanDTO data);

    /** Backs {@code BikBenefitCalculator}. */
    BikBenefitPreviewDTO previewBenefitBik(BikBenefitDTO draft);

    SaveResult saveBenefitEntry(BikBenefitDTO data);
}

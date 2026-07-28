package com.blueseer.pay;

import com.blueseer.pay.ShareRemunerationDtos.SettlementType;
import com.blueseer.pay.ShareRemunerationDtos.ShareVestingDTO;
import com.blueseer.pay.ShareRemunerationDtos.ShareVestingPreviewDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * C-16 Share-Based Remuneration, simplified: taxable value is the plain
 * shares-times-market-value notional pay amount, and the employer-PRSI
 * exemption test is C-16 Step 3's two-part check (share-settled AND shares
 * in the employing company or its parent). C-16's full 60-day
 * settlement-date/remittance-date scheduling logic is out of scope for this
 * pass - see {@link ShareRemunerationDtos.ShareVestingPreviewDTO}'s javadoc.
 */
final class ShareRemunerationCalculator {

    private ShareRemunerationCalculator() {
    }

    static ShareVestingPreviewDTO preview(ShareVestingDTO draft) {
        BigDecimal taxableValue = nz(draft.numberOfShares()).multiply(nz(draft.marketValuePerShare()))
                .setScale(2, RoundingMode.HALF_UP);
        boolean employerPrsiExempt = draft.settlementType() == SettlementType.SHARE_SETTLED
                && draft.sharesInEmployingCompanyOrParent();
        return new ShareVestingPreviewDTO(taxableValue, employerPrsiExempt, draft.vestingDate());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

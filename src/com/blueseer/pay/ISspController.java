package com.blueseer.pay;

import com.blueseer.pay.SspDtos.SspClaimDTO;
import com.blueseer.pay.SspDtos.SspPreviewDTO;

/** Controller backing S-56 Statutory Sick Pay Setup/Operation. */
public interface ISspController {

    /** Backs {@code SspEntitlementEngine}/C-12. */
    SspPreviewDTO previewSspEntitlement(SspClaimDTO draft);

    /** Persists the claim and feeds the result into the current or next pay entry as an addition line. */
    SaveResult recordSickLeave(SspClaimDTO draft);
}

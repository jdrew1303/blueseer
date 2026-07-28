package com.blueseer.pay;

import com.blueseer.pay.ShareRemunerationDtos.ShareVestingDTO;
import com.blueseer.pay.ShareRemunerationDtos.ShareVestingPreviewDTO;

/** Controller backing S-26 Share-Based Remuneration. */
public interface IShareRemunerationController {

    /** A lightweight preview call, not the full save. */
    ShareVestingPreviewDTO previewTaxableValue(ShareVestingDTO draft);

    SaveResult saveVestingEvent(ShareVestingDTO draft);
}

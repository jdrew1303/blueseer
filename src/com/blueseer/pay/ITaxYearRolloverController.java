package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.TaxYearRolloverDtos.TaxYearRolloverResult;
import com.blueseer.pay.TaxYearRolloverDtos.YearEndChecklistDTO;

/** Controller backing S-65 Start New Tax Year. */
public interface ITaxYearRolloverController {

    YearEndChecklistDTO getYearEndChecklist(CompanyId id, int closingTaxYear);

    /**
     * Backs {@code TaxYearRolloverService}; per roadmap §5, this does
     * <strong>not</strong> copy {@code emp_mstr} data - it only instantiates
     * the new year's rules context, so this call is expected to be fast
     * regardless of employee count.
     */
    TaxYearRolloverResult startNewTaxYear(CompanyId id, int newTaxYear);
}

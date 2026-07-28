package com.blueseer.pay;

import com.blueseer.pay.ty2026.Paye2026Rules;

import java.util.List;

/**
 * Every Track A no-arg screen constructor that needs a real {@link
 * IPayrollCalculationService} (S-18 onward) resolves it from here rather
 * than each building its own {@link TaxYearRulesRegistry}, so registering a
 * future {@code Paye2027Rules} only ever needs to happen in one place.
 */
final class PayrollEngineFactory {

    private PayrollEngineFactory() {
    }

    static IPayrollCalculationService defaultCalculationService() {
        return new PayrollCalculationService(new TaxYearRulesRegistry(List.of(new Paye2026Rules())));
    }

    /** Populates S-46/S-47's {@code cbTaxYear} - every tax year with a registered {@link ITaxYearRules} bean. */
    static List<Integer> registeredTaxYears() {
        return List.of(new Paye2026Rules().taxYear());
    }
}

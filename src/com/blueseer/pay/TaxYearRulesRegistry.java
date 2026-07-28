package com.blueseer.pay;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Default {@link ITaxYearRulesRegistry}: one {@link ITaxYearRules} bean per
 * tax year, keyed by {@link ITaxYearRules#taxYear()}. New years are added by
 * registering another concrete rules class (e.g. a future {@code Paye2027Rules}
 * in {@code com.blueseer.pay.ty2027}), never by mutating an existing year's
 * constants in place.
 */
public final class TaxYearRulesRegistry implements ITaxYearRulesRegistry {

    private final Map<Integer, ITaxYearRules> rulesByYear = new HashMap<>();

    public TaxYearRulesRegistry(List<ITaxYearRules> registeredRules) {
        for (ITaxYearRules rules : registeredRules) {
            rulesByYear.put(rules.taxYear(), rules);
        }
    }

    @Override
    public ITaxYearRules resolve(int taxYear) {
        ITaxYearRules rules = rulesByYear.get(taxYear);
        if (rules == null) {
            throw new IllegalStateException("No ITaxYearRules registered for tax year " + taxYear);
        }
        return rules;
    }
}

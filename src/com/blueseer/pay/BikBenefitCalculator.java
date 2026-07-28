package com.blueseer.pay;

import java.math.BigDecimal;

/**
 * Small Benefit Exemption (s.112B TCA 1997), per Revenue's own dedicated
 * page - [revenue.ie/en/employing-people/benefit-in-kind-for-employers/valuation-of-benefits/small-benefit-exemption.aspx](https://www.revenue.ie/en/employing-people/benefit-in-kind-for-employers/valuation-of-benefits/small-benefit-exemption.aspx)
 * (fetched directly): up to 5 non-cash benefits per tax year, combined value
 * capped at &euro;1,500, unchanged by Budget 2026. Unlike most of this
 * module's exemptions, exceeding the limit makes the <strong>whole</strong>
 * benefit taxable, not just the excess - "If a single benefit exceeds
 * &euro;1,500 in value, the full value of that benefit is subject to tax,"
 * and only the first five benefits in a year may qualify at all.
 *
 * <p>Referenced from the calc-engine spec (§C-19, §C-11) as "already covered
 * in C-05's preface," but no such preface exists in the spec as written -
 * C-05 is the unrelated ASC engine. Rather than silently omit the Small
 * Benefit Exemption or invent numbers, this class sources the two governing
 * figures directly from Revenue's own page, the same rigor standard the rest
 * of this package's engines hold to.
 */
final class BikBenefitCalculator {

    static final int MAX_BENEFITS_PER_YEAR = 5;
    static final BigDecimal MAX_COMBINED_VALUE_PER_YEAR = new BigDecimal("1500");

    private BikBenefitCalculator() {
    }

    record UsageState(int benefitsUsedThisYear, BigDecimal combinedValueUsedThisYear) {
    }

    record BenefitResult(BigDecimal remainingAnnualAllowance, boolean qualifiesForExemption, BigDecimal taxableAmount) {
    }

    static BenefitResult calculate(BigDecimal benefitAmount, UsageState usage) {
        BigDecimal remaining = MAX_COMBINED_VALUE_PER_YEAR.subtract(usage.combinedValueUsedThisYear()).max(BigDecimal.ZERO);
        boolean countExhausted = usage.benefitsUsedThisYear() >= MAX_BENEFITS_PER_YEAR;
        boolean qualifies = !countExhausted && benefitAmount.compareTo(remaining) <= 0;
        BigDecimal taxable = qualifies ? BigDecimal.ZERO : benefitAmount;
        return new BenefitResult(remaining, qualifies, taxable);
    }
}

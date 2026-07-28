package com.blueseer.pay;

/**
 * C-06 Tax-Year Rules Registry, per
 * {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md}. Given a
 * tax year, returns the registered {@link ITaxYearRules} bean for it -
 * resolved from a payslip's own {@code tax_year} column by callers, never
 * from {@code LocalDate.now()}, so prior-year payslips keep processing
 * against their own year's rules while a new tax year is concurrently live.
 */
public interface ITaxYearRulesRegistry {

    /**
     * @throws IllegalStateException if no {@link ITaxYearRules} is registered
     *                                for {@code taxYear} - no silent fallback
     *                                to a prior year's rules, per C-06's
     *                                resolution algorithm
     */
    ITaxYearRules resolve(int taxYear);
}

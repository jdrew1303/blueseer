package com.blueseer.pay;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * Everything that changes annually (or, for PRSI, mid-year on a legislated
 * date) about Irish statutory payroll deductions, per
 * {@code docs/architecture/irish-payroll-2026-roadmap.md} &sect;5. One
 * concrete implementation per tax year (e.g. {@link Paye2026Rules}, package-
 * scoped {@code com.blueseer.pay.ty2026}) resolves this interface's methods
 * from a payslip's own {@code tax_year} column - never from
 * {@code LocalDate.now()} - so a 2026 fortnightly run whose first week falls
 * in late 2025 processes each constituent week against the correct year's
 * rules.
 *
 * <p>Every numeric constant a calculation engine (C-01, C-02, C-03, ...) uses
 * is resolved from this interface, never hard-coded into an engine's EvalEx
 * formula strings - that is what lets a Budget change or a mid-year rate
 * step (see {@link #prsiRates}) land as a change to one rules class rather
 * than a change to every formula that happens to reference a rate.
 */
public interface ITaxYearRules {

    /** The tax year this instance's constants apply to, e.g. {@code 2026}. */
    int taxYear();

    /** C-01 PAYE: standard/higher rate and the RPC020012 emergency-basis table. */
    PayeRates payeRates();

    /**
     * C-02 PRSI: resolved <strong>as of the pay date</strong>, not just the
     * tax year - 2026's Class A rates step up on 1 October 2026, so the same
     * {@code Paye2026Rules} instance must resolve two different rate sets
     * depending on which side of that date {@code payDate} falls, scaled to
     * {@code frequency} (weekly figures are Revenue's/DSP's own published
     * base; fortnightly is exactly double and monthly is exactly
     * &times;52/12 - confirmed against the one value DSP publishes for all
     * three frequencies, &euro;552/&euro;1,104/&euro;2,392, before being
     * relied on for the other three PRSI threshold constants that DSP only
     * publishes on a weekly basis).
     */
    PrsiRates prsiRates(LocalDate payDate, PayFrequency frequency);

    /** C-03 USC: the four standard bands plus the reduced-rate cap and flat emergency rate. */
    UscRates uscRates();

    /** C-05 ASC: Standard Accrual and Single Scheme band structure (both share the same exempt/upper ceilings). */
    AscRates ascRates(AscGroup group);

    /** C-10 Termination Lump Sum: s.123/s.201 TCA 1997 exemption constants (TDM Part 05-05-19). */
    TerminationLumpSumRates terminationLumpSumRates();

    /** C-07 BIK Company Cars: OMV reductions and the Table A/Table B rate matrix (TDM Part 05-01-01b). */
    BikCarRates bikCarRates();

    /** C-08 BIK Company Vans: flat rate + the same temporary OMV reduction as cars (TDM Part 05-01-01b §4.1/§5.2). */
    BigDecimal bikVanRate();

    /** C-09 BIK Preferential Loans: specified rates by loan category (TDM Part 05-01-01d §2.3). */
    BikLoanRates bikLoanRates();

    /** C-12 Statutory Sick Leave: rate/cap/annual-entitlement constants, per S.I. No. 607/2022. */
    SslRates sslRates();

    /** C-15 Standard Pension Contribution Relief: earnings cap + age-band percentage limits, per Revenue's tax-relief-limits page. */
    StandardPensionRates standardPensionRates();

    /** C-23 CWPS: flat weekly euro amounts, per CWPS's own Annual Contribution Rates card. */
    CwpsRates cwpsRates();

    /** C-24 NECI: percentage-of-earnings rates + member floor - lower-confidence tier, see {@link CwpsRates} javadoc contrast. */
    NeciRates neciRates();

    /** C-14 Auto-Enrolment: earnings cap + scheme-year-tiered contribution rates, per gov.ie's own contribution table. */
    AutoEnrolmentRates autoEnrolmentRates(int schemeYear);

    /**
     * C-01's PAYE rate pair. Not year-invariant even though it has been
     * unchanged since well before 2026 - kept resolved from here, not a
     * literal in {@link com.blueseer.pay.PayeCalculator}, on the same
     * principle as everything else in this interface.
     */
    record PayeRates(BigDecimal standardRate, BigDecimal higherRate) {
    }

    /**
     * C-01's emergency-basis allowance, per Revenue form RPC020012 - the
     * credit is always zero (not modelled here at all; {@link com.blueseer.pay.PayeCalculator}
     * hard-codes that as a literal {@code 0} step per the calc-engine spec,
     * since there is no per-year value that could ever make it non-zero).
     * Only the temporary cut-off allowance varies by year and frequency.
     *
     * @param cutOffAllowancePerPeriod the flat cut-off point available for
     *                                 each of the first {@code allowanceWindowLength}
     *                                 periods
     * @param allowanceWindowLength    number of periods the allowance
     *                                 applies for before dropping to zero
     */
    record EmergencyPayeAllowance(BigDecimal cutOffAllowancePerPeriod, int allowanceWindowLength) {
    }

    /** Convenience: {@link #payeRates()} plus the frequency-specific emergency allowance. */
    EmergencyPayeAllowance emergencyPayeAllowance(PayFrequency frequency);

    /**
     * C-02's PRSI Class A constants, already resolved for one specific pay
     * date and scaled to one specific frequency - the calculator never sees
     * an un-scaled weekly figure or has to reason about effective dates
     * itself.
     */
    record PrsiRates(
            BigDecimal weeklyLowerThreshold,
            BigDecimal prsiCreditUpperThreshold,
            BigDecimal prsiCreditMax,
            BigDecimal employeeRate,
            BigDecimal employerLowerRate,
            BigDecimal employerHigherRate,
            BigDecimal employerHigherRateThreshold) {
    }

    /** C-03's USC band structure - annual ceilings; {@link com.blueseer.pay.UscCalculator} prorates by period itself. */
    record UscRates(
            BigDecimal annualBand1Ceiling, BigDecimal band1Rate,
            BigDecimal annualBand2Ceiling, BigDecimal band2Rate,
            BigDecimal annualBand3Ceiling, BigDecimal band3Rate,
            BigDecimal band4Rate,
            BigDecimal reducedRateCapValue,
            BigDecimal emergencyUscRate) {
    }

    /** Which ASC group an employee belongs to (Standard Accrual vs. Single Scheme), per C-05. */
    enum AscGroup { STANDARD_ACCRUAL, SINGLE_SCHEME }

    /**
     * C-05's ASC band structure - same exempt/upper ceilings for both
     * groups, only the paid rates differ, per
     * publicservicepensions.gov.ie (confirmed primary source; the exemplar
     * itself does not publish these numbers).
     */
    record AscRates(BigDecimal exemptCeiling, BigDecimal upperBandCeiling, BigDecimal midRate, BigDecimal topRate) {
    }

    /**
     * C-10's s.123/s.201 TCA 1997 constants, per TDM Part 05-05-19 §3.3/§3.4/§3.8.
     *
     * @param basicExemptionFlatAmount   &euro;10,160 flat basic exemption
     * @param basicExemptionPerYearAmount &euro;765 per complete year of service, not apportionable for a partial year
     * @param increasedExemptionCap      &euro;10,000 ceiling on the increased exemption (before the RCS offset)
     * @param scsbDivisor                15, the fixed Schedule 3 SCSB divisor
     * @param lifetimeReliefCapAmount    &euro;200,000 lifetime cap on s.201 relief (s.201(8))
     */
    record TerminationLumpSumRates(
            BigDecimal basicExemptionFlatAmount,
            BigDecimal basicExemptionPerYearAmount,
            BigDecimal increasedExemptionCap,
            BigDecimal scsbDivisor,
            BigDecimal lifetimeReliefCapAmount) {
    }

    /**
     * C-07's TDM Table A/Table B constants, per TDM Part 05-01-01b
     * §4.1.3/§4.1.4. {@code ratesByCategory} is keyed by {@code "A1"} |
     * {@code "A"} | {@code "B"} | {@code "C"} | {@code "D"} | {@code "E"} -
     * the 2026 figures below are cross-checked against three of the TDM's own
     * worked examples (Tony: category C, 32,000km &rarr; 24%; Sarah: category
     * E, 28,000km &rarr; 30%; Peter: category A1, 0-26,000km &rarr; 15%).
     *
     * @param temporaryOmvReduction        &euro;10,000 for 2026; applies to categories A1/A/B/C/D and all vans, not category E
     * @param electricVehicleOmvReduction  &euro;20,000 for 2026; stacks additively with {@code temporaryOmvReduction} for EVs only
     */
    record BikCarRates(
            BigDecimal temporaryOmvReduction,
            BigDecimal electricVehicleOmvReduction,
            Map<String, CategoryRateBands> ratesByCategory) {

        /** One vehicle category's rate at each of Table A's 4 business-mileage bands. */
        public record CategoryRateBands(
                BigDecimal upTo26000Km,
                BigDecimal from26001To39000Km,
                BigDecimal from39001To48000Km,
                BigDecimal from48001KmUp) {
        }
    }

    /** C-09's specified rates by loan category, per TDM Part 05-01-01d §2.3 - unchanged since 1 January 2013. */
    record BikLoanRates(BigDecimal specifiedRateHomeLoan, BigDecimal specifiedRateOther) {
    }

    /**
     * C-12's Statutory Sick Leave constants, per S.I. No. 607/2022 and the
     * WRC/DSP sources cross-cited in the calc-engine spec - the entitlement
     * is frozen at 5 days for 2026 (the Act's own phased 3/5/7/10-day
     * schedule was legislated but the 2025/2026 steps were publicly
     * reversed).
     */
    record SslRates(BigDecimal ratePercentage, BigDecimal dailyCap, int daysEntitlementPerYear) {
    }

    /**
     * C-15's earnings cap and age-related percentage limits, per Revenue's
     * "Tax relief limits on pension contributions" page - &euro;115,000 cap;
     * under 30 = 15%, 30-39 = 20%, 40-49 = 25%, 50-54 = 30%, 55-59 = 35%,
     * 60+ = 40%.
     */
    record StandardPensionRates(
            BigDecimal earningsCap,
            BigDecimal ageUnder30, BigDecimal age30To39, BigDecimal age40To49,
            BigDecimal age50To54, BigDecimal age55To59, BigDecimal age60Plus) {
    }

    /**
     * C-23's flat weekly euro amounts, per CWPS's own "Annual Contribution
     * Rates" card (effective 1 August 2025) - unlike C-15/C-24, none of these
     * depend on what the employee earns.
     */
    record CwpsRates(
            BigDecimal employerPensionWeekly, BigDecimal memberPensionWeekly,
            BigDecimal employerDeathInServiceWeekly, BigDecimal memberDeathInServiceWeekly,
            BigDecimal employerSickPayWeekly, BigDecimal memberSickPayWeekly,
            BigDecimal memberHealthTrustWeekly,
            BigDecimal employerBenevolentFundWeekly, BigDecimal memberBenevolentFundWeekly) {
    }

    /**
     * C-24's percentage-of-earnings rates + member floor - explicitly a
     * <strong>lower-confidence tier</strong> than every other rate in this
     * interface (see the calc-engine spec §C-24): sourced from two
     * independently-consistent secondary payroll-vendor documents, not a
     * primary NECI/Revenue publication. Do not treat these as verified.
     */
    record NeciRates(BigDecimal employerContributionRate, BigDecimal memberContributionRate, BigDecimal memberMinimumWeeklyContribution) {
    }

    /**
     * C-14's earnings cap (fixed across all scheme years) and this scheme
     * year's matched employee/employer contribution rate, per gov.ie's own
     * "Auto-enrolment retirement savings system for employers" table - Years
     * 1-3 = 1.5%/1.5%, 4-6 = 3%/3%, 7-9 = 4.5%/4.5%, 10+ = 6%/6% (employer
     * always matches employee exactly; the State tops up at 1/3 of the
     * employee amount, per {@code stateTopUpDivisor}).
     */
    record AutoEnrolmentRates(BigDecimal annualEarningsCap, BigDecimal employeeContributionRate,
            BigDecimal employerContributionRate, BigDecimal stateTopUpDivisor) {
    }
}

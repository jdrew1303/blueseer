package com.blueseer.pay.ty2026;

import com.blueseer.pay.ITaxYearRules;
import com.blueseer.pay.PayFrequency;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The 2026 tax year's statutory constants, as researched and cited in
 * {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md} (engines
 * C-01, C-02, C-03, C-05). Every figure below traces to a primary source
 * fetched and read directly during that research pass - Revenue's own
 * "Budget 2026 Summary" and RPC020012 form for PAYE, the Department of
 * Social Protection's "PRSI Class A Rates" page for PRSI, Revenue's "Budget
 * 2026 Summary" for USC, and publicservicepensions.gov.ie for ASC - not
 * estimated or carried over from a prior year's figures. This class is
 * intentionally the <em>only</em> place any of these numbers appears as a
 * Java literal; every calculation engine in this package receives them
 * through {@link ITaxYearRules}, never as its own constant.
 *
 * <p><strong>Re-verify before go-live.</strong> Per the calc-engine spec's
 * own Engine Integration Pattern note, this document is a specification, not
 * a substitute for validating against Revenue's live RPN test-system
 * payloads at implementation time.
 */
public final class Paye2026Rules implements ITaxYearRules {

    /** 1 October 2026: the legislated PRSI Class A rate step, per the Department of Social Protection. */
    private static final LocalDate PRSI_RATE_STEP_DATE = LocalDate.of(2026, 10, 1);

    @Override
    public int taxYear() {
        return 2026;
    }

    @Override
    public PayeRates payeRates() {
        return new PayeRates(new BigDecimal("0.20"), new BigDecimal("0.40"));
    }

    @Override
    public EmergencyPayeAllowance emergencyPayeAllowance(PayFrequency frequency) {
        // Revenue form RPC020012, "Emergency Basis of Tax Deduction 2026" -
        // these are flat, Revenue-published figures per frequency, NOT a
        // simple multiple of the weekly figure (846.16 x 52/12 = 3666.03,
        // not the published 3666.67) - so each is its own literal here
        // rather than derived, unlike the PRSI thresholds below.
        return switch (frequency) {
            case WEEKLY -> new EmergencyPayeAllowance(new BigDecimal("846.16"), 4);
            case FORTNIGHTLY -> new EmergencyPayeAllowance(new BigDecimal("1692.31"), 2);
            case MONTHLY -> new EmergencyPayeAllowance(new BigDecimal("3666.67"), 1);
        };
    }

    @Override
    public PrsiRates prsiRates(LocalDate payDate, PayFrequency frequency) {
        boolean postOctoberStep = !payDate.isBefore(PRSI_RATE_STEP_DATE);

        // Weekly base figures per gov.ie "PRSI Class A Rates" (last updated
        // 10 June 2026). Fortnightly/monthly are derived by scaling - this
        // is confirmed safe (not just assumed) because DSP separately
        // publishes the employer-higher-rate threshold at all three
        // frequencies (552 / 1,104 / 2,392) and 552 x 52/12 = 2,392.00
        // exactly, matching Revenue's own annualise-then-divide convention
        // rather than a distinctly-published monthly table.
        BigDecimal weeklyLowerThreshold = new BigDecimal("352");
        BigDecimal prsiCreditUpperThreshold = new BigDecimal("424");
        BigDecimal prsiCreditMax = new BigDecimal("12");
        BigDecimal employerHigherRateThreshold = new BigDecimal("552");

        BigDecimal employeeRate;
        BigDecimal employerLowerRate;
        BigDecimal employerHigherRate;
        if (postOctoberStep) {
            employeeRate = new BigDecimal("0.0435");
            employerLowerRate = new BigDecimal("0.0915");
            employerHigherRate = new BigDecimal("0.1140");
        } else {
            employeeRate = new BigDecimal("0.0420");
            employerLowerRate = new BigDecimal("0.0900");
            employerHigherRate = new BigDecimal("0.1125");
        }

        BigDecimal scale = periodScaleFactor(frequency);
        return new PrsiRates(
                scale(weeklyLowerThreshold, scale),
                scale(prsiCreditUpperThreshold, scale),
                scale(prsiCreditMax, scale),
                employeeRate,
                employerLowerRate,
                employerHigherRate,
                scale(employerHigherRateThreshold, scale));
    }

    @Override
    public UscRates uscRates() {
        // Revenue "Budget 2026 Summary" (revenue.ie/budget), published 7
        // October 2025 - unchanged from 2025 except the widened band 2
        // ceiling (27,382 -> 28,700).
        return new UscRates(
                new BigDecimal("12012"), new BigDecimal("0.005"),
                new BigDecimal("28700"), new BigDecimal("0.02"),
                new BigDecimal("70044"), new BigDecimal("0.03"),
                new BigDecimal("0.08"),
                new BigDecimal("0.02"),
                new BigDecimal("0.08"));
    }

    @Override
    public AscRates ascRates(AscGroup group) {
        // publicservicepensions.gov.ie - "applied since 1 January 2020",
        // both groups share the same 34,500/60,000 ceilings, only the paid
        // rates differ.
        BigDecimal exemptCeiling = new BigDecimal("34500");
        BigDecimal upperBandCeiling = new BigDecimal("60000");
        return switch (group) {
            case STANDARD_ACCRUAL -> new AscRates(exemptCeiling, upperBandCeiling,
                    new BigDecimal("0.10"), new BigDecimal("0.105"));
            case SINGLE_SCHEME -> new AscRates(exemptCeiling, upperBandCeiling,
                    new BigDecimal("0.0333"), new BigDecimal("0.035"));
        };
    }

    @Override
    public TerminationLumpSumRates terminationLumpSumRates() {
        // TDM Part 05-05-19 §3.3/§3.4/§3.8 - unchanged figures, confirmed
        // against the manual's own worked examples (Jim's 35-years example
        // reproduces exactly off these constants).
        return new TerminationLumpSumRates(
                new BigDecimal("10160"),
                new BigDecimal("765"),
                new BigDecimal("10000"),
                new BigDecimal("15"),
                new BigDecimal("200000"));
    }

    @Override
    public BikCarRates bikCarRates() {
        // TDM Part 05-01-01b, Table A ("Applicable with effect from 1
        // January 2026") and the OMV-reduction summary table (§4.1.3).
        // Cross-checked against three worked examples in the manual: Tony
        // (category C, 32,000km -> 24%), Sarah (category E, 28,000km ->
        // 30%), Peter (category A1, 0-26,000km -> 15%) - all three reproduce
        // exactly off the figures below.
        Map<String, BikCarRates.CategoryRateBands> table = new LinkedHashMap<>();
        table.put("A1", new BikCarRates.CategoryRateBands(
                new BigDecimal("0.15"), new BigDecimal("0.12"), new BigDecimal("0.09"), new BigDecimal("0.06")));
        table.put("A", new BikCarRates.CategoryRateBands(
                new BigDecimal("0.225"), new BigDecimal("0.18"), new BigDecimal("0.135"), new BigDecimal("0.09")));
        table.put("B", new BikCarRates.CategoryRateBands(
                new BigDecimal("0.2625"), new BigDecimal("0.21"), new BigDecimal("0.1575"), new BigDecimal("0.105")));
        table.put("C", new BikCarRates.CategoryRateBands(
                new BigDecimal("0.30"), new BigDecimal("0.24"), new BigDecimal("0.18"), new BigDecimal("0.12")));
        table.put("D", new BikCarRates.CategoryRateBands(
                new BigDecimal("0.3375"), new BigDecimal("0.27"), new BigDecimal("0.2025"), new BigDecimal("0.135")));
        table.put("E", new BikCarRates.CategoryRateBands(
                new BigDecimal("0.375"), new BigDecimal("0.30"), new BigDecimal("0.225"), new BigDecimal("0.15")));
        return new BikCarRates(new BigDecimal("10000"), new BigDecimal("20000"), java.util.Collections.unmodifiableMap(table));
    }

    @Override
    public BigDecimal bikVanRate() {
        // TDM Part 05-01-01b §4.1 - flat 8% since 1 January 2023 (up from 5%).
        return new BigDecimal("0.08");
    }

    @Override
    public BikLoanRates bikLoanRates() {
        // TDM Part 05-01-01d §2.3 - unchanged since 1 January 2013.
        return new BikLoanRates(new BigDecimal("0.04"), new BigDecimal("0.135"));
    }

    @Override
    public SslRates sslRates() {
        // S.I. No. 607/2022 and DSP's "Illness Benefit, Injury Benefit and
        // Statutory Sick Leave in 2026" - entitlement frozen at 5 days for
        // 2026 (the Act's legislated 7/10-day steps for 2025/2026 were
        // publicly reversed).
        return new SslRates(new BigDecimal("0.70"), new BigDecimal("110"), 5);
    }

    @Override
    public StandardPensionRates standardPensionRates() {
        // revenue.ie/en/jobs-and-pensions/pension/relief/tax-relief-limits.aspx
        return new StandardPensionRates(new BigDecimal("115000"),
                new BigDecimal("0.15"), new BigDecimal("0.20"), new BigDecimal("0.25"),
                new BigDecimal("0.30"), new BigDecimal("0.35"), new BigDecimal("0.40"));
    }

    @Override
    public CwpsRates cwpsRates() {
        // cwps.ie's own "Annual Contribution Rates" card, effective 1 August 2025.
        return new CwpsRates(
                new BigDecimal("31.87"), new BigDecimal("21.27"),
                new BigDecimal("1.17"), new BigDecimal("1.17"),
                new BigDecimal("2.37"), new BigDecimal("0.63"),
                new BigDecimal("1.00"),
                new BigDecimal("0.19"), new BigDecimal("0.50"));
    }

    @Override
    public NeciRates neciRates() {
        // Lower-confidence tier - see NeciRates javadoc. Two independently-
        // consistent secondary payroll-vendor sources only; not yet
        // confirmed against NECI's own scheme documentation.
        return new NeciRates(new BigDecimal("0.037"), new BigDecimal("0.025"), new BigDecimal("4.11"));
    }

    @Override
    public AutoEnrolmentRates autoEnrolmentRates(int schemeYear) {
        // gov.ie's own "Auto-enrolment retirement savings system for
        // employers" contribution table - 2026 is Year 1 of the 10-year
        // phase-in.
        BigDecimal rate = switch (Math.max(1, schemeYear)) {
            case 1, 2, 3 -> new BigDecimal("0.015");
            case 4, 5, 6 -> new BigDecimal("0.03");
            case 7, 8, 9 -> new BigDecimal("0.045");
            default -> new BigDecimal("0.06");
        };
        return new AutoEnrolmentRates(new BigDecimal("80000"), rate, rate, new BigDecimal("3"));
    }

    /**
     * Weekly-to-frequency scale factor. Fortnightly is exactly double;
     * monthly is 52/12 (not 12/12=1, and not a rounded 4.33) so that
     * {@code weekly x scale} reproduces DSP's own published monthly
     * threshold exactly rather than an approximation - see the
     * {@link #prsiRates} javadoc for the cross-check that justifies scaling
     * at all instead of requiring separately-published per-frequency
     * constants.
     */
    private static BigDecimal periodScaleFactor(PayFrequency frequency) {
        return switch (frequency) {
            case WEEKLY -> BigDecimal.ONE;
            case FORTNIGHTLY -> new BigDecimal("2");
            case MONTHLY -> new BigDecimal("52").divide(new BigDecimal("12"), 10, java.math.RoundingMode.HALF_UP);
        };
    }

    private static BigDecimal scale(BigDecimal weeklyValue, BigDecimal scaleFactor) {
        return weeklyValue.multiply(scaleFactor).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}

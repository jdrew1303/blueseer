package com.blueseer.pay;

/**
 * The three pay frequencies the roadmap's New Company Wizard (S-01) actually
 * offers: Weekly+Monthly or Fortnightly+Monthly (never Weekly+Fortnightly
 * together on one employer). Four-weekly and twice-monthly appear in
 * Revenue's own emergency-basis tables (RPC020012) but are not a supported
 * combination in this module, so are deliberately not modelled here.
 */
public enum PayFrequency {
    WEEKLY(52),
    FORTNIGHTLY(26),
    MONTHLY(12);

    private final int periodsPerYear;

    PayFrequency(int periodsPerYear) {
        this.periodsPerYear = periodsPerYear;
    }

    public int periodsPerYear() {
        return periodsPerYear;
    }
}

package com.blueseer.pay;

import java.math.BigDecimal;

/** DTOs backing S-51/S-52 Post-Cessation Payment. */
public final class PostCessationDtos {

    private PostCessationDtos() {
    }

    /**
     * Differentiates S-51 from S-52: which {@link ITaxYearRules} the
     * Controller resolves, not a second interface - per the roadmap's own
     * framing of this pair as "the concrete proof-point" for the multi-year
     * architecture.
     */
    public record TaxYearScope(boolean current, Integer historicalYearOrNull) {

        public static TaxYearScope forCurrentYear() {
            return new TaxYearScope(true, null);
        }

        public static TaxYearScope forHistoricalYear(int year) {
            return new TaxYearScope(false, year);
        }

        public int resolveYear(int currentTaxYear) {
            return current ? currentTaxYear : historicalYearOrNull;
        }
    }

    public record PostCessationResult(boolean success, BigDecimal paye, BigDecimal prsi, BigDecimal usc, BigDecimal net, String message) {
    }
}

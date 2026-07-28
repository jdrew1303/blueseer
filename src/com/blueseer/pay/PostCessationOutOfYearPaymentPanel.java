package com.blueseer.pay;

/**
 * S-52 Post-Cessation Payment (Out of Year) - the same {@link
 * PostCessationPaymentPanel} as S-51, only with {@code outOfYear=true} (adds
 * {@code cbTaxYear}, resolves a historical {@link
 * com.blueseer.pay.PostCessationDtos.TaxYearScope} instead of the current
 * one). A separate class only so the menu system has a distinct no-arg
 * constructor to launch, per the roadmap's own note that this pair is "the
 * concrete proof-point" for the multi-year architecture: the UI shape never
 * duplicates, only the resolved tax year does.
 */
public final class PostCessationOutOfYearPaymentPanel extends PostCessationPaymentPanel {

    public PostCessationOutOfYearPaymentPanel() {
        super(new InMemoryPostCessationController(PayrollStubStore.shared(), PayrollEngineFactory.defaultCalculationService()), true);
    }
}

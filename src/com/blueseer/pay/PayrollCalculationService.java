package com.blueseer.pay;

import java.time.LocalDate;

/**
 * Real {@link IPayrollCalculationService} implementation: resolves each
 * call's {@link ITaxYearRules} from the injected {@link ITaxYearRulesRegistry}
 * and delegates to the matching C-01...C-05 calculator. Contains no
 * statutory arithmetic of its own - every formula lives in the calculator
 * it delegates to, per the calc-engine spec.
 */
public final class PayrollCalculationService implements IPayrollCalculationService {

    private final ITaxYearRulesRegistry registry;

    public PayrollCalculationService(ITaxYearRulesRegistry registry) {
        this.registry = registry;
    }

    @Override
    public EngineResult calculatePaye(int taxYear, PayFrequency frequency, PayeCalculator.PayeContext ctx) {
        ITaxYearRules rules = registry.resolve(taxYear);
        return PayeCalculator.calculate(ctx, rules.payeRates(), rules.emergencyPayeAllowance(frequency));
    }

    @Override
    public PrsiCalculator.PrsiResult calculatePrsi(int taxYear, LocalDate payDate, PayFrequency frequency, PrsiCalculator.PrsiContext ctx) {
        ITaxYearRules rules = registry.resolve(taxYear);
        return PrsiCalculator.calculate(ctx, rules.prsiRates(payDate, frequency));
    }

    @Override
    public EngineResult calculateUsc(int taxYear, UscCalculator.UscContext ctx) {
        ITaxYearRules rules = registry.resolve(taxYear);
        return UscCalculator.calculate(ctx, rules.uscRates());
    }

    @Override
    public EngineResult calculateLpt(LptCalculator.LptContext ctx) {
        return LptCalculator.calculate(ctx);
    }

    @Override
    public EngineResult calculateAsc(int taxYear, ITaxYearRules.AscGroup ascGroup, AscCalculator.AscContext ctx) {
        ITaxYearRules rules = registry.resolve(taxYear);
        return AscCalculator.calculate(ctx, rules.ascRates(ascGroup));
    }
}

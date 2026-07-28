package com.blueseer.pay;

import java.time.LocalDate;

/**
 * Facade over C-01...C-06, per
 * {@code docs/architecture/irish-payroll-2026-roadmap.md} &sect;4.1 - one
 * call per payslip line item, so the Payslip Workings screen (S-23) can
 * render each statutory deduction's own {@link EngineResult} audit trail
 * independently rather than one collapsed aggregate. Every method resolves
 * its {@link ITaxYearRules} from {@code taxYear}, never from
 * {@code LocalDate.now()}, per the roadmap's multi-tax-year design (&sect;5).
 *
 * <p>Screens depend on this interface, injected via constructor/setter, and
 * are built against a stub implementation until Track B's real {@link
 * PayrollCalculationService} is wired in - per principle 1, "business
 * objects are injected into views, not owned by them."
 */
public interface IPayrollCalculationService {

    /** C-01 PAYE. */
    EngineResult calculatePaye(int taxYear, PayFrequency frequency, PayeCalculator.PayeContext ctx);

    /** C-02 PRSI - employee and employer lines are both computed and returned. */
    PrsiCalculator.PrsiResult calculatePrsi(int taxYear, LocalDate payDate, PayFrequency frequency, PrsiCalculator.PrsiContext ctx);

    /** C-03 USC. */
    EngineResult calculateUsc(int taxYear, UscCalculator.UscContext ctx);

    /**
     * C-04 LPT. Not year-rate-dependent (RPN-supplied), but sequenced last
     * among the money-affecting engines when {@code lptDeductionMethod ==
     * PERCENTAGE_OF_NET}, since {@link LptCalculator.LptContext#netPayThisPeriod()}
     * is only available once PAYE/PRSI/USC have already run for the period.
     */
    EngineResult calculateLpt(LptCalculator.LptContext ctx);

    /** C-05 ASC - independent of PAYE/PRSI/USC/LPT, may run any time after PRSI per C-06. */
    EngineResult calculateAsc(int taxYear, ITaxYearRules.AscGroup ascGroup, AscCalculator.AscContext ctx);
}

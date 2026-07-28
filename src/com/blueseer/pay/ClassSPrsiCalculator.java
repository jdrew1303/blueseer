package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * C-13 Directors PRSI (Class S) - proprietary directors pay a flat employee
 * PRSI rate on all reckonable income with no employer contribution at all,
 * unlike Class A's threshold/credit-tapered structure in {@link
 * PrsiCalculator}. The 4% rate is DSP's own published flat Class S rate -
 * unlike Class A it does not step or band, so (unlike {@link
 * ITaxYearRules#prsiRates}) there is no per-tax-year rate table to resolve
 * this from; it is simply hard-coded here, same as PAYE's emergency-credit
 * "always zero" step is hard-coded in {@link PayeCalculator}.
 *
 * <p>This module does not yet distinguish proprietary from non-proprietary
 * directors (that would need a shareholding-percentage field the employee
 * record doesn't carry yet, per S-25's spec) - every employee flagged {@code
 * director == true} on their Personal Details tab is treated as proprietary
 * and routed here.
 */
final class ClassSPrsiCalculator {

    private static final String ENGINE = "C-13 PRSI (Class S)";
    private static final BigDecimal CLASS_S_RATE = new BigDecimal("0.04");

    private ClassSPrsiCalculator() {
    }

    record ClassSResult(EngineResult employee, EngineResult employer) {
    }

    static ClassSResult calculate(BigDecimal reckonablePay) {
        Map<String, Object> initial = new HashMap<>();
        initial.put("reckonablePay", reckonablePay);
        initial.put("classSRate", CLASS_S_RATE);

        EvalExStepRunner employeeRunner = new EvalExStepRunner(ENGINE, initial);
        employeeRunner.finalStep("Class S employee PRSI (flat rate, no thresholds)", "reckonablePay * classSRate");

        EvalExStepRunner employerRunner = new EvalExStepRunner(ENGINE, initial);
        employerRunner.finalStep("Class S employer PRSI (none payable)", "0");

        return new ClassSResult(employeeRunner.result(), employerRunner.result());
    }
}

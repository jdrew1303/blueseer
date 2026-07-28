package com.blueseer.pay;

import java.math.BigDecimal;

/**
 * One labelled step of a statutory calculation engine (C-01 PAYE, C-02 PRSI,
 * C-03 USC, etc., per {@code docs/architecture/irish-payroll-2026-calc-engines-spec.md}),
 * as literally evaluated by EvalEx. Every engine in this package appends one
 * of these per step to its result, in evaluation order, rather than only
 * returning a final number - this list <em>is</em> the audit trail the
 * Payslip Workings screen (S-23) renders, not a debugging aid layered on
 * afterwards. The engine name, step label, and formula string here should be
 * exactly what a reviewer reading the calc-engine spec would expect to see
 * for that step, so the two stay traceable to each other without a mapping
 * table.
 *
 * @param engineName   the engine identifier from the calc-engine spec, e.g.
 *                     {@code "C-01 PAYE"}, so steps from different engines
 *                     interleaved on one payslip line stay distinguishable
 * @param stepLabel    the human-readable step label from the spec, e.g.
 *                     {@code "Cumulative gross pay to date"}
 * @param formula      the literal EvalEx expression string evaluated for
 *                     this step, e.g. {@code "cumulativeGrossPayPriorToThisPeriod + grossPayThisPeriod"}
 * @param resultValue  the value that formula evaluated to, given the
 *                     context map in effect at that step
 */
public record CalculationStep(String engineName, String stepLabel, String formula, BigDecimal resultValue) {
}

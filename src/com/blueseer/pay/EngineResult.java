package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.List;

/**
 * The output of any calculation engine in this package: the final payslip
 * line value, plus the ordered {@link CalculationStep} trail that produced
 * it. {@code amount} is always {@code steps.get(steps.size() - 1).resultValue()}
 * rounded to 2 decimal places for currency display - kept as a separate
 * field rather than making callers dig the last step out of the list
 * themselves.
 */
public record EngineResult(BigDecimal amount, List<CalculationStep> steps) {
}

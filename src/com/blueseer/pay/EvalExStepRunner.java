package com.blueseer.pay;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.EvaluationException;
import com.ezylang.evalex.config.ExpressionConfiguration;
import com.ezylang.evalex.parser.ParseException;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared EvalEx plumbing for every calculation engine in this package,
 * so C-01/C-02/C-03/... all evaluate steps and build their
 * {@link CalculationStep} audit trail identically rather than each engine
 * reimplementing "call EvalEx, catch its checked exceptions, wrap the
 * result." The context map accumulates across steps within one call - a
 * step that stores its result under {@code "cumulativeGrossToDate"} makes
 * that key available to every later step in the same run, exactly matching
 * how the calc-engine spec describes each step as building on the last.
 *
 * <p>Precision: 34-digit {@link MathContext} (comparable to IEEE 754
 * decimal128) with {@link RoundingMode#HALF_UP}, applied uniformly across
 * every intermediate step - money is never rounded to 2 decimal places
 * until {@link EngineResult#amount()} is read, so a chain of percentage
 * calculations (PRSI credit tapering, USC banding) does not accumulate
 * rounding error step over step the way premature 2-decimal-place rounding
 * would.
 */
final class EvalExStepRunner {

    private static final ExpressionConfiguration CONFIG = ExpressionConfiguration.builder()
            .mathContext(new MathContext(34, RoundingMode.HALF_UP))
            .build();

    private final String engineName;
    private final Map<String, Object> context = new HashMap<>();
    private final List<CalculationStep> steps = new ArrayList<>();

    EvalExStepRunner(String engineName, Map<String, Object> initialContext) {
        this.engineName = engineName;
        this.context.putAll(initialContext);
    }

    /**
     * Evaluates {@code formula} against the accumulated context, stores the
     * result under {@code storeAs} for subsequent steps to reference, and
     * appends a {@link CalculationStep} labelled {@code stepLabel}.
     *
     * @return the step's result, so callers can also use it immediately in
     *         Java-side branching (e.g. deciding which formula to run next)
     *         without re-reading it back out of the context map
     */
    BigDecimal step(String stepLabel, String formula, String storeAs) {
        BigDecimal result = evaluate(formula);
        context.put(storeAs, result);
        steps.add(new CalculationStep(engineName, stepLabel, formula, result));
        return result;
    }

    /**
     * As {@link #step}, but for a step whose result is not referenced by
     * name in any later formula (typically the engine's own final line) -
     * still recorded in the audit trail, just not stored back into the
     * context map under a key.
     */
    BigDecimal finalStep(String stepLabel, String formula) {
        BigDecimal result = evaluate(formula);
        steps.add(new CalculationStep(engineName, stepLabel, formula, result));
        return result;
    }

    /** Makes an already-computed value available to later formulas without evaluating a new expression for it. */
    void put(String key, Object value) {
        context.put(key, value);
    }

    EngineResult result() {
        BigDecimal amount = steps.isEmpty()
                ? BigDecimal.ZERO
                : steps.get(steps.size() - 1).resultValue().setScale(2, RoundingMode.HALF_UP);
        return new EngineResult(amount, List.copyOf(steps));
    }

    private BigDecimal evaluate(String formula) {
        try {
            return new Expression(formula, CONFIG).withValues(context).evaluate().getNumberValue();
        } catch (EvaluationException | ParseException e) {
            throw new IllegalStateException(
                    "EvalEx failed for engine=" + engineName + " formula=[" + formula + "]: " + e.getMessage(), e);
        }
    }
}

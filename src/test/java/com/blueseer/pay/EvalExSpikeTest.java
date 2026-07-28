package com.blueseer.pay;

import com.ezylang.evalex.Expression;
import com.ezylang.evalex.EvaluationException;
import com.ezylang.evalex.parser.ParseException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Empirical spike resolving irish-payroll-2026-calc-engines-spec.md's Open
 * Question 3 (EvalEx v3 STRING {@code ==} equality support) and confirming
 * the {@code withValues(Map)} / {@code IF} / {@code MIN} / {@code MAX} API
 * shape every calc engine in this package depends on, before those engines
 * are written against assumptions instead of observed behaviour.
 */
class EvalExSpikeTest {

    @Test
    void withValuesMapAndArithmeticWork() throws EvaluationException, ParseException {
        Map<String, Object> values = new HashMap<>();
        values.put("grossPay", new BigDecimal("500.00"));
        values.put("rate", new BigDecimal("0.20"));
        Expression expression = new Expression("grossPay * rate");
        BigDecimal result = expression.withValues(values).evaluate().getNumberValue();
        assertEquals(0, new BigDecimal("100.00").compareTo(result));
    }

    @Test
    void ifMinMaxWork() throws EvaluationException, ParseException {
        Map<String, Object> values = new HashMap<>();
        values.put("a", new BigDecimal("10"));
        values.put("b", new BigDecimal("20"));
        Expression expression = new Expression("IF(a < b, MIN(a, b), MAX(a, b))");
        BigDecimal result = expression.withValues(values).evaluate().getNumberValue();
        assertEquals(0, new BigDecimal("10").compareTo(result));
    }

    @Test
    void stringEqualityInIfCondition() throws EvaluationException, ParseException {
        // This is the spike: does `stringVar == "LITERAL"` work as a boolean
        // condition the way C-01/C-04/C-05/C-07/C-08/C-09/C-10/C-11 assume?
        Map<String, Object> values = new HashMap<>();
        values.put("calculationBasis", "WEEK1");
        Expression expression = new Expression("IF(calculationBasis == \"WEEK1\", 1, 0)");
        BigDecimal result = expression.withValues(values).evaluate().getNumberValue();
        assertEquals(0, BigDecimal.ONE.compareTo(result));
    }

    @Test
    void stringInequalityWhenNotMatching() throws EvaluationException, ParseException {
        Map<String, Object> values = new HashMap<>();
        values.put("calculationBasis", "CUMULATIVE");
        Expression expression = new Expression("IF(calculationBasis == \"WEEK1\", 1, 0)");
        BigDecimal result = expression.withValues(values).evaluate().getNumberValue();
        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }

    @Test
    void booleanValuesInContextMapWork() throws EvaluationException, ParseException {
        Map<String, Object> values = new HashMap<>();
        values.put("hasPpsNumber", Boolean.FALSE);
        Expression expression = new Expression("IF(hasPpsNumber == true, 1, 0)");
        BigDecimal result = expression.withValues(values).evaluate().getNumberValue();
        assertEquals(0, BigDecimal.ZERO.compareTo(result));
    }
}

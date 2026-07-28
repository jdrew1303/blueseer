package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C-08 BIK Company Vans, per TDM Part 05-01-01b §4.1/§5.2 (see calc-engine
 * spec §C-08). Reuses cars' {@code temporaryOmvReduction} (TDM §4.1.3
 * explicitly includes "All vans" in that reduction), applies a flat rate
 * with no mileage tapering.
 */
final class BikVanCalculator {

    private static final String ENGINE = "C-08 BIK Company Vans";

    private BikVanCalculator() {
    }

    record BikVanContext(
            BigDecimal omvOriginal,
            BigDecimal amountMadeGoodByEmployee,
            int daysVehicleAvailableInYear,
            int daysInYear,
            boolean qualifiesForLimitedPrivateUseExemption) {
    }

    record BikVanResult(
            BigDecimal reducedOmv,
            BigDecimal cashEquivalentForPeriod,
            BigDecimal finalBikChargeable,
            List<CalculationStep> steps) {
    }

    static BikVanResult calculate(BikVanContext ctx, BigDecimal temporaryOmvReduction, BigDecimal vanBikRate) {
        Map<String, Object> initial = new HashMap<>();
        initial.put("omvOriginal", ctx.omvOriginal());
        initial.put("temporaryOmvReduction", temporaryOmvReduction);
        initial.put("vanBikRate", vanBikRate);
        initial.put("qualifiesForLimitedPrivateUseExemption", ctx.qualifiesForLimitedPrivateUseExemption());
        initial.put("daysVehicleAvailableInYear", BigDecimal.valueOf(ctx.daysVehicleAvailableInYear()));
        initial.put("daysInYear", BigDecimal.valueOf(ctx.daysInYear()));
        initial.put("amountMadeGoodByEmployee", ctx.amountMadeGoodByEmployee());

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        BigDecimal reducedOmv = runner.step("Reduced OMV",
                "MAX(0, omvOriginal - temporaryOmvReduction)",
                "reducedOmv");
        BigDecimal cashEquivalentBeforeProration = runner.step("Cash equivalent before proration",
                "IF(qualifiesForLimitedPrivateUseExemption == true, 0, reducedOmv * vanBikRate)",
                "cashEquivalentBeforeProration");
        BigDecimal cashEquivalentForPeriod = runner.step("Pro-rated for partial-year availability",
                "cashEquivalentBeforeProration * (daysVehicleAvailableInYear / daysInYear)",
                "cashEquivalentForPeriod");
        BigDecimal finalBik = runner.finalStep("Final BIK chargeable this period",
                "MAX(0, cashEquivalentForPeriod - amountMadeGoodByEmployee)");

        return new BikVanResult(reducedOmv, cashEquivalentForPeriod, finalBik, runner.result().steps());
    }
}

package com.blueseer.pay;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * C-07 BIK Company Cars, per TDM Part 05-01-01b §4.1-4.1.6/§5.1 (see
 * calc-engine spec §C-07). Vehicle category (Table B) and the mileage-band
 * rate (Table A) are resolved via {@link BikCarRateTable} before the 7-step
 * EvalEx chain runs, since the calc-engine spec treats those as lookups, not
 * formulas.
 */
final class BikCarCalculator {

    private static final String ENGINE = "C-07 BIK Company Cars";

    private BikCarCalculator() {
    }

    record BikCarContext(
            BigDecimal omvOriginal,
            BigDecimal co2EmissionsGramsPerKm,
            BigDecimal actualBusinessKilometres,
            int daysVehicleAvailableInYear,
            int daysInYear,
            boolean isElectricVehicle,
            BigDecimal amountMadeGoodByEmployee,
            boolean qualifiesFor20PercentReduction) {
    }

    record BikCarResult(
            String vehicleCategory,
            BigDecimal annualisedBusinessKm,
            BigDecimal ratePercentAtActualMileage,
            BigDecimal reducedOmv,
            BigDecimal taperedCashEquivalent,
            BigDecimal twentyPercentReductionCashEquivalent,
            BigDecimal cashEquivalentForPeriod,
            BigDecimal finalBikChargeable,
            List<CalculationStep> steps) {
    }

    static BikCarResult calculate(BikCarContext ctx, ITaxYearRules.BikCarRates rates) {
        String category = BikCarRateTable.categoryForCo2(ctx.co2EmissionsGramsPerKm());

        Map<String, Object> initial = new HashMap<>();
        initial.put("actualBusinessKilometres", ctx.actualBusinessKilometres());
        initial.put("daysInYear", BigDecimal.valueOf(ctx.daysInYear()));
        initial.put("daysVehicleAvailableInYear", BigDecimal.valueOf(ctx.daysVehicleAvailableInYear()));
        initial.put("omvOriginal", ctx.omvOriginal());
        initial.put("temporaryOmvReduction", rates.temporaryOmvReduction());
        initial.put("electricVehicleOmvReduction", rates.electricVehicleOmvReduction());
        initial.put("isElectricVehicle", ctx.isElectricVehicle());
        initial.put("amountMadeGoodByEmployee", ctx.amountMadeGoodByEmployee());
        initial.put("qualifiesFor20PercentReduction", ctx.qualifiesFor20PercentReduction());

        EvalExStepRunner runner = new EvalExStepRunner(ENGINE, initial);

        BigDecimal annualisedBusinessKm = runner.step("Annualised business kilometres",
                "actualBusinessKilometres * daysInYear / daysVehicleAvailableInYear",
                "annualisedBusinessKm");

        BigDecimal ratePercentAtActualMileage = BikCarRateTable.rateForCategoryAndMileage(rates, category, annualisedBusinessKm);
        BigDecimal ratePercentAtLowestMileageBand = BikCarRateTable.lowestMileageBandRate(rates, category);
        runner.put("ratePercentAtActualMileage", ratePercentAtActualMileage);
        runner.put("ratePercentAtLowestMileageBand", ratePercentAtLowestMileageBand);

        BigDecimal reducedOmv = runner.step("Reduced OMV",
                "MAX(0, omvOriginal - temporaryOmvReduction - IF(isElectricVehicle == true, electricVehicleOmvReduction, 0))",
                "reducedOmv");
        BigDecimal taperedCashEquivalent = runner.step("Cash equivalent at actual tapered rate",
                "reducedOmv * ratePercentAtActualMileage",
                "taperedCashEquivalent");
        BigDecimal twentyPercentReductionCashEquivalent = runner.step("Cash equivalent under the alternative 20% reduction",
                "reducedOmv * ratePercentAtLowestMileageBand * 0.80",
                "twentyPercentReductionCashEquivalent");
        BigDecimal cashEquivalentBeforeProration = runner.step("Selected cash equivalent before proration",
                "IF(qualifiesFor20PercentReduction == true, MIN(taperedCashEquivalent, twentyPercentReductionCashEquivalent), taperedCashEquivalent)",
                "cashEquivalentBeforeProration");
        BigDecimal cashEquivalentForPeriod = runner.step("Pro-rated for partial-year availability",
                "cashEquivalentBeforeProration * (daysVehicleAvailableInYear / daysInYear)",
                "cashEquivalentForPeriod");
        BigDecimal finalBik = runner.finalStep("Final BIK chargeable this period",
                "MAX(0, cashEquivalentForPeriod - amountMadeGoodByEmployee)");

        return new BikCarResult(category, annualisedBusinessKm, ratePercentAtActualMileage, reducedOmv,
                taperedCashEquivalent, twentyPercentReductionCashEquivalent, cashEquivalentForPeriod, finalBik,
                runner.result().steps());
    }
}

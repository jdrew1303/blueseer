package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs backing S-53 BIK Cars &amp; Vans (C-07/C-08), S-54 BIK Preferential Loans (C-09), and S-55 BIK Annual/One-Off Benefits. */
public final class BikDtos {

    private BikDtos() {
    }

    public record BikCarDTO(
            EmployeeId employeeId,
            BigDecimal omvOriginal,
            BigDecimal co2EmissionsGramsPerKm,
            BigDecimal actualBusinessKilometres,
            LocalDate availableFrom,
            LocalDate availableTo,
            boolean isElectricVehicle,
            BigDecimal amountMadeGoodByEmployee,
            boolean qualifiesFor20PercentReduction) {
    }

    public record BikVanDTO(
            EmployeeId employeeId,
            BigDecimal omvOriginal,
            LocalDate availableFrom,
            LocalDate availableTo,
            BigDecimal amountMadeGoodByEmployee,
            boolean qualifiesForLimitedPrivateUseExemption) {
    }

    public record BikPreviewDTO(
            String vehicleCategoryOrNull,
            BigDecimal ratePercentAtActualMileageOrNull,
            BigDecimal reducedOmv,
            BigDecimal cashEquivalentForPeriod,
            BigDecimal finalBikChargeable,
            List<CalculationStep> steps) {
    }

    /** Whichever of {@code carOrNull}/{@code vanOrNull} is non-null is the entry being saved, per S-53's single {@code cardPanel}. */
    public record BikVehicleDTO(
            EmployeeId employeeId,
            BikCarDTO carOrNull,
            BikVanDTO vanOrNull) {
    }

    /**
     * {@code actualInterestRateForSubPeriod} is carried for S-54's table
     * display only (per the calc-engine spec's own state context map) - C-09's
     * EvalEx steps compare the specified rate against
     * {@code actualInterestPaidForSubPeriod} directly, never against a rate.
     */
    public record BikLoanSubPeriodDTO(
            BigDecimal openingPrincipalBalance,
            int daysInSubPeriod,
            BigDecimal actualInterestRateForSubPeriod,
            BigDecimal actualInterestPaidForSubPeriod) {
    }

    public record BikLoanDTO(
            EmployeeId employeeId,
            String loanCategory,
            List<BikLoanSubPeriodDTO> subPeriods,
            boolean isJointLoanWithNonEmployee,
            boolean isMarriedOrCivilPartnerJointLoan,
            BigDecimal employeeSharePercentage) {
    }

    public record BikBenefitDTO(
            EmployeeId employeeId,
            String benefitDescription,
            BigDecimal benefitAmount,
            boolean smallBenefitExemptionEligible) {
    }

    public record BikBenefitPreviewDTO(
            BigDecimal remainingAnnualAllowance,
            BigDecimal taxableAmount) {
    }
}

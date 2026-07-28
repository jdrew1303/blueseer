package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.DepartmentId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs backing S-04...S-13 (Employee Maintenance family), grouped rather than one file per type. */
public final class EmployeeDtos {

    private EmployeeDtos() {
    }

    /** Powers S-04's cbSurnameLookup autocomplete. */
    public record EmployeeSummaryDTO(EmployeeId id, String surname, String firstName, String worksNumber, boolean isFormerEmployee) {
    }

    /** S-09 Departments Maintenance. */
    public record DepartmentDTO(DepartmentId id, String code, String name) {
    }

    /** S-05 Personal Details tab. */
    public record PersonalDetailsDTO(
            EmployeeId id,
            String surname,
            String firstName,
            String address,
            LocalDate dateOfBirth,
            String email,
            String payslipPassword,
            boolean director,
            DepartmentId departmentId,
            String ppsNumber,
            String employmentId,
            String worksNumber,
            BigDecimal hourlyRate,
            BigDecimal fixedPay,
            PayMethod payMethod,
            String bank,
            String branch,
            String sortCode,
            String accountNumber,
            String creditUnionRef) {
    }

    public enum PayMethod { CASH, CHEQUE, CREDIT_TRANSFER }

    /** Result of S-05's PPS modulus-check, called on tbPpsNumber focus-lost. */
    public record PpsValidationResult(boolean valid, String reasonIfInvalid) {
    }

    /** S-06 Revenue Details tab - deliberately carries no tax credit/cut-off fields (RPN-only). */
    public record RevenueDetailsDTO(
            EmployeeId id,
            LocalDate startDate,
            int startWeek,
            String prsiClass,
            List<String> exemptionCodes) {
    }

    /** Drives S-06's lblEmergencyBanner. */
    public record EmergencyStatusDTO(boolean onEmergencyBasis, String reasonIfEmergency) {
    }

    /** S-07/S-08 Additions/Deductions table rows. */
    public record AdditionLineDTO(
            PayrollIds.LineItemId id,
            String description,
            boolean taxable,
            BigDecimal amount,
            LocalDate endDateOrNull) {
    }

    /** {@code description == "ASC"} carries the ASC sub-fields instead of a flat amount, per S-08. */
    public record DeductionLineDTO(
            PayrollIds.LineItemId id,
            String description,
            boolean preTax,
            BigDecimal amount,
            LocalDate endDateOrNull,
            ITaxYearRules.AscGroup ascGroupOrNull,
            BigDecimal ascOverrideAmountOrNull,
            BigDecimal ascOverridePercentageOrNull) {
    }

    /** S-10 Mid-Year Cumulatives tab. */
    public record CumulativesDTO(
            BigDecimal priorGrossPay,
            BigDecimal priorTaxPaid,
            BigDecimal priorPrsiPaid,
            BigDecimal priorUscPaid) {
    }

    /** S-11 HR Details tab - non-statutory, informational only. */
    public record HrDetailsDTO(String jobTitle, String contractType, String emergencyContactName, String emergencyContactPhone) {
    }

    /** S-12 CSO Details tab, feeds the EHECS extract. */
    public record CsoDetailsDTO(String occupationCode, String hoursCategory) {
    }

    /**
     * S-04's composite load result - sub-objects are exactly what each tab
     * consumes, loaded once when cbSurnameLookup selection changes.
     */
    public record EmployeeRecordDTO(
            EmployeeId id,
            PersonalDetailsDTO personalDetails,
            RevenueDetailsDTO revenueDetails,
            List<AdditionLineDTO> additions,
            List<DeductionLineDTO> deductions,
            CumulativesDTO cumulatives,
            HrDetailsDTO hrDetails,
            CsoDetailsDTO csoDetails) {
    }
}

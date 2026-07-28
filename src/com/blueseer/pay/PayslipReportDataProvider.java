package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.AdditionLineDTO;
import com.blueseer.pay.EmployeeDtos.CumulativesDTO;
import com.blueseer.pay.EmployeeDtos.DeductionLineDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.PersonalDetailsDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Track C: maps finalised {@link PayslipRecordDTO}s into the print-ready
 * {@link PayslipReportDTO} bean every payslip stationery template consumes,
 * per {@link IReportDataProvider}. {@code additionalFilters} keys used:
 * {@code "periodNumber"} (Integer, required), {@code "employeeIds"}
 * (List&lt;EmployeeId&gt;, optional - omit for "all employees in the
 * period"), {@code "includeZeroPayment"} (Boolean, default false).
 *
 * <p>There is no persisted company profile store yet (S-01's wizard doesn't
 * write one anywhere queryable), so employer name/address/registration are
 * fixed placeholders - the same gap already documented for S-35's employer
 * comparison. Additions/deductions text reflects the employee's *current*
 * standing lines rather than a per-payslip snapshot, and cumulative
 * gross/tax/PRSI/USC reflect the employee's *current* running total rather
 * than what it was as of that specific historical payslip - both are
 * Track A stub simplifications from not persisting a full point-in-time
 * snapshot per payslip.
 */
final class PayslipReportDataProvider implements IReportDataProvider<PayslipReportDTO> {

    private static final String PLACEHOLDER_EMPLOYER_NAME = "Demo Company Ltd";
    private static final String PLACEHOLDER_EMPLOYER_ADDRESS = "1 Example Street, Dublin 1";
    private static final String PLACEHOLDER_EMPLOYER_REG = "1234567A";

    private final PayrollStubStore store;

    PayslipReportDataProvider(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<PayslipReportDTO> fetchReportData(ReportCriteria criteria) {
        Integer periodNumber = (Integer) criteria.additionalFilters().get("periodNumber");
        Set<EmployeeId> employeeFilter = (Set<EmployeeId>) criteria.additionalFilters().get("employeeIds");
        boolean includeZeroPayment = Boolean.TRUE.equals(criteria.additionalFilters().get("includeZeroPayment"));

        List<PayslipReportDTO> results = new ArrayList<>();
        for (PayslipRecordDTO payslip : store.payslips.values()) {
            if (periodNumber != null && payslip.periodNumber() != periodNumber) {
                continue;
            }
            if (employeeFilter != null && !employeeFilter.contains(payslip.employeeId())) {
                continue;
            }
            if (!includeZeroPayment && payslip.net().signum() == 0) {
                continue;
            }
            EmployeeRecordDTO rec = store.employees.get(payslip.employeeId());
            if (rec != null) {
                results.add(toReportDto(payslip, rec));
            }
        }
        return results;
    }

    private PayslipReportDTO toReportDto(PayslipRecordDTO payslip, EmployeeRecordDTO rec) {
        PersonalDetailsDTO p = rec.personalDetails();
        PayFrequency frequency = GrossPayAssembler.inferFrequency(p, store.employeeFrequencyOverride.get(rec.id()));

        PayslipReportDTO dto = new PayslipReportDTO();
        dto.setEmployerName(PLACEHOLDER_EMPLOYER_NAME);
        dto.setEmployerAddress(PLACEHOLDER_EMPLOYER_ADDRESS);
        dto.setEmployerRegistrationNumber(PLACEHOLDER_EMPLOYER_REG);

        dto.setEmployeeName(p.surname() + ", " + p.firstName());
        dto.setEmployeeAddress(p.address());
        dto.setPpsNumber(p.ppsNumber());
        dto.setWorksNumber(p.worksNumber());

        dto.setPayDate(payslip.payDate());
        dto.setPeriodNumber(payslip.periodNumber());
        dto.setTaxYear(2026);
        dto.setPayFrequency(frequency.name());
        dto.setPrsiClass(p.director() ? "S0" : rec.revenueDetails() == null ? "" : rec.revenueDetails().prsiClass());
        dto.setCalculationBasis("Emergency");

        dto.setGrossPay(payslip.gross());
        dto.setAdditionsText(formatAdditions(rec.additions()));
        dto.setDeductionsText(formatDeductions(rec.deductions()));
        dto.setPaye(payslip.paye());
        dto.setEmployeePrsi(payslip.prsiEmployee());
        dto.setUsc(payslip.usc());
        dto.setLpt(BigDecimal.ZERO);
        dto.setNetPay(payslip.net());
        dto.setEmployerPrsi(BigDecimal.ZERO);

        dto.setAnnualTaxCredit(BigDecimal.ZERO);
        dto.setAnnualCutOffPoint(BigDecimal.ZERO);

        CumulativesDTO cum = rec.cumulatives();
        dto.setCumulativeGrossPay(nz(cum == null ? null : cum.priorGrossPay()));
        dto.setCumulativeTaxPaid(nz(cum == null ? null : cum.priorTaxPaid()));
        dto.setCumulativePrsiPaid(nz(cum == null ? null : cum.priorPrsiPaid()));
        dto.setCumulativeUscPaid(nz(cum == null ? null : cum.priorUscPaid()));

        dto.setNetPayInWords(null);
        return dto;
    }

    private static String formatAdditions(List<AdditionLineDTO> additions) {
        if (additions == null || additions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (AdditionLineDTO a : additions) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(a.description()).append("  ").append(a.amount() == null ? "" : a.amount().toPlainString());
        }
        return sb.toString();
    }

    private static String formatDeductions(List<DeductionLineDTO> deductions) {
        if (deductions == null || deductions.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (DeductionLineDTO d : deductions) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(d.description()).append("  ").append(d.amount() == null ? "Auto-calculated" : d.amount().toPlainString());
        }
        return sb.toString();
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}

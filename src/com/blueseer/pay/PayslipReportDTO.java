package com.blueseer.pay;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One payslip's worth of print-ready data, per
 * docs/architecture/irish-payroll-2026-roadmap.md's decoupling design: fed
 * to each {@code .jrxml} template via {@code JRBeanCollectionDataSource}
 * (bean-driven, not the SQL-{@code queryString} pattern some older
 * templates in this project use), matching {@link IReportDataProvider}.
 *
 * <p>Field set and the "Workings"-style tax-credit/cut-off reference line
 * are grounded in Thesaurus Payroll Manager 2026's own Payslip Workings
 * screen (thesaurus.ie/docs/2026/processing-payroll/payslip-workings/) and
 * its documented payslip stationery types
 * (thesaurus.ie/docs/2026/distributing-payslips/printing-payslips/).
 * {@code additionsText}/{@code deductionsText} are pre-formatted,
 * newline-separated "description ... amount" lines rather than a nested
 * bean list, since JasperReports renders those as a single wrapped text
 * field without needing a subreport - matching the calc-engine spec's own
 * note that "any narrative entered will print to the employee payslip
 * opposite the deduction figure."
 */
public class PayslipReportDTO {

    private String employerName;
    private String employerAddress;
    private String employerRegistrationNumber;

    private String employeeName;
    private String employeeAddress;
    private String ppsNumber;
    private String worksNumber;

    private LocalDate payDate;
    private int periodNumber;
    private int taxYear;
    private String payFrequency;
    private String prsiClass;
    private String calculationBasis;

    private BigDecimal grossPay;
    private String additionsText;
    private String deductionsText;
    private BigDecimal paye;
    private BigDecimal employeePrsi;
    private BigDecimal usc;
    private BigDecimal lpt;
    private BigDecimal netPay;
    private BigDecimal employerPrsi;

    private BigDecimal annualTaxCredit;
    private BigDecimal annualCutOffPoint;

    private BigDecimal cumulativeGrossPay;
    private BigDecimal cumulativeTaxPaid;
    private BigDecimal cumulativePrsiPaid;
    private BigDecimal cumulativeUscPaid;

    private String netPayInWords;

    public String getEmployerName() {
        return employerName;
    }

    public void setEmployerName(String employerName) {
        this.employerName = employerName;
    }

    public String getEmployerAddress() {
        return employerAddress;
    }

    public void setEmployerAddress(String employerAddress) {
        this.employerAddress = employerAddress;
    }

    public String getEmployerRegistrationNumber() {
        return employerRegistrationNumber;
    }

    public void setEmployerRegistrationNumber(String employerRegistrationNumber) {
        this.employerRegistrationNumber = employerRegistrationNumber;
    }

    public String getEmployeeName() {
        return employeeName;
    }

    public void setEmployeeName(String employeeName) {
        this.employeeName = employeeName;
    }

    public String getEmployeeAddress() {
        return employeeAddress;
    }

    public void setEmployeeAddress(String employeeAddress) {
        this.employeeAddress = employeeAddress;
    }

    public String getPpsNumber() {
        return ppsNumber;
    }

    public void setPpsNumber(String ppsNumber) {
        this.ppsNumber = ppsNumber;
    }

    public String getWorksNumber() {
        return worksNumber;
    }

    public void setWorksNumber(String worksNumber) {
        this.worksNumber = worksNumber;
    }

    public LocalDate getPayDate() {
        return payDate;
    }

    public void setPayDate(LocalDate payDate) {
        this.payDate = payDate;
    }

    public int getPeriodNumber() {
        return periodNumber;
    }

    public void setPeriodNumber(int periodNumber) {
        this.periodNumber = periodNumber;
    }

    public int getTaxYear() {
        return taxYear;
    }

    public void setTaxYear(int taxYear) {
        this.taxYear = taxYear;
    }

    public String getPayFrequency() {
        return payFrequency;
    }

    public void setPayFrequency(String payFrequency) {
        this.payFrequency = payFrequency;
    }

    public String getPrsiClass() {
        return prsiClass;
    }

    public void setPrsiClass(String prsiClass) {
        this.prsiClass = prsiClass;
    }

    public String getCalculationBasis() {
        return calculationBasis;
    }

    public void setCalculationBasis(String calculationBasis) {
        this.calculationBasis = calculationBasis;
    }

    public BigDecimal getGrossPay() {
        return grossPay;
    }

    public void setGrossPay(BigDecimal grossPay) {
        this.grossPay = grossPay;
    }

    public String getAdditionsText() {
        return additionsText;
    }

    public void setAdditionsText(String additionsText) {
        this.additionsText = additionsText;
    }

    public String getDeductionsText() {
        return deductionsText;
    }

    public void setDeductionsText(String deductionsText) {
        this.deductionsText = deductionsText;
    }

    public BigDecimal getPaye() {
        return paye;
    }

    public void setPaye(BigDecimal paye) {
        this.paye = paye;
    }

    public BigDecimal getEmployeePrsi() {
        return employeePrsi;
    }

    public void setEmployeePrsi(BigDecimal employeePrsi) {
        this.employeePrsi = employeePrsi;
    }

    public BigDecimal getUsc() {
        return usc;
    }

    public void setUsc(BigDecimal usc) {
        this.usc = usc;
    }

    public BigDecimal getLpt() {
        return lpt;
    }

    public void setLpt(BigDecimal lpt) {
        this.lpt = lpt;
    }

    public BigDecimal getNetPay() {
        return netPay;
    }

    public void setNetPay(BigDecimal netPay) {
        this.netPay = netPay;
    }

    public BigDecimal getEmployerPrsi() {
        return employerPrsi;
    }

    public void setEmployerPrsi(BigDecimal employerPrsi) {
        this.employerPrsi = employerPrsi;
    }

    public BigDecimal getAnnualTaxCredit() {
        return annualTaxCredit;
    }

    public void setAnnualTaxCredit(BigDecimal annualTaxCredit) {
        this.annualTaxCredit = annualTaxCredit;
    }

    public BigDecimal getAnnualCutOffPoint() {
        return annualCutOffPoint;
    }

    public void setAnnualCutOffPoint(BigDecimal annualCutOffPoint) {
        this.annualCutOffPoint = annualCutOffPoint;
    }

    public BigDecimal getCumulativeGrossPay() {
        return cumulativeGrossPay;
    }

    public void setCumulativeGrossPay(BigDecimal cumulativeGrossPay) {
        this.cumulativeGrossPay = cumulativeGrossPay;
    }

    public BigDecimal getCumulativeTaxPaid() {
        return cumulativeTaxPaid;
    }

    public void setCumulativeTaxPaid(BigDecimal cumulativeTaxPaid) {
        this.cumulativeTaxPaid = cumulativeTaxPaid;
    }

    public BigDecimal getCumulativePrsiPaid() {
        return cumulativePrsiPaid;
    }

    public void setCumulativePrsiPaid(BigDecimal cumulativePrsiPaid) {
        this.cumulativePrsiPaid = cumulativePrsiPaid;
    }

    public BigDecimal getCumulativeUscPaid() {
        return cumulativeUscPaid;
    }

    public void setCumulativeUscPaid(BigDecimal cumulativeUscPaid) {
        this.cumulativeUscPaid = cumulativeUscPaid;
    }

    public String getNetPayInWords() {
        return netPayInWords;
    }

    public void setNetPayInWords(String netPayInWords) {
        this.netPayInWords = netPayInWords;
    }
}

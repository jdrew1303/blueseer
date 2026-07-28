package com.blueseer.pay;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Compiles and fills each of the 4 payslip stationery templates (S-36,
 * roadmap &sect;3.7) with a sample {@link PayslipReportDTO} via
 * {@code JRBeanCollectionDataSource}, then exports to PDF - the same
 * "actually build and run it" bar applied to every other piece of this
 * module, not just eyeballing the XML.
 */
class PayslipJasperTemplatesTest {

    private static final String JASPER_DIR = "sf/jasper/";

    @ParameterizedTest
    @ValueSource(strings = {
            "payslip_2perpage_hires",
            "payslip_2perpage_lores",
            "payslip_and_cheque",
            "payslip_laser_security"
    })
    void templateCompilesAndFillsWithSampleData(String templateName) throws Exception {
        File jrxml = new File(JASPER_DIR + templateName + ".jrxml");
        assertTrue(jrxml.isFile(), "Missing template: " + jrxml.getPath());

        JasperReport report = JasperCompileManager.compileReport(jrxml.getPath());

        JRBeanCollectionDataSource dataSource = new JRBeanCollectionDataSource(List.of(samplePayslip()));
        Map<String, Object> params = new HashMap<>();
        JasperPrint print = JasperFillManager.fillReport(report, params, dataSource);

        assertTrue(print.getPages().size() >= 1, "Expected at least one page for " + templateName);

        File outFile = new File("target/" + templateName + "-sample.pdf");
        outFile.getParentFile().mkdirs();
        JasperExportManager.exportReportToPdfFile(print, outFile.getPath());
        assertTrue(outFile.isFile() && outFile.length() > 0, "PDF export was empty for " + templateName);
    }

    @Test
    void emptyDataSourceStillCompilesAndFills() throws Exception {
        // Guards against a template accidentally requiring a non-empty
        // collection (e.g. a mis-scoped group) - S-36 must tolerate a
        // "no zero-payment payslips selected" run without throwing.
        for (String templateName : List.of("payslip_2perpage_hires", "payslip_2perpage_lores",
                "payslip_and_cheque", "payslip_laser_security")) {
            JasperReport report = JasperCompileManager.compileReport(JASPER_DIR + templateName + ".jrxml");
            JasperPrint print = JasperFillManager.fillReport(report, new HashMap<>(), new JREmptyDataSource());
            assertTrue(print != null, "fillReport returned null for " + templateName);
        }
    }

    private static PayslipReportDTO samplePayslip() {
        PayslipReportDTO dto = new PayslipReportDTO();
        dto.setEmployerName("Aoife Foods Ltd");
        dto.setEmployerAddress("10 Grafton Street, Dublin 2");
        dto.setEmployerRegistrationNumber("1234567A");
        dto.setEmployeeName("Siobhan Murphy");
        dto.setEmployeeAddress("12 Grafton Street\nDublin 2\nD02 XY45");
        dto.setPpsNumber("1234567A");
        dto.setWorksNumber("W001");
        dto.setPayDate(LocalDate.of(2026, 3, 27));
        dto.setPeriodNumber(4);
        dto.setTaxYear(2026);
        dto.setPayFrequency("Monthly");
        dto.setPrsiClass("A1");
        dto.setCalculationBasis("Cumulative");
        dto.setGrossPay(new BigDecimal("3500.00"));
        dto.setAdditionsText("Bonus                              200.00");
        dto.setDeductionsText("Pension                              50.00");
        dto.setPaye(new BigDecimal("650.00"));
        dto.setEmployeePrsi(new BigDecimal("147.00"));
        dto.setUsc(new BigDecimal("87.14"));
        dto.setLpt(null);
        dto.setNetPay(new BigDecimal("2615.86"));
        dto.setEmployerPrsi(new BigDecimal("315.00"));
        dto.setAnnualTaxCredit(new BigDecimal("4200.00"));
        dto.setAnnualCutOffPoint(new BigDecimal("48000.00"));
        dto.setCumulativeGrossPay(new BigDecimal("14000.00"));
        dto.setCumulativeTaxPaid(new BigDecimal("2600.00"));
        dto.setCumulativePrsiPaid(new BigDecimal("588.00"));
        dto.setCumulativeUscPaid(new BigDecimal("348.56"));
        return dto;
    }
}

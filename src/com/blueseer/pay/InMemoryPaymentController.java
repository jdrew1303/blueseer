package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.EmployeeDtos.PayMethod;
import com.blueseer.pay.EmployeeDtos.PersonalDetailsDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PaymentDtos.BankAccountDTO;
import com.blueseer.pay.PaymentDtos.BankFileRequestDTO;
import com.blueseer.pay.PaymentDtos.PayMethodSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** Track A/B/C stub for {@link IPaymentController}, backed by {@link PayrollStubStore}. */
final class InMemoryPaymentController implements IPaymentController {

    private static final BankAccountDTO DEMO_ACCOUNT =
            new BankAccountDTO("Demo Company Ltd - Current Account", "IE29AIBK93115212345678", "AIBKIE2D");

    private final PayrollStubStore store;

    InMemoryPaymentController(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public List<BankAccountDTO> getSourceAccounts(CompanyId id) {
        return List.of(DEMO_ACCOUNT);
    }

    @Override
    public List<EmployeeSummaryDTO> getCreditTransferEmployees(CompanyId id, int periodNumber) {
        List<EmployeeSummaryDTO> results = new ArrayList<>();
        List<EmployeeId> seen = new ArrayList<>();
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.periodNumber() != periodNumber || seen.contains(p.employeeId())) {
                continue;
            }
            EmployeeRecordDTO rec = store.employees.get(p.employeeId());
            if (rec == null || rec.personalDetails().payMethod() != PayMethod.CREDIT_TRANSFER) {
                continue;
            }
            seen.add(p.employeeId());
            PersonalDetailsDTO personal = rec.personalDetails();
            results.add(new EmployeeSummaryDTO(p.employeeId(), personal.surname(), personal.firstName(), personal.worksNumber(), false));
        }
        return results;
    }

    @Override
    public byte[] generateBankFile(BankFileRequestDTO req) {
        StringBuilder sb = new StringBuilder();
        boolean sepaXml = "Standard SEPA (pain.001)".equals(req.fileFormat()) || "Non-Irish IBAN".equals(req.fileFormat());
        if (sepaXml) {
            sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
            sb.append("<Document xmlns=\"urn:iso:std:iso:20022:tech:xsd:pain.001.001.03\">\n");
            sb.append("  <CstmrCdtTrfInitn>\n");
            sb.append("    <GrpHdr><PmtInfId>PAY-").append(req.periodNumber()).append("</PmtInfId></GrpHdr>\n");
            sb.append("    <PmtInf>\n");
            sb.append("      <ReqdExctnDt>").append(req.paymentDate()).append("</ReqdExctnDt>\n");
            sb.append("      <DbtrAcct><IBAN>").append(req.sourceAccount().iban()).append("</IBAN></DbtrAcct>\n");
            appendCreditTransfers(sb, req, "      <CdtTrfTxInf><Cdtr>%s</Cdtr><CdtrAcct><IBAN>%s</IBAN></CdtrAcct><Amt>%s</Amt></CdtTrfTxInf>\n");
            sb.append("    </PmtInf>\n");
            sb.append("  </CstmrCdtTrfInitn>\n");
            sb.append("</Document>\n");
        } else if ("Bankline".equals(req.fileFormat())) {
            sb.append("SOURCE,").append(req.sourceAccount().iban()).append(",").append(req.paymentDate()).append('\n');
            appendCreditTransfers(sb, req, "PAYEE,%s,%s,%s\n");
        } else {
            sb.append("{\n  \"paymentDate\": \"").append(req.paymentDate()).append("\",\n");
            sb.append("  \"sourceIban\": \"").append(req.sourceAccount().iban()).append("\",\n");
            sb.append("  \"payments\": [\n");
            appendCreditTransfers(sb, req, "    {\"payee\": \"%s\", \"iban\": \"%s\", \"amount\": \"%s\"},\n");
            sb.append("  ]\n}\n");
        }
        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Employee records only hold sort-code/account-number, not a real IBAN
     * (no BIC/IBAN capture exists on {@link PersonalDetailsDTO} yet), so the
     * payee IBAN here is a placeholder derived from those fields, not a real
     * modulus-checked conversion - an honest simplification, consistent with
     * every other Track A stub in this module.
     */
    private void appendCreditTransfers(StringBuilder sb, BankFileRequestDTO req, String lineFormat) {
        for (EmployeeId id : req.employeeIds()) {
            EmployeeRecordDTO rec = store.employees.get(id);
            if (rec == null) {
                continue;
            }
            PersonalDetailsDTO p = rec.personalDetails();
            BigDecimal amount = latestNetForPeriod(id, req.periodNumber());
            if (amount == null) {
                continue;
            }
            String placeholderIban = "IE00XXXX" + safe(p.sortCode()) + safe(p.accountNumber());
            sb.append(String.format(lineFormat, p.surname() + ", " + p.firstName(), placeholderIban, amount.toPlainString()));
        }
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private BigDecimal latestNetForPeriod(EmployeeId id, int periodNumber) {
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.employeeId().equals(id) && p.periodNumber() == periodNumber) {
                return p.net();
            }
        }
        return null;
    }

    @Override
    public PayMethodSummaryDTO getPayMethodSummary(CompanyId id, LocalDate payDate) {
        BigDecimal cashTotal = BigDecimal.ZERO;
        BigDecimal chequeTotal = BigDecimal.ZERO;
        BigDecimal creditTransferTotal = BigDecimal.ZERO;
        int cashCount = 0;
        int chequeCount = 0;
        int creditTransferCount = 0;

        for (PayslipRecordDTO p : store.payslips.values()) {
            if (!p.payDate().equals(payDate)) {
                continue;
            }
            EmployeeRecordDTO rec = store.employees.get(p.employeeId());
            PayMethod method = rec == null ? PayMethod.CREDIT_TRANSFER : rec.personalDetails().payMethod();
            switch (method) {
                case CASH -> {
                    cashTotal = cashTotal.add(p.net());
                    cashCount++;
                }
                case CHEQUE -> {
                    chequeTotal = chequeTotal.add(p.net());
                    chequeCount++;
                }
                default -> {
                    creditTransferTotal = creditTransferTotal.add(p.net());
                    creditTransferCount++;
                }
            }
        }
        return new PayMethodSummaryDTO(payDate, cashTotal, cashCount, chequeTotal, chequeCount, creditTransferTotal, creditTransferCount);
    }
}

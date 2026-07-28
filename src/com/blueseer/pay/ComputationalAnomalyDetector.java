package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayProcessingDtos.AnomalyDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * S-24 Computational Anomaly (backs S-22's pre-finalisation check): flags
 * any employee whose draft gross pay for the period being finalised differs
 * from their most recent prior finalised payslip's gross by more than the
 * variance threshold - a simple heuristic, not a statistical model, per the
 * roadmap's own "variance threshold heuristic" description.
 */
final class ComputationalAnomalyDetector {

    private static final BigDecimal THRESHOLD_PERCENT = BigDecimal.valueOf(25);

    private ComputationalAnomalyDetector() {
    }

    static List<AnomalyDTO> detect(PayrollStubStore store, int periodNumber) {
        List<AnomalyDTO> anomalies = new ArrayList<>();
        for (Map.Entry<EmployeeId, PayEntryDTO> entry : store.draftPayEntries.entrySet()) {
            EmployeeId empId = entry.getKey();
            EmployeeRecordDTO rec = store.employees.get(empId);
            if (rec == null) {
                continue;
            }
            BigDecimal thisGross = GrossPayAssembler.computeGrossPay(entry.getValue());
            BigDecimal priorGross = latestPriorPayslipGross(store, empId, periodNumber);
            if (priorGross == null || priorGross.signum() == 0) {
                continue;
            }
            BigDecimal pctChange = thisGross.subtract(priorGross)
                    .divide(priorGross, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
            if (pctChange.abs().compareTo(THRESHOLD_PERCENT) > 0) {
                String name = rec.personalDetails().surname() + ", " + rec.personalDetails().firstName();
                String reason = pctChange.signum() > 0
                        ? "Gross pay increased by more than " + THRESHOLD_PERCENT + "% vs prior period"
                        : "Gross pay decreased by more than " + THRESHOLD_PERCENT + "% vs prior period";
                anomalies.add(new AnomalyDTO(empId, name, thisGross, priorGross, pctChange, reason));
            }
        }
        return anomalies;
    }

    private static BigDecimal latestPriorPayslipGross(PayrollStubStore store, EmployeeId id, int periodNumber) {
        PayslipRecordDTO latest = null;
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.employeeId().equals(id) && p.periodNumber() < periodNumber) {
                if (latest == null || p.periodNumber() > latest.periodNumber()) {
                    latest = p;
                }
            }
        }
        return latest == null ? null : latest.gross();
    }
}

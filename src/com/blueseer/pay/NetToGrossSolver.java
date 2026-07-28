package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayProcessingDtos.NetToGrossResultDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * C-20 Net-to-Gross (S-20): binary-searches a flat gross figure through
 * {@link PayslipEngineChain} until its resulting net pay lands within one
 * cent of the target, since PAYE/PRSI/USC are monotonically non-decreasing
 * in gross pay for a fixed period/basis. Every iteration is recorded in the
 * returned trail per C-20's audit-trail requirement - this is not a
 * performance optimisation hidden from the user, the trail itself is part
 * of the screen's contract (S-20's {@code resultPanel}/audit expectations).
 */
final class NetToGrossSolver {

    private static final int MAX_ITERATIONS = 40;
    private static final BigDecimal CONVERGENCE_TOLERANCE = new BigDecimal("0.01");

    private NetToGrossSolver() {
    }

    static NetToGrossResultDTO solve(EmployeeRecordDTO rec, int periodNumber, BigDecimal targetNet,
            IPayrollCalculationService calcService, PayFrequency frequencyOverrideOrNull, RpnDtos.RpnDataDTO rpnDataOrNull) {
        List<String> trail = new ArrayList<>();
        BigDecimal low = BigDecimal.ZERO;
        BigDecimal high = targetNet.multiply(BigDecimal.valueOf(3)).max(BigDecimal.valueOf(100));
        BigDecimal mid = high;
        PayslipEngineChain.ChainResult chain = null;

        for (int i = 1; i <= MAX_ITERATIONS; i++) {
            mid = low.add(high).divide(BigDecimal.valueOf(2), 10, RoundingMode.HALF_UP);
            PayEntryDTO probe = new PayEntryDTO(rec.id(), periodNumber, null, null, null, null, null, mid, null, 0, false, null, null);
            chain = PayslipEngineChain.run(rec, probe, periodNumber, LocalDate.now(), calcService, frequencyOverrideOrNull, rpnDataOrNull);
            trail.add("Iteration " + i + ": gross=" + mid.setScale(2, RoundingMode.HALF_UP) + " -> net=" + chain.net());

            if (chain.net().subtract(targetNet).abs().compareTo(CONVERGENCE_TOLERANCE) <= 0) {
                break;
            }
            if (chain.net().compareTo(targetNet) < 0) {
                low = mid;
            } else {
                high = mid;
            }
        }

        return new NetToGrossResultDTO(mid.setScale(2, RoundingMode.HALF_UP), chain.paye().amount(),
                chain.prsi().employee().amount(), chain.usc().amount(), chain.net(), trail);
    }
}

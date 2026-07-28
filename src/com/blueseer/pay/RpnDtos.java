package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** DTOs backing S-14/S-15/S-16 (RPN request/retrieval/log). */
public final class RpnDtos {

    private RpnDtos() {
    }

    /** S-14's tblRpnResult contents, and S-06's saved-into-emp_rpn_ie payload. */
    public record RpnDataDTO(BigDecimal annualTaxCredit, BigDecimal annualCutOffPoint, String prsiClass) {
    }

    /** Sealed outcome so S-14's view can route to the exact right CardLayout card, not just a boolean. */
    public sealed interface RpnRequestOutcome {
        record Success(RpnDataDTO data) implements RpnRequestOutcome {
        }

        record RegistrationRequired() implements RpnRequestOutcome {
        }

        record NoPpsBlocked() implements RpnRequestOutcome {
        }

        record FirstEmploymentBlocked() implements RpnRequestOutcome {
        }

        record ServiceError(String message) implements RpnRequestOutcome {
        }
    }

    /** One row of S-15's tblRpnResults. */
    public record RpnDiffDTO(
            EmployeeId employeeId,
            BigDecimal oldCredit, BigDecimal newCredit,
            BigDecimal oldCutOff, BigDecimal newCutOff,
            boolean changed) {
    }

    public record BulkRpnResultDTO(List<RpnDiffDTO> diffs, boolean noChangesFound) {
    }

    /** S-16's filter row (dcDateFrom/dcDateTo/cbEmployeeFilter). */
    public record RpnLogFilterDTO(CompanyId companyId, LocalDate dateFrom, LocalDate dateTo, EmployeeId employeeIdOrNull) {
    }

    public record RpnLogEntryDTO(LocalDate date, EmployeeId employeeId, String requestType, String result) {
    }
}

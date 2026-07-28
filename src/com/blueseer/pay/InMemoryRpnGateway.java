package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.RpnDtos.BulkRpnResultDTO;
import com.blueseer.pay.RpnDtos.RpnDataDTO;
import com.blueseer.pay.RpnDtos.RpnDiffDTO;
import com.blueseer.pay.RpnDtos.RpnLogEntryDTO;
import com.blueseer.pay.RpnDtos.RpnLogFilterDTO;
import com.blueseer.pay.RpnDtos.RpnRequestOutcome;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Track A/B/C stub for {@link IRpnGateway}, backed by {@link
 * PayrollStubStore}. There is no real connection to Revenue's ROS test
 * environment in this build (roadmap §3.3's own deferral note: real RPN
 * retrieval needs Revenue-issued test credentials this session doesn't
 * have) - every outcome below is simulated, not fetched. Where a simulated
 * "successful" RPN response needs concrete credit/cut-off figures, this uses
 * Revenue's own published 2026 single-person defaults (&euro;2,000 Personal
 * Tax Credit + &euro;2,000 Employee/PAYE Tax Credit = &euro;4,000; &euro;44,000
 * standard rate cut-off point) rather than an invented number - a real
 * individual's actual RPN would differ by marital status, other credits
 * claimed, and multiple employments, none of which this stub models.
 */
final class InMemoryRpnGateway implements IRpnGateway {

    private static final BigDecimal SINGLE_PERSON_ANNUAL_CREDIT = new BigDecimal("4000");
    private static final BigDecimal SINGLE_PERSON_ANNUAL_CUTOFF = new BigDecimal("44000");
    private static final String DEFAULT_PRSI_CLASS = "A1";

    private final PayrollStubStore store;

    InMemoryRpnGateway(PayrollStubStore store) {
        this.store = store;
    }

    @Override
    public RpnRequestOutcome requestNewStarterRpn(EmployeeId id) {
        EmployeeRecordDTO rec = store.employees.get(id);
        if (rec == null) {
            return new RpnRequestOutcome.ServiceError("Employee not found");
        }
        String pps = rec.personalDetails().ppsNumber();
        if (pps == null || pps.isBlank()) {
            logEntry(id, "New Starter RPN Request", "Blocked - No PPS Number");
            return new RpnRequestOutcome.NoPpsBlocked();
        }
        if (!store.employmentRegistrationConfirmed.contains(id)) {
            logEntry(id, "New Starter RPN Request", "Registration Required");
            return new RpnRequestOutcome.RegistrationRequired();
        }
        RpnDataDTO data = new RpnDataDTO(SINGLE_PERSON_ANNUAL_CREDIT, SINGLE_PERSON_ANNUAL_CUTOFF, DEFAULT_PRSI_CLASS);
        logEntry(id, "New Starter RPN Request", "Success");
        return new RpnRequestOutcome.Success(data);
    }

    @Override
    public RpnRequestOutcome confirmEmploymentRegistration(EmployeeId id) {
        store.employmentRegistrationConfirmed.add(id);
        RpnDataDTO data = new RpnDataDTO(SINGLE_PERSON_ANNUAL_CREDIT, SINGLE_PERSON_ANNUAL_CUTOFF, DEFAULT_PRSI_CLASS);
        logEntry(id, "Employment Registration Confirmation", "Success");
        return new RpnRequestOutcome.Success(data);
    }

    @Override
    public void applyRpnData(EmployeeId id, RpnDataDTO data) {
        store.appliedRpnData.put(id, data);
        logEntry(id, "RPN Applied", "Success");
    }

    @Override
    public BulkRpnResultDTO retrieveBulkRpns(CompanyId id) {
        store.lastBulkRpnRetrievalDate = LocalDate.now();
        List<RpnDiffDTO> diffs = new ArrayList<>();
        for (EmployeeRecordDTO rec : store.employees.values()) {
            if (store.leaveDates.containsKey(rec.id())) {
                continue;
            }
            String pps = rec.personalDetails().ppsNumber();
            if (pps == null || pps.isBlank()) {
                continue;
            }
            RpnDataDTO existing = store.appliedRpnData.get(rec.id());
            BigDecimal oldCredit = existing == null ? BigDecimal.ZERO : existing.annualTaxCredit();
            BigDecimal oldCutOff = existing == null ? BigDecimal.ZERO : existing.annualCutOffPoint();
            boolean changed = existing == null;
            if (changed) {
                diffs.add(new RpnDiffDTO(rec.id(), oldCredit, SINGLE_PERSON_ANNUAL_CREDIT, oldCutOff, SINGLE_PERSON_ANNUAL_CUTOFF, true));
            }
        }
        logEntry(null, "Bulk RPN Retrieval", diffs.isEmpty() ? "No Changes" : diffs.size() + " Changed");
        return new BulkRpnResultDTO(diffs, diffs.isEmpty());
    }

    @Override
    public void applyBulkRpnChanges(List<RpnDiffDTO> selected) {
        for (RpnDiffDTO diff : selected) {
            store.appliedRpnData.put(diff.employeeId(), new RpnDataDTO(diff.newCredit(), diff.newCutOff(), DEFAULT_PRSI_CLASS));
            logEntry(diff.employeeId(), "Bulk RPN Applied", "Success");
        }
    }

    @Override
    public List<RpnLogEntryDTO> getRpnLog(RpnLogFilterDTO filter) {
        List<RpnLogEntryDTO> result = new ArrayList<>();
        for (RpnLogEntryDTO entry : store.rpnLog) {
            if (filter.dateFrom() != null && entry.date().isBefore(filter.dateFrom())) {
                continue;
            }
            if (filter.dateTo() != null && entry.date().isAfter(filter.dateTo())) {
                continue;
            }
            if (filter.employeeIdOrNull() != null && !filter.employeeIdOrNull().equals(entry.employeeId())) {
                continue;
            }
            result.add(entry);
        }
        return result;
    }

    @Override
    public boolean isRpnOverdueForCurrentPeriod(CompanyId id) {
        Set<EmployeeId> activeWithoutRpn = new LinkedHashSet<>();
        for (EmployeeRecordDTO rec : store.employees.values()) {
            if (store.leaveDates.containsKey(rec.id())) {
                continue;
            }
            String pps = rec.personalDetails().ppsNumber();
            if (pps == null || pps.isBlank()) {
                continue;
            }
            if (!store.appliedRpnData.containsKey(rec.id())) {
                activeWithoutRpn.add(rec.id());
            }
        }
        if (activeWithoutRpn.isEmpty()) {
            return false;
        }
        return store.lastBulkRpnRetrievalDate == null || store.lastBulkRpnRetrievalDate.isBefore(LocalDate.now());
    }

    private void logEntry(EmployeeId idOrNull, String requestType, String result) {
        store.rpnLog.add(new RpnLogEntryDTO(LocalDate.now(), idOrNull, requestType, result));
    }
}

package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.CsoDetailsDTO;
import com.blueseer.pay.EmployeeDtos.CumulativesDTO;
import com.blueseer.pay.EmployeeDtos.DepartmentDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeRecordDTO;
import com.blueseer.pay.EmployeeDtos.HrDetailsDTO;
import com.blueseer.pay.EmployeeDtos.PayMethod;
import com.blueseer.pay.EmployeeDtos.PersonalDetailsDTO;
import com.blueseer.pay.EmployeeDtos.RevenueDetailsDTO;
import com.blueseer.pay.PayProcessingDtos.PayEntryDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PayrollIds.DepartmentId;
import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.PayslipId;
import com.blueseer.pay.PayrollIds.PsrBatchId;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * In-memory backing store shared by {@link InMemoryEmployeeRepository},
 * {@link InMemoryRevenueDetailsService}, {@link InMemoryAdditionDeductionService},
 * and {@link InMemoryDepartmentController} - Track A's stub, per the roadmap's
 * "built against stub/mock implementations... so visual work never blocks
 * on domain logic." Not persisted; reset every time the app restarts.
 */
final class PayrollStubStore {

    // S-02 Additional PAYE Registrations / S-03 Digital Certificate Manager.
    record PayeRegistrationRecord(PayrollIds.RegistrationId id, String registrationNumber, String description) {
    }

    final List<PayeRegistrationRecord> registrations = new java.util.ArrayList<>();
    final Map<PayrollIds.RegistrationId, String> subCertStatusByRegistration = new LinkedHashMap<>();
    private long nextRegistrationIdValue = 1;

    long nextRegistrationId() {
        return nextRegistrationIdValue++;
    }

    // S-03 Digital Certificate Manager - employer/agent certificate status.
    record CertificateRecord(boolean present, String expiryLabelOrNull) {
    }

    CertificateRecord employerCertificate = null;
    CertificateRecord agentCertificate = null;

    final Map<EmployeeId, EmployeeRecordDTO> employees = new LinkedHashMap<>();
    final Map<EmployeeId, LocalDate> leaveDates = new LinkedHashMap<>();
    final Map<DepartmentId, DepartmentDTO> departments = new LinkedHashMap<>();

    // S-50: the taxable (post-exemption) portion of a termination lump sum,
    // set by ITerminationController.applyToFinalPayslip and consumed exactly
    // once by whichever finalisation path (S-22 or S-49) next processes this
    // employee - injected into PAYE/USC gross but explicitly excluded from
    // the PRSI base, per C-10 Step 9.
    final Map<EmployeeId, BigDecimal> terminationLumpSumTaxable = new LinkedHashMap<>();

    // §3.4 Pay Processing (S-17...S-24) state - one shared period counter
    // across every employee regardless of their individual frequency; see
    // PayslipEngineChain's class comment for why this Track A stub does not
    // yet partition weekly/monthly into separate concurrent runs.
    final Map<EmployeeId, PayEntryDTO> draftPayEntries = new LinkedHashMap<>();
    final Map<PayslipId, PayslipRecordDTO> payslips = new LinkedHashMap<>();
    int currentPeriodNumber = 1;
    int activeTaxYear = 2026;
    LocalDate lastRpnImportDate = null;
    String lastPeriodUpdatedLabel = "(none yet)";
    private long nextPayslipIdValue = 1;

    long nextPayslipId() {
        return nextPayslipIdValue++;
    }

    // S-27 Cycle to Work Scheme (C-11) - last arrangement start date per
    // employee, used for the once-per-4-years eligibility check.
    final Map<EmployeeId, LocalDate> lastCycleToWorkDate = new LinkedHashMap<>();

    // S-55 Small Benefit Exemption usage (5 benefits / €1,500 combined per
    // tax year) - reset only on app restart, like every other Track A stub
    // counter in this class.
    final Map<EmployeeId, Integer> bikBenefitCountThisYear = new LinkedHashMap<>();
    final Map<EmployeeId, BigDecimal> bikBenefitValueThisYear = new LinkedHashMap<>();

    // S-56 Statutory Sick Leave - days used year-to-date per employee, per
    // C-12's annual 5-day entitlement.
    final Map<EmployeeId, Integer> sslDaysUsedThisYear = new LinkedHashMap<>();

    // S-57 Illness Benefit - the DSP-to-employer mandate reimbursement amount
    // is explicitly never payroll money (C-22 Step 3); kept here purely as a
    // standalone bookkeeping/GL note for the employer, never read by any
    // calculation engine.
    final Map<EmployeeId, BigDecimal> illnessBenefitLedgerNotes = new LinkedHashMap<>();

    // S-59/S-60 Pension - C-15's annual relief headroom tracker and S-60's
    // tracing number field. No Sectoral Employment Order coverage field
    // exists on the employee record yet, so CWPS registration is a Track A
    // stub default (every employee treated as registered once that card is
    // selected) rather than a real eligibility lookup.
    final Map<EmployeeId, BigDecimal> pensionCumulativeReliefYtd = new LinkedHashMap<>();
    final Map<EmployeeId, String> pensionTracingNumbers = new LinkedHashMap<>();

    // S-61 Auto-Enrolment (C-14) - employee-initiated participation flags and
    // any pending AEPN correction note.
    final Map<EmployeeId, Boolean> aeHasExistingPensionCoverage = new LinkedHashMap<>();
    final Map<EmployeeId, Boolean> aeOptedOutOrSuspended = new LinkedHashMap<>();
    final Map<EmployeeId, String> aepnPendingNoteOrNull = new LinkedHashMap<>();

    // S-64 Pay Frequency Change - an explicit override once an employee goes
    // through the wizard, taking precedence over GrossPayAssembler's usual
    // hourlyRate/fixedPay-based inference (which can only ever produce
    // WEEKLY or MONTHLY, never FORTNIGHTLY). Threaded through every
    // GrossPayAssembler.inferFrequency call site (PayslipEngineChain and its
    // five callers, InMemoryFinalisationController, InMemoryPayrollCalendarController,
    // PayslipReportDataProvider) so a frequency change actually changes how
    // that employee's pay is calculated, not just what S-64 itself displays.
    final Map<EmployeeId, PayFrequency> employeeFrequencyOverride = new LinkedHashMap<>();

    // S-14/S-15 RPN retrieval - once an employee's tax credit/cut-off has
    // been applied via a real (simulated) RPN response, PayslipEngineChain
    // switches that employee from the brand-new-employee EMERGENCY default
    // to CUMULATIVE basis using these figures, per S-06's own framing
    // ("automatically placed on emergency tax until updated by an RPN").
    final Map<EmployeeId, RpnDtos.RpnDataDTO> appliedRpnData = new LinkedHashMap<>();

    // S-14's one-time "registration required" simulated friction - once an
    // employee's employment registration has been confirmed, subsequent RPN
    // requests go straight to Success.
    final Set<EmployeeId> employmentRegistrationConfirmed = new java.util.LinkedHashSet<>();

    // S-16 RPN Logs - one entry per request/retrieval/apply action.
    final List<RpnDtos.RpnLogEntryDTO> rpnLog = new java.util.ArrayList<>();
    LocalDate lastBulkRpnRetrievalDate = null;

    // S-62 Payroll Journal Mapping - GL account code per payroll category, keyed by accounting target.
    final Map<JournalDtos.AccountingTarget, Map<String, String>> journalMappings = new LinkedHashMap<>();

    // S-68 Revenue Payments Record - a real ledger, but nothing in this
    // build writes to it yet (no "record a Revenue payment" action exists
    // anywhere in the roadmap's §3.12 screens - S-66 explicitly has no such
    // button, since actually paying Revenue happens via ROS, outside
    // BlueSeer). Kept as a genuine, currently-empty list rather than
    // fabricated rows.
    final List<RemittanceDtos.PaymentRecordRowDTO> revenuePayments = new java.util.ArrayList<>();
    private long nextGlBatchRefValue = 1;

    long nextGlBatchRef() {
        return nextGlBatchRefValue++;
    }

    // §3.6 PSR batches - one per finalised period, auto-created by
    // PayPeriodFinaliser in "Due" status; S-31 submits it (-> "Filed"), S-32
    // can also flip status manually via the right-click menu.
    record PsrBatchRecord(PsrBatchId id, LocalDate payDate, List<PayslipId> payslipIds, String status, String revenueReferenceNumberOrNull) {
    }

    final Map<PsrBatchId, PsrBatchRecord> psrBatches = new LinkedHashMap<>();
    private long nextPsrBatchIdValue = 1;

    long nextPsrBatchId() {
        return nextPsrBatchIdValue++;
    }

    /**
     * One store per JVM/app session, not per screen class - S-17 onward
     * (Pay Processing) must see the same employees/departments that Employee
     * Maintenance created, so every no-arg Track A constructor resolves the
     * store through here rather than calling {@link #newDemoStore()} itself.
     */
    static PayrollStubStore shared() {
        return Holder.INSTANCE;
    }

    private static final class Holder {
        private static final PayrollStubStore INSTANCE = PayrollStubStore.newDemoStore();
    }

    static PayrollStubStore newDemoStore() {
        PayrollStubStore store = new PayrollStubStore();

        DepartmentId kitchen = new DepartmentId(1);
        DepartmentId frontOfHouse = new DepartmentId(2);
        store.departments.put(kitchen, new DepartmentDTO(kitchen, "KIT", "Kitchen"));
        store.departments.put(frontOfHouse, new DepartmentDTO(frontOfHouse, "FOH", "Front of House"));

        // The demo's only director (S-25/C-13) - director==true routes her PRSI
        // through Class S instead of Class A, per PayslipEngineChain.
        EmployeeId id1 = new EmployeeId(1);
        store.employees.put(id1, new EmployeeRecordDTO(
                id1,
                new PersonalDetailsDTO(id1, "Murphy", "Siobhan", "12 Grafton Street, Dublin 2",
                        LocalDate.of(1990, 4, 12), "smurphy@example.ie", "", true, kitchen,
                        "1234567A", "EMP-0001", "W001", null, new BigDecimal("3500.00"),
                        PayMethod.CREDIT_TRANSFER, "AIB", "Dublin", "93-11-08", "12345678", ""),
                new RevenueDetailsDTO(id1, LocalDate.of(2024, 1, 15), 3, "A1", List.of()),
                List.of(), List.of(),
                new CumulativesDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new HrDetailsDTO("Accountant", "Permanent", "", ""),
                new CsoDetailsDTO("", "")));

        EmployeeId id2 = new EmployeeId(2);
        store.employees.put(id2, new EmployeeRecordDTO(
                id2,
                new PersonalDetailsDTO(id2, "O'Brien", "Cian", "", null, "cobrien@example.ie", "", false, null,
                        "", "EMP-0002", "W002", new BigDecimal("18.50"), null,
                        PayMethod.CASH, "", "", "", "", ""),
                new RevenueDetailsDTO(id2, LocalDate.of(2026, 6, 1), 22, "A1", List.of()),
                List.of(), List.of(),
                new CumulativesDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new HrDetailsDTO("", "Fixed-Term", "", ""),
                new CsoDetailsDTO("", "")));

        // Former employee (S-13 Leavers Re-joining) - has a leave date and no
        // active employment; searchEmployees() should surface them as
        // isFormerEmployee=true instead of a normal editable match.
        EmployeeId id3 = new EmployeeId(3);
        store.employees.put(id3, new EmployeeRecordDTO(
                id3,
                new PersonalDetailsDTO(id3, "Walsh", "David", "5 Merrion Square, Dublin 2",
                        LocalDate.of(1988, 9, 2), "dwalsh@example.ie", "", false, frontOfHouse,
                        "7654321B", "EMP-0003", "W003", new BigDecimal("22.00"), null,
                        PayMethod.CASH, "", "", "", "", ""),
                new RevenueDetailsDTO(id3, LocalDate.of(2023, 3, 1), 9, "A1", List.of()),
                List.of(), List.of(),
                new CumulativesDTO(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO),
                new HrDetailsDTO("Waiter", "Permanent", "", ""),
                new CsoDetailsDTO("", "")));
        store.leaveDates.put(id3, LocalDate.of(2025, 11, 30));

        return store;
    }
}

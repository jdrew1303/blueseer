# Irish Payroll Module — 2026 Tax Year Architecture & Screen Roadmap

**Status:** Planning only — no application code in scope for this document.
**Exemplar:** Thesaurus Payroll Manager (Ireland) 2026, `thesaurus.ie/docs/2026/` (Bright Software Group).
**Target stack:** Java Swing (NetBeans `.form`/`.java` pattern already used by `com.blueseer.hrm`), Jasper Reports, existing BlueSeer EDI subsystem (`com.blueseer.edi`).
**Existing codebase touchpoints identified:** `com.blueseer.hrm` (`EmployeeMaint`, `hrmData`, `emp_mstr` table already carries `emp_ssn`, `emp_acct`, `emp_routing`, `emp_payfrequency`, `emp_dob`, `emp_startdate`, `emp_termdate`), `com.blueseer.fgl` (general ledger, target for journal posting), `com.blueseer.edi` (target for RPN/PSR/AECS transport), `sf/jasper` (report template store).

Proposed new module package: **`com.blueseer.pay`** (3-letter convention consistent with `hrm`, `fgl`, `inv`, etc.). `emp_mstr` remains the single source of truth for identity; Irish statutory data lives in new linked tables (`emp_pay_ie`, `emp_rpn_ie`, `psr_batch_ie`, …) keyed on `emp_nbr` + `tax_year`, not by cloning the employee master.

---

## 1. Architecture Principles

1. **Business objects are injected into views, not owned by them.** Every screen's Swing form (`XyzUI.form` + `XyzUI.java`) depends only on interfaces (`IEmployeeRepository`, `IPayrollCalculationService`, `IRpnGateway`, `IPsrGateway`, `IReportDataProvider`) supplied via constructor/setter injection. This is what makes the Parallelization Matrix (§4) possible — layout, calculation, and report tracks never block on each other.
2. **One running application, many tax years.** Unlike the exemplar (a fresh installer per tax year with a one-time import wizard), BlueSeer is a persistent multi-module ERP. Tax-year isolation is achieved in-process via a `TaxYearRules` strategy (§5), not via separate installs.
3. **Screens mirror the exemplar's flow**, because that flow encodes real Revenue compliance ordering (you cannot request an RPN before the employee record exists; you cannot submit a PSR before payslips are finalised). Reordering these steps in our UI would reorder compliance obligations, so friction reduction takes a back seat to flow fidelity.
4. **EDI is the transport, not the domain.** All Revenue interactions (RPN request/response, PSR submission, AECS to NAERSA) are modelled as documented request/response DTOs handled by `com.blueseer.edi`; the payroll domain layer never constructs SOAP/XML directly.
5. **Reports are driven by POJO/DTO collections**, never by direct SQL inside `.jrxml`. Each report has a documented "shape" (a Java bean or `List<Bean>`) that the Reports track can build against with mock data before Calculations delivers the real thing.

---

## 2. Navigational Map

### 2.1 The exemplar's spine: 8 Process Icons

Thesaurus organises the entire pay-period lifecycle around eight sequential "Process Icons," each opening a screen and, on completion, unlocking/prompting the next. We mirror this as our primary navigation rail.

| # | Process Icon | Screen it opens | Unlocks |
|---|---|---|---|
| 1 | Add/Amend Employees | Employee Maintenance (tabbed: Personal, Revenue, Additions, Deductions, HR, CSO) | Icon 2 (new starter prompt) |
| 2 | Get RPNs | RPN Retrieval (bulk, each pay period) / RPN Request (new starter) | Icon 3 |
| 3 | Enter Pay | Weekly/Fortnightly/Monthly Input, or Quick Edit Entry, or Net-to-Gross | Icon 4 |
| 4 | Preview | Payroll Preview report | Icon 5 |
| 5 | Update | Finalise Pay Period ("Update Payslips") | Icon 6 |
| 6 | Submit | PSR Prepare & Submit | Icon 7, backup prompt |
| 7 | Distribute | Print/Email Payslips | Icon 8 |
| 8 | Report | Reports hub (Payroll Summary/Audit Trail entry point) | — |

### 2.2 End-to-end user flows

**Flow A — New starter to first payslip**
```
File ▸ Add New Company (once per employer)
   └─ New Company Wizard → Employer Reg No / pay frequency / password
        └─ Digital Certificate setup (ROS cert, employer or agent)

Process Icon 1 "New" → Personal Details tab
   └─ Revenue Details tab (start date, start week, PRSI class, exemptions)
        └─ Additions tab (optional) → Deductions tab (optional)
             └─ HR Details tab (optional) → CSO Details tab (optional)
                  └─ [Update] → record saved

On close of new-employee record → prompt: "Send RPN request?"
Process Icon 2 → RPN Request
   ├─ if PPS number present & employment registered → RPN retrieved → [Update] imports credits/COP
   ├─ if PPS present but employment not yet registered at Revenue → confirm → registers employment → requests RPN
   └─ if no PPS number → employee stays on emergency basis (blocking state, surfaced on this screen)

Process Icon 3 → Weekly/Monthly/Fortnightly Input (or Quick Edit)
Process Icon 4 → Payroll Preview → back to Icon 3 to fix, or continue
Process Icon 5 → Finalise Pay Period ("Update")
Process Icon 6 → PSR Prepare & Submit → Revenue confirmation
   └─ prompt: "Back up payroll data?" → Backup utility
Process Icon 7 → Print/Email Payslips
Process Icon 8 → Reports hub
```

**Flow B — Steady-state pay period (existing employees)**
```
Process Icon 2 → RPN Retrieval (bulk, mandatory prompt each period)
Process Icon 3 → Pay entry → Icon 4 Preview → Icon 5 Finalise → Icon 6 PSR → Icon 7 Distribute
```

**Flow C — Leaver (in current pay run)**
```
Process Icon 3 → select employee → enter final pay items
   └─ tick "leaving" → confirm in-this-run → enter leave date → [Update File]
Process Icon 5 → Finalise (leave date now embedded in this employee's payslip)
Process Icon 6 → PSR (submission includes leave date; Revenue notified in same submission)
```

**Flow D — Leaver (mid pay period, off-cycle)**
```
Processing Leavers ▸ Mid Pay Period → standalone final payslip screen
   └─ finalise → payslip available for print/email immediately (does not wait for the next scheduled run)
```

**Flow E — Correction (post-submission error)**
```
Corrections ▸ Overview → select correction type:
   [Do it all again | New Employee | Payment was different |
    Wrong PPS Number | Wrong PRSI Class | Employee has left]
        └─ routes into the relevant screen (re-open pay period, or Icon 3 entry
           for the next run) applying "follow the money" adjustment logic
        └─ where physical payment differed from processed payroll → Correction PSR
```

**Flow F — Year-end / tax-year rollover**
```
Payroll Calendar reaches Week 52/53 → Reports ▸ Year End Summary
   → Employment Details Summary (P60 replacement) generated per employee
Starting the New Tax Year ▸ "Start 2027" wizard
   → creates a new TaxYearRules context (2027) inside the SAME application instance
   → Company/Employee master data carried forward by reference (not copied);
     2025/2026/2027 payroll transactions remain independently queryable
```

**Flow G — Payroll Journal export**
```
After Finalise (Icon 5) → Utilities ▸ Accounts Export ▸ Mapping (one-time per employer)
   → Create Export File (CSV: Quickbooks/Sage/Xero) OR native GL post to com.blueseer.fgl
```

### 2.3 Screen-relationship diagram

```
 [Company Setup] → [Digital Certificate] → [Employee Maintenance] ⇄ [RPN Retrieval]
                                                    │
                                                    ▼
        [Payroll Calendar] ──context──▶ [Pay Entry: Input / Quick Edit / Net-to-Gross]
                                                    │
                                                    ▼
                                          [Payroll Preview] ──back to Pay Entry──┐
                                                    │                            │
                                                    ▼                            │
                                        [Finalise Pay Period] ◀──────────────────┘
                                                    │
                        ┌───────────────────────────┼───────────────────────────┐
                        ▼                            ▼                           ▼
              [PSR Prepare/Submit]        [Print/Email Payslips]      [Payroll Journal Export]
                        │                            │
                        ▼                            ▼
              [PSR Control Panel]           [Paying Employees:
              [Correction PSR]                Bank File / Cash / Cheque Reporting]
                        │
                        ▼
              [Reports Hub: Audit Trail, Tax Details, Register of
               Employees, Additions/Deductions, Pension, Other,
               Year End Summary]
```

---

## 3. Screen-Level Backlog

Each screen lists: **UI Layout** (Swing components/layout), **Business Objects/Calculations** (domain logic injected in), **Associated Reports** (Jasper templates). Screen IDs are stable references for task dispatch.

**Detailed component-tree/event-listener/controller-interface specs** for every screen below (S-01 through S-70) live in [irish-payroll-2026-screen-specs.md](irish-payroll-2026-screen-specs.md).

### 3.1 Company & Certificate Setup

| ID | Screen | UI Layout | Business Objects / Calculations | Reports |
|---|---|---|---|---|
| S-01 | New Company Wizard | `JWizard`-style multi-step `CardLayout` panel: company name/address (`JTextField`×n), Employer Registered Number (`JFormattedTextField`, 7-digit+1-2 letter mask), pay-frequency checkboxes (weekly+monthly XOR fortnightly+monthly — enforce via `ButtonGroup` logic in layout, validated by service), password fields (`JPasswordField`×2, min 4 alphanumeric) | `EmployerRegistrationValidator` (PAYE reg-number format check), `CompanyService.create()` writes employer row + initializes `TaxYearRules` binding for current year | — |
| S-02 | Additional PAYE Registrations | `JTable` of registered numbers + Add/Remove buttons | `EmployerRegistrationService` (supports multi-registration employers, e.g. group structures) | Employer registration listing |
| S-03 | Digital Certificate Manager | Tabs: Employer cert / Agent cert; file chooser + password field; sub-cert list `JTable` for additional PAYE registrations | `RosCertificateStore` (secure at-rest storage, cert↔employer binding), `RosCertificateValidator` | — |

### 3.2 Employee Master (Process Icon 1)

| ID | Screen | UI Layout | Business Objects / Calculations | Reports |
|---|---|---|---|---|
| S-04 | Employee Maintenance — shell | `JTabbedPane` host (Personal / Revenue / Additions / Deductions / Departments / HR / CSO / Mid-Year Cumulatives), Surname `JComboBox` selector + New/Update/Delete buttons — extends existing `EmployeeMaint.form` pattern | `IEmployeeRepository` (wraps `emp_mstr` + new `emp_pay_ie`), `EmployeeValidationService` | Register of Employees (S-31) |
| S-05 | Personal Details tab | `GroupLayout` form: Surname, First Name, Address (multi-line), DOB (`JDateChooser`), Email/Password, Connect-access checkbox *(exclude wiring — see §6)*, Director checkbox, Department `JComboBox`, PPS Number field, Employment ID (read-only, auto-populated), Works Number, Hourly Rate / Weekly-Monthly-Fortnightly Pay (mutually exclusive), Pay Method radio (Cash/Cheque/Credit Transfer) revealing Bank/Branch/Sort Code/Account Number/Credit Union Ref | `PpsNumberValidator` (modulus check; invalid → blank + downstream address/DOB become mandatory), `EmploymentIdentifierGenerator`, `SepaAccountConverter` (Sort Code + 8-digit account → BIC/IBAN, no manual entry) | — |
| S-06 | Revenue Details tab | Start Date (`JDateChooser`), Start Week (auto-derived from Payroll Calendar, editable), PRSI Class `JComboBox` (+ inline help link), Exemption/Exclusion checkboxes, read-only "Emergency Basis" status banner | `PrsiClassRules` (2026 class table), `EmergencyTaxPolicy` (auto-applied until RPN received), `RevenueDetailsService` — **manual entry of credits/COP is deliberately not permitted**, mirroring the exemplar's RPN-only model | — |
| S-07 | Additions tab | Repeating row grid (`JTable`, editable): Description, Taxable/Non-Taxable toggle, Amount; "zero out next period" reminder banner | `AdditionCalculationEngine` (taxable additions flow into PAYE/PRSI/USC gross; non-taxable bypass), `AdditionEndDateService` (S-07a below) | Additions/Deductions Report (S-33) |
| S-07a | Addition/Deduction End-Date utility | Modal dialog: pick addition/deduction row, end date picker | `RecurringItemScheduler` | — |
| S-08 | Deductions tab | Same grid pattern as S-07: Description, pre-tax/post-tax toggle, Amount | `DeductionCalculationEngine` (pension, union dues, attachment of earnings, etc.) | Additions/Deductions Report (S-33) |
| S-09 | Departments Maintenance | `JList`/`JTable` CRUD of department codes+names | `DepartmentService` — feeds department filters on S-14, S-25, S-31 | — |
| S-10 | Mid-Year Cumulatives | Form: prior pay/tax/PRSI/USC paid this year (for employees joining mid-year with history), leave-date display (read-only, populated post-leaver-processing) | `CumulativeRecordService` | — |
| S-11 | HR Details tab | Free-form fields: job title, contract type, emergency contact, etc. | `HrDetailService` (non-statutory, informational) | — |
| S-12 | CSO Details tab | Fields required for CSO/EHECS reporting (occupation code, hours category) | `CsoClassificationService` | EHECS extract (S-38) |
| S-13 | Leavers Re-joining | Variant of S-04/S-05 flow pre-populated from prior employment record | `RejoinerReconciliationService` (new Employment ID, prior cumulative linkage) | — |

### 3.3 Revenue Payroll Notifications (Process Icon 2)

| ID | Screen | UI Layout | Business Objects / Calculations | Reports |
|---|---|---|---|---|
| S-14 | RPN Request (new starter) | Result panel: employer-registration-required prompt (Yes/No confirm dialog), retrieved credits/COP summary, `[Update]` button | `RpnRequestGateway` (via `com.blueseer.edi`), `EmploymentRegistrationService`, blocking rule: *no PPS → no RPN possible; first-ever Irish employment → employee must self-register via Jobs & Pensions before RPN exists* (surfaced as an actionable message, not silently retried) | RPN Details print |
| S-15 | RPN Retrieval (bulk, each period) | Pre-payroll modal: "Retrieve RPNs" trigger, results `JTable` (employee, old vs new credits/COP), "no changes found" state, `[Print RPN Details]`, `[Update]` | `BulkRpnSyncService`, `RpnDiffCalculator` | RPN Details print |
| S-16 | RPN Logs & Reminders | `JTable` history of RPN requests/responses per employee, overdue-reminder banner if no RPN pulled before a finalised run | `RpnAuditLogService` | RPN Log report |

### 3.4 Payroll Calendar & Pay Processing (Process Icon 3–5)

| ID | Screen | UI Layout | Business Objects / Calculations | Reports |
|---|---|---|---|---|
| S-17 | Payroll Calendar | Read-only `JTable`: Week/Period No, From, To, per pay frequency, current-period highlight | `PayrollCalendarService` (statutory week/period boundaries for the active `TaxYearRules`) | Payroll Calendar print |
| S-18 | Weekly/Monthly/Fortnightly Input | Single-employee form: hourly rate + Standard/Time-and-a-third/Time-and-a-half/Double-time hour fields (mutually exclusive with flat Weekly/Monthly Basic), Holiday Pay + "additional weeks" spreader, Additions/Deductions summary, leaver checkbox + leave-date, department filter dropdown, Next/Update File nav | `GrossPayAssembler`, `HolidayPaySpreadEngine` (locks future weeks, auto-reactivates), `LeaverFlagService` | Payslip preview (draft) |
| S-19 | Quick Edit Entry | `JTable` grid, one row per employee, inline-editable pay columns, double-click to drill into S-18 for advanced fields (notes, etc. — explicitly NOT available in grid mode, matching exemplar) | Same `GrossPayAssembler` as S-18 (shared service, two views) | — |
| S-20 | Net to Gross Payments | Form: target net amount → derived gross | `NetToGrossSolver` (iterative PAYE/PRSI/USC solve for target take-home) | — |
| S-21 | Payroll Preview | Read-only tabular report, period number banner, double-click employee → jumps to S-18/S-19 to correct | `PayrollPreviewAggregator` (pulls current in-progress period across all employees) | Payroll Preview report (screen + print/HTML/email) |
| S-22 | Finalise Pay Period ("Update Payslips") | Summary panel: last RPN import date, last period updated (read-only), period being processed (read-only, system-derived), editable pay date, optional weekly payslip reference note; `[Update]` → `[OK]` confirmation | `PayPeriodFinaliser` (locks the period, computes final PAYE/PRSI/USC/LPT/ASC per employee, persists payslip records, auto-generates the pending PSR) | Finalisation summary |
| S-23 | Payslip Workings | Drill-down detail: line-by-line PAYE/PRSI/USC calculation steps for one payslip (audit/explainability view) | `PayslipWorkingsExplainer` (renders intermediate values from the calc engines, not a separate calculation) | Payslip Workings report |
| S-24 | Computational Anomaly | Warning dialog listing employees whose calculated result looks statistically anomalous vs. prior period | `ComputationalAnomalyDetector` (variance threshold heuristic) | Anomaly list |
| S-25 | Directors Fees | Specialised entry variant (proprietary director PRSI/USC treatment) | `DirectorPayCalculationEngine` | — |
| S-26 | Share-Based Remuneration | Entry form for RSU/share award taxable events | `ShareRemunerationCalculator` | — |
| S-27 | Cycle to Work Scheme | Salary-sacrifice setup form | `CycleToWorkDeductionEngine` (exemption cap rules) | — |
| S-28 | Importing Hours (Text/CSV) | File chooser + column-mapping grid + preview/validate table | `HoursImportParser`, `ImportValidationService` | Import exceptions report |
| S-29 | Full Periodic CSV Import | File chooser + mapping wizard for a complete period's pay data | `FullPeriodCsvImporter` | Import summary |
| S-30 | Week 53 handling | Contextual banner/wizard appearing only when calendar produces a 53rd weekly pay date | `Week53DetectionService`, `Week53TaxTreatment` (statutory non-cumulative treatment) | — |

### 3.5 Payroll Deductions Engine (no dedicated screen — injected calculation services)

| ID | Component | Business Objects / Calculations |
|---|---|---|
| C-01 | PAYE Engine | `PayeCalculator` — cumulative & Week1/Month1 basis, tax credits, standard rate cut-off point, emergency basis table |
| C-02 | PRSI Engine | `PrsiCalculator` — per-class (A, A1, J, S, H, etc.) employee/employer rates, weekly PRSI-free allowance/credit tapering |
| C-03 | USC Engine | `UscCalculator` — rate bands, USC-exempt **status** vs USC-exempt **income** distinction, USC surcharge for certain classes |
| C-04 | LPT Engine | `LptDeductionService` — deducted per Revenue-instructed amount from RPN, not independently calculated |
| C-05 | ASC Engine | `AscCalculator` — Additional Superannuation Contribution (public-sector pensionable pay) |
| C-06 | Tax-Year Rules Registry | `TaxYearRules` interface, `Paye2025Rules` / `Paye2026Rules` implementations — see §5 |

### 3.6 Payroll Submission Requests (Process Icon 6)

| ID | Screen | UI Layout | Business Objects / Calculations | Reports |
|---|---|---|---|---|
| S-31 | PSR Prepare & Submit | Summary panel: PAYE/USC/PRSI/LPT totals, `[View]` detail drill-in, `[Submit PSR to Revenue]`, submission confirmation (Revenue reference + totals echoed back) | `PsrBuilder` (assembles submission payload from finalised payslips), `PsrGateway` (via `com.blueseer.edi`) | PSR summary / detail |
| S-32 | PSR Control Panel | `JTable`: pay date, payslip/correction count, count returned, status (Filed/Due); right-click "mark as sent"/"mark as not sent"; multi-select submit for >1 outstanding | `PsrControlPanelService`, `PsrStatusReconciler` | PSR status listing |
| S-33 | Correction PSR wizard | `CardLayout` wizard keyed off correction type (Do it all again / New Employee / Payment was different / Wrong PPS / Wrong PRSI Class / Employee has left) | `CorrectionRoutingService`, `FollowTheMoneyAdjuster` (applies under/overpayment correction to the *next* live run rather than mutating history), `CorrectionPsrBuilder` | Correction summary |
| S-34 | Recheck Payroll Submissions | Action screen: re-query Revenue for submission status | `PsrRecheckService` | — |
| S-35 | Check Revenue Record | Query panel comparing local vs Revenue-held employee/employer record | `RevenueRecordComparator` | Discrepancy report |

### 3.7 Distribution & Payment (Process Icon 7)

| ID | Screen | UI Layout | Business Objects / Calculations | Reports |
|---|---|---|---|---|
| S-36 | Print/Email Payslips | Period selector, employee multi-select list (+ "Select All" / by-department), payslip-count spinner, "include zero-payment" checkbox, payslip-type `JComboBox` (2-per-page hi/lo-res, payslip & cheque, laser security) | `PayslipDistributionService`, `PayslipRenderSelector` | **Payslip** (multiple `.jrxml` layouts per stationery type) |
| S-37 | Emailing Payslips | Email-specific options: password-protect PDF, employer "from" address (from Company Details) | `SecurePayslipMailer` | Payslip (PDF, emailed) |
| S-38 | Paying Employees — Bank File (SEPA) | Wizard: source account, payment date, employee selection, file-format target (standard SEPA / non-Irish IBAN variant / Bankline / Modulr) | `SepaPaymentFileBuilder` (variants: generic SEPA `pain.001`, Bankline flat-file, Modulr API payload) | Bank payment listing |
| S-39 | Paying Employees — Reporting | Tabs: Pay Method Summary (cash/cheque/credit-transfer breakdown), Cash Requirement Summary | `PayMethodAggregator` | Pay Method Summary, Cash Summary |

### 3.8 Reports Hub (Process Icon 8)

| ID | Screen | UI Layout | Business Objects / Calculations | Reports |
|---|---|---|---|---|
| S-40 | Payroll Summary / Audit Trail | Period range picker (From/To), Directors-only / exclude-Directors toggle, sort-alphabetically prompt, summary-only toggle, Print/Copy/HTML/Email actions | `AuditTrailAggregator` | Payroll Summary/Audit Trail |
| S-41 | Tax Details Report (Monthly/Quarterly) | Period-range picker | `TaxDetailsAggregator` | Tax Details Report |
| S-42 | Register of Employees | Filterable employee listing | `EmployeeRegisterService` | Register of Employees |
| S-43 | Additions/Deductions Reports | Period-range picker, addition/deduction type filter | `AdditionsDeductionsAggregator` | Additions/Deductions report |
| S-44 | Pension Reports | Sub-tabs: Normal / CWPS / NECI | `PensionReportAggregator` (per scheme type) | Normal/CWPS/NECI Pension reports |
| S-45 | Other Reports | Sub-selector: Hours Worked/Overtime, Holiday Pay, Notional Pay, ASC | `OtherReportsAggregator` | Hours/Overtime, Holiday Pay, Notional Pay, ASC reports |
| S-46 | Year End Summary | Period-independent, whole-tax-year aggregation | `YearEndAggregator` | Year End Summary |
| S-47 | Employment Details Summary (P60 replacement) | Per-employee generation, batch mode | `EmploymentDetailsSummaryGenerator` | Employment Details Summary |

### 3.9 Leavers (Processing Leavers section)

| ID | Screen | UI Layout | Business Objects / Calculations | Reports |
|---|---|---|---|---|
| S-48 | Leaver (in current run) | Embedded in S-18 — leaving checkbox + confirm dialog + leave-date field | `LeaverFlagService` (shared with S-18) | — |
| S-49 | Leaver (mid pay period) | Standalone single-employee final-payslip screen (bypasses the normal period cycle) | `OffCycleFinaliser` | Final payslip |
| S-50 | Leaver — taxable/non-taxable lump sum | Termination-payment entry: statutory exemption vs. taxable excess split | `TerminationLumpSumCalculator` (SCSB/exemption logic) | Lump sum statement |
| S-51 | Post-Cessation Payment (current year) | Late payment to an already-left employee within the same tax year | `PostCessationPaymentService` | Payslip (post-cessation) |
| S-52 | Post-Cessation Payment (out of year) | Same, but prior tax year — requires `TaxYearRules` for the *closed* year | `PostCessationPaymentService` (parameterised by historical `TaxYearRules`) | Payslip (post-cessation, prior year) |

### 3.10 Benefit in Kind, Sick Pay, Parenting Benefits, Pensions

| ID | Screen | UI Layout | Business Objects / Calculations | Reports |
|---|---|---|---|---|
| S-53 | BIK — Cars & Vans | Form: OMV, CO2 band, business KM, private use % | `BikCarCalculator` (2026 CO2-banded rates), `BikVanCalculator` | BIK statement |
| S-54 | BIK — Preferential Loans | Loan principal, employer rate vs specified rate | `BikLoanCalculator` | BIK statement |
| S-55 | BIK — Annual / One-Off Benefits | Simple amount + category entry | `BikBenefitCalculator` | BIK statement |
| S-56 | Statutory Sick Pay setup/operation | Per-employee SSP day tracking, waiting-period logic | `SspEntitlementEngine` (statutory day count, employer top-up policy) | SSP report |
| S-57 | Illness Benefit handling | Employer-pays / employer-does-not-pay / employer-receives-IB-payment variants | `IllnessBenefitReconciliationService` | Illness Benefit report |
| S-58 | Parenting Benefits (Maternity/Paternity/Parent's) | Benefit-period entry, top-up policy | `ParentingBenefitCalculator` | Parenting Benefit report |
| S-59 | Pension Deduction setup | Standard/CWPS/NECI scheme selector, contribution % or fixed amount, employer match | `PensionDeductionEngine` (per-scheme rate tables) | Pension Reports (S-44) |
| S-60 | Pension Tracing Number entry | Simple field on employee record | `PensionTracingService` | — |
| S-61 | Auto-Enrolment (MyFuture Fund) | AE eligibility flag, contribution tier display, AECS submission trigger, AEPN handling, in-app correction workflow | `AutoEnrolmentEligibilityEngine`, `AecsSubmissionGateway` (via `com.blueseer.edi` → NAERSA), `AepnHandler` | AE contribution summary |

### 3.11 Journals, Pay Frequency, Year Transition

| ID | Screen | UI Layout | Business Objects / Calculations | Reports |
|---|---|---|---|---|
| S-62 | Payroll Journal Mapping | `JTable`: payroll category ↔ GL account code, per accounting-system target (Quickbooks/Sage/Xero mapping *or* native `com.blueseer.fgl` account) | `JournalMappingService` | — |
| S-63 | Journal Export | `[Create Export File]` action → file save dialog | `JournalExportBuilder` (CSV) **or** `NativeGlPoster` (direct `com.blueseer.fgl` journal entry) | Journal export (CSV) |
| S-64 | Change Employee Pay Frequency | Confirmation wizard (weekly↔fortnightly↔monthly), mid-year proration warning | `PayFrequencyChangeService` (recalculates cumulative basis) | — |
| S-65 | Start New Tax Year | Wizard: confirm current year finalised (incl. Week 53 check), instantiate next `TaxYearRules`, carry forward company/employee references | `TaxYearRolloverService` (see §5) | Year-end completeness checklist |

### 3.12 Revenue Payments / Remittance

| ID | Screen | UI Layout | Business Objects / Calculations | Reports |
|---|---|---|---|---|
| S-66 | Remittance to Revenue — Overview | Dashboard: amount due, due date, payment status | `RemittanceSummaryService` | Tax Details Report (S-41) |
| S-67 | Payment Due Dates | Read-only calendar of statutory due dates | `PaymentDueDateCalculator` | — |
| S-68 | Revenue Payments Record | `JTable` history of payments made | `RevenuePaymentLedger` | Revenue Payments Record |
| S-69 | Returns Look-Up | Query/search of historical returns | `ReturnsLookupService` | — |
| S-70 | Query Revenue Record | Discrepancy query tool | `RevenueRecordComparator` (shared with S-35) | Discrepancy report |

---

## 4. Parallelization Matrix

The decoupling strategy: every screen in §3 is split into **up to four independently assignable work packages**. A screen is "wireable" once its Layout, Calculation/Data-Model, and Report packages each expose the interface documented in that screen's row above.

| Track | Scope | Depends on | Can start immediately? | Output contract |
|---|---|---|---|---|
| **A — Layouts** | All `.form`/`.java` Swing screens in §3. Built against **stub/mock implementations** of the injected interfaces (hardcoded sample data) so visual work never blocks on domain logic. | Interface signatures only (defined up front, see §4.1) | ✅ Yes, once interfaces are frozen | Compiled Swing panels wired to stub services, screenshot-reviewable |
| **B — Calculations & Data Models** | `C-01`…`C-06` engines, all `*Service`/`*Calculator`/`*Engine`/`*Aggregator` classes, DB schema (`emp_pay_ie`, `emp_rpn_ie`, `psr_batch_ie`, etc.), unit tests against published Revenue test vectors | Interface signatures only | ✅ Yes, parallel to A | Interface implementations + JUnit suite, no UI dependency |
| **C — Reports** | All `.jrxml` templates in §3's Reports columns | Documented DTO/bean shape per report (frozen up front) | ✅ Yes, using a `JRBeanCollectionDataSource` fed with hand-built sample beans | Compiled `.jasper` files previewable via JasperViewer standalone, no app dependency |
| **D — EDI/Revenue Integration** | `RpnRequestGateway`, `BulkRpnSyncService`, `PsrGateway`, `AecsSubmissionGateway`, ROS SOAP/XML schema mapping | `com.blueseer.edi` transport conventions | ✅ Yes, parallel to A/B/C (mock Revenue responses using published ROS test-system payloads) | Gateway implementations satisfying Track B's interfaces |
| **E — Wiring** | Replace stub implementations in Track A with real Track B/D services; bind Track C reports to real data providers; end-to-end flow testing per §2.2 | A + B + C + D substantially complete for a given screen | ❌ No — this is deliberately the last step per screen/flow | Fully integrated, demoable screen |

### 4.1 Interfaces to freeze before dispatch (prerequisite for A/B/C/D to run in parallel)

These are the contracts that must be agreed first — treat this as the first ticket, blocking all four tracks:

- `IEmployeeRepository`, `IRevenueDetailsService`, `IAdditionDeductionService`
- `IRpnGateway` (request + bulk retrieve), `IPsrGateway` (prepare/submit/control-panel/correction)
- `IPayrollCalculationService` (facade over C-01…C-06, one call per payslip line item)
- `ITaxYearRules` (see §5)
- `IReportDataProvider<T>` per report DTO (one per row in the Reports columns of §3)
- `IJournalExportTarget` (CSV mapping vs. native `fgl` post)

### 4.2 Suggested track sequencing by functional area

| Functional area (§3 section) | A (Layouts) | B (Calc/Data) | C (Reports) | D (EDI) | E (Wiring) |
|---|---|---|---|---|---|
| 3.1 Company/Cert Setup | Sprint 1 | Sprint 1 | — | Sprint 1 (cert transport) | Sprint 2 |
| 3.2 Employee Master | Sprint 1 | Sprint 1 | Sprint 1 (Register) | — | Sprint 2 |
| 3.3 RPN | Sprint 1 | Sprint 1 | Sprint 1 | Sprint 1 | Sprint 2 |
| 3.4 Pay Processing + 3.5 Calc Engine | Sprint 2 | Sprint 1–2 (highest risk, start earliest) | Sprint 2 | — | Sprint 3 |
| 3.6 PSR | Sprint 2 | Sprint 2 | Sprint 2 | Sprint 1–2 | Sprint 3 |
| 3.7 Distribution/Payment | Sprint 2 | Sprint 2 | Sprint 2 | — | Sprint 3 |
| 3.8 Reports Hub | Sprint 2 | Sprint 2 | Sprint 2 | — | Sprint 3 |
| 3.9 Leavers | Sprint 2 | Sprint 2 | Sprint 2 | — | Sprint 3 |
| 3.10 BIK/SSP/Pensions/AE | Sprint 3 | Sprint 2–3 | Sprint 3 | Sprint 3 (AE→NAERSA) | Sprint 4 |
| 3.11 Journals/Year Transition | Sprint 3 | Sprint 2–3 (Tax-Year Rules is a Sprint 1 prerequisite, see §5) | Sprint 3 | — | Sprint 4 |
| 3.12 Revenue Payments | Sprint 3 | Sprint 3 | Sprint 3 | Sprint 3 | Sprint 4 |

*(Sprint numbers are relative sequencing hints for dispatch batching, not calendar commitments.)*

---

## 5. Version Control: Concurrent Tax-Year Architecture

The exemplar sidesteps multi-year concurrency by shipping a **new standalone installer every tax year** with a one-time "import from last year" wizard (`starting-the-new-tax-year/how-do-i-start-processing-2026-payroll`) — the 2025 and 2026 products are literally different programs pointed at different data folders (`C:\ThesaurusPayroll2025`, `C:\ThesaurusPayroll2026`). That model doesn't fit a single persistent ERP like BlueSeer, so this is the one place we deliberately **don't** mirror the exemplar's mechanism — only its outcome (prior-year data stays untouched and fully accessible while the new year is being processed, including year-end overlap where a fortnightly pay period spans both years).

**Design:**

- `ITaxYearRules` interface exposes everything that changes annually: PAYE credit/COP tables, PRSI class rate tables, USC bands, LPT handling, ASC bands, BIK CO2 bands, Payroll Calendar week/period boundaries, statutory report layouts (e.g., Employment Details Summary) where the year's format differs.
- One concrete implementation per tax year: `Paye2025Rules`, `Paye2026Rules`, package-scoped as `com.blueseer.pay.ty2025`, `com.blueseer.pay.ty2026`, etc. — each independently versioned, testable, and shippable (a mid-year Budget change only requires a new/patched year-class, not a schema migration).
- Every payroll transaction row (`payslip`, `emp_rpn_ie`, `psr_batch_ie`) is tagged `tax_year`. `PayPeriodFinaliser` (S-22) and all calculation engines resolve their `ITaxYearRules` instance from this column, never from "the current system date," so a 2026 fortnightly run whose first week falls in late 2025 processes each week against the correct year's rules — matching the exemplar's explicit guidance for that edge case.
- `TaxYearRolloverService` (S-65) does **not** copy employee/company master data into a new file — it instantiates the new year's rules context and lets `emp_mstr` (already persistent across years in BlueSeer) carry forward by reference. Historical years remain queryable in the same database indefinitely; there is no "old installer" to keep around.
- Reports and screens that are inherently year-scoped (Year End Summary, Employment Details Summary, Payroll Calendar) take a `taxYear` parameter rather than assuming "current."
- Post-cessation payments "out of year" (S-52) are the concrete proof-point for this design: the screen must resolve a *historical* `ITaxYearRules` while the rest of the application operates in the current year — impossible under a separate-installer model, natural under this one.

---

## 6. Exclusions

The following exemplar features are explicitly **out of scope** — they are Thesaurus Connect (cloud/web-portal) capabilities with no on-premise Swing equivalent, and BlueSeer's EDI/reporting stack replaces their purpose:

- Thesaurus Connect overview, registration, and account customisation
- Employer Self-Service Portal (Dashboard, Employees, Reports, Calendar, Revenue, Documents, Settings, Leave Request Cancellations)
- Employee Self-Service Portal (Dashboard, Documents, Calendar, My Details, Requesting Leave, Requesting Leave Cancellation)
- Self Service mobile App and its Two-Factor Authentication flows (employer and employee)
- Connect Multiuser enhancements ("Using the Latest Employer File" cloud-lock coordination)
- Backing up to / restoring from **Thesaurus Connect** cloud storage specifically (local Backup/Restore, S-existing BlueSeer utility, is in scope and already exists at the ERP level — not rebuilt here)
- Bright ID-based ordering/licensing flow
- Payroll Upgrades / licence-key activation screens (Thesaurus-specific distribution mechanism)

**Explicitly retained despite living under a "Connect"-adjacent doc section:** Auto-Enrolment / MyFuture Fund (§3.10, S-61) is a **statutory NAERSA integration**, not a cloud portal convenience — its AECS submission and AEPN handling are in scope as core EDI/domain work. Only its "Employer Help Guide" and "MyFuture Fund Demo" documentation pages are excluded as non-functional.

---

## 7. Open Questions for Product Sign-off

1. **Payslip stationery formats** (S-36): do we need parity with all four exemplar layouts (2-per-page hi/lo-res, payslip & cheque, laser security), or can we standardise on one Jasper template with a print-density parameter?
2. **Bank file targets** (S-38): confirm which of SEPA-generic / non-Irish IBAN / Bankline / Modulr are actually required for launch vs. later phases.
3. **Accounting export targets** (S-62/63): confirm whether external CSV mapping (Quickbooks/Sage/Xero) is needed at all, or whether native `com.blueseer.fgl` posting supersedes it entirely for our customer base.
4. **CWPS/NECI pension schemes** (S-44/S-59): confirm these sector-specific schemes are in our customer base before committing Sprint 2–3 capacity.

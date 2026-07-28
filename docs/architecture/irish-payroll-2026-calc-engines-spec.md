# Irish Payroll Module — Calculation Engine Specification (Phase 1.5)

**Status:** Planning/specification only — no application code in scope.
**Scope:** Detailed expansion of §3.5 "Payroll Deductions Engine" from [irish-payroll-2026-roadmap.md](irish-payroll-2026-roadmap.md) — the statutory deduction family (PAYE, PRSI, USC, LPT, ASC) plus the Tax-Year Rules registry that feeds them.
**Calculation library:** [EvalEx v3](https://github.com/ezylang/EvalEx) (`com.ezylang.evalex.Expression`), chosen for `BigDecimal`-native arithmetic and because every step evaluates independently against a named variable map — the intermediate `EvaluationValue` of each step is exactly what the **Payslip Workings** screen (S-23) renders as its audit trail, so the step list below is not just documentation, it is the literal sequence `PayslipWorkingsExplainer` replays on screen.

## Engine Integration Pattern (read before the individual specs)

- **Formulas never contain literal rates, bands, or thresholds.** Every numeric constant a formula touches (rates, ceilings, credits) is a *key in the State Context Map*, resolved and injected by the active `ITaxYearRules` implementation (`Paye2025Rules` / `Paye2026Rules`, per §5 of the main roadmap) before the `Expression` is evaluated. This is what lets a mid-year rate change (see C-02, the 1 October 2026 PRSI step) or a full year rollover happen without touching a single formula string — only the rules class changes.
- Each engine runs as an ordered list of named `Expression` evaluations. Step *N*'s result is written back into the context map under a step-specific key (e.g. `cumulativeGrossToDate`) so step *N+1* can reference it by name. This chain **is** the audit trail — persist each `(stepLabel, formula, resultValue)` tuple against the payslip line for S-23.
- **Reference values quoted below have been cross-checked against primary sources**, not just the exemplar and secondary payroll blogs: Revenue's own **"Budget 2026 Summary"** (`revenue.ie/budget`, published 7 October 2025), Revenue's own **"The Emergency Basis of Tax & USC Deduction 2026"** table (form RPC020012), and the Department of Social Protection's **"PRSI Class A Rates"** publication (`gov.ie`, last updated 10 June 2026). This round of verification caught two real errors in an earlier draft of this document, both now corrected below:
    1. The **emergency PAYE mechanism** (C-01) was modelled on a guessed 4-week-credit/8-week-cutoff taper. Revenue's actual published table shows the tax **credit is always €0.00** on emergency basis regardless of PPSN status, and the cut-off point (not the credit) is what applies temporarily — for the first 4 weekly periods (or the frequency-equivalent window — see C-01) at a flat Revenue-published allowance, then €0 from the next period onward.
    2. The **ASC band structure** (C-05) was modelled on a guessed 3-band structure (exempt/mid/high) with different exempt ceilings per group. The Department of Public Expenditure's public service pensions authority (`publicservicepensions.gov.ie`) confirms a simpler 2-band structure with the **same** €34,500 exempt ceiling for both Standard Accrual and Single Scheme members — only the paid rates differ between groups.
    Even so, **Track B's Sprint 1 ticket should still be "confirm §C-01–C-05 reference constants against the live RPN test-system payloads and the current Revenue ASC Tax and Duty Manual before `Paye2026Rules` ships"** — this document is a specification, not a substitute for validating against Revenue's live systems at implementation time. Source citations are listed per-engine and collected in the **Sources** section at the end of this document.

---

### C-01 - PAYE (Pay As You Earn) Engine

*   **State Context Map:**
    *   `grossPayThisPeriod` (BigDecimal) — taxable gross pay for the period being processed
    *   `payPeriodsPerYear` (Integer) — 52 / 26 / 12
    *   `periodNumber` (Integer) — 1-based sequence number within the tax year
    *   `cumulativeGrossPayPriorToThisPeriod` (BigDecimal) — sum of all prior periods this tax year, excludes the current period
    *   `cumulativeTaxPaidToDate` (BigDecimal) — PAYE already deducted in prior periods this tax year
    *   `annualTaxCredit` (BigDecimal) — RPN-supplied, already Revenue-aggregated (do not decompose into Personal/PAYE/Earned-Income sub-credits in code — Revenue delivers one number per employment)
    *   `annualCutOffPoint` (BigDecimal) — RPN-supplied SRCOP for this employment
    *   `standardRate` (BigDecimal) — resolved from `ITaxYearRules`
    *   `higherRate` (BigDecimal) — resolved from `ITaxYearRules`
    *   `calculationBasis` (String) — `"CUMULATIVE"` \| `"WEEK1"` \| `"EMERGENCY"`, RPN-supplied
    *   `hasPpsNumber` (Boolean)
    *   `periodsSinceEmergencyStart` (Integer) — only populated when `calculationBasis == "EMERGENCY"`; count of pay periods (in the employee's own pay frequency) since emergency basis began for this employment, starting at 1
    *   `emergencyCutOffAllowancePerPeriod` (BigDecimal) — only populated when `calculationBasis == "EMERGENCY"`; resolved from `ITaxYearRules` **per pay frequency** (this is a flat Revenue-published figure, not a fraction of the employee's own SRCOP — see reference values below)
    *   `emergencyAllowanceWindowLength` (Integer) — only populated when `calculationBasis == "EMERGENCY"`; number of pay periods the allowance applies for, resolved from `ITaxYearRules` per pay frequency
    *   **2026 reference values (sourced from `Paye2026Rules`; confirmed against Revenue's "Budget 2026 Summary," `revenue.ie/budget`, published 7 October 2025):** `standardRate = 0.20`, `higherRate = 0.40`; single-employment PAYE credit €4,000/yr total (Personal €2,000 + Employee/PAYE €2,000, pre-aggregated on the RPN — do not decompose in code); SRCOP: single €44,000/yr, married-with-qualifying-child €48,000/yr, married one-earner €53,000/yr, married two-earner €53,000/yr **plus an increase of the lower earner's own income capped at €35,000** (so combined maximum €88,000 — the increase is not a flat entitlement, it is capped at whichever is lower: €35,000 or the second spouse's actual income). These are illustrative defaults for test fixtures only — the RPN delivers the actual apportioned per-employment figure and is never overridden by a hardcoded fallback in production.
    *   **2026 Emergency-basis reference values (sourced from `Paye2026Rules`; confirmed against Revenue's own "Emergency Basis of Tax & USC Deduction 2026" table, form RPC020012 — unchanged from the 2025 table, since it tracks the unchanged single-person SRCOP):** `emergencyCutOffAllowancePerPeriod` = €846.16 (weekly) / €1,692.31 (fortnightly) / €1,833.34 (twice-monthly) / €3,384.62 (four-weekly) / €3,666.67 (monthly); `emergencyAllowanceWindowLength` = 4 (weekly) / 2 (fortnightly) / 2 (twice-monthly) / 1 (four-weekly) / 1 (monthly). **The tax credit under emergency basis is always €0.00 for every period, with or without a PPSN — there is no credit taper.** Only the cut-off point is temporarily available, and only if a PPSN has been provided.

*   **Step-by-Step EvalEx Math (cumulative basis):**
    *   *Step 1 — Cumulative gross pay to date:*
        *EvalEx Formula:* `cumulativeGrossPayPriorToThisPeriod + grossPayThisPeriod`
        → stored as `cumulativeGrossToDate`
    *   *Step 2 — Cumulative tax credit entitlement to date:*
        *EvalEx Formula:* `(annualTaxCredit / payPeriodsPerYear) * periodNumber`
        → stored as `cumulativeCreditToDate`
    *   *Step 3 — Cumulative standard-rate cut-off to date:*
        *EvalEx Formula:* `(annualCutOffPoint / payPeriodsPerYear) * periodNumber`
        → stored as `cumulativeCutOffToDate`
    *   *Step 4 — Tax at standard rate on income within the cut-off:*
        *EvalEx Formula:* `MIN(cumulativeGrossToDate, cumulativeCutOffToDate) * standardRate`
        → stored as `standardRateTax`
    *   *Step 5 — Tax at higher rate on income above the cut-off:*
        *EvalEx Formula:* `MAX(0, cumulativeGrossToDate - cumulativeCutOffToDate) * higherRate`
        → stored as `higherRateTax`
    *   *Step 6 — Gross cumulative tax liability:*
        *EvalEx Formula:* `standardRateTax + higherRateTax`
        → stored as `grossCumulativeTax`
    *   *Step 7 — Net cumulative tax due after credits:*
        *EvalEx Formula:* `MAX(0, grossCumulativeTax - cumulativeCreditToDate)`
        → stored as `netCumulativeTaxDue`
    *   *Step 8 — PAYE payable this period:*
        *EvalEx Formula:* `MAX(0, netCumulativeTaxDue - cumulativeTaxPaidToDate)`
        → this is the payslip line value

*   **Step-by-Step EvalEx Math (Week1/Month1 basis — `calculationBasis == "WEEK1"`):**
    *   *Step 1 — Period tax credit (no look-back):*
        *EvalEx Formula:* `annualTaxCredit / payPeriodsPerYear`
    *   *Step 2 — Period cut-off (no look-back):*
        *EvalEx Formula:* `annualCutOffPoint / payPeriodsPerYear`
    *   *Step 3 — Standard-rate tax this period:*
        *EvalEx Formula:* `MIN(grossPayThisPeriod, periodCutOff) * standardRate`
    *   *Step 4 — Higher-rate tax this period:*
        *EvalEx Formula:* `MAX(0, grossPayThisPeriod - periodCutOff) * higherRate`
    *   *Step 5 — PAYE payable this period:*
        *EvalEx Formula:* `MAX(0, (standardRateTaxThisPeriod + higherRateTaxThisPeriod) - periodCredit)`

*   **Step-by-Step EvalEx Math (Emergency basis — `calculationBasis == "EMERGENCY"`; per Revenue's published RPC020012 table, not a derived taper):**
    *   *Step 1 — Emergency credit available (always zero — Revenue's table shows €0.00 for every period, with or without a PPSN; this step exists so the audit trail shows the zero explicitly rather than silently omitting a credit line):*
        *EvalEx Formula:* `0`
        → stored as `emergencyCreditAvailable`
    *   *Step 2 — Emergency cut-off available this period (flat allowance for a fixed number of periods if a PPSN has been provided, then zero; always zero without a PPSN):*
        *EvalEx Formula:* `IF(hasPpsNumber == false, 0, IF(periodsSinceEmergencyStart <= emergencyAllowanceWindowLength, emergencyCutOffAllowancePerPeriod, 0))`
        → stored as `emergencyCutOffAvailable`
    *   *Step 3 — Standard-rate tax this period:*
        *EvalEx Formula:* `MIN(grossPayThisPeriod, emergencyCutOffAvailable) * standardRate`
        → stored as `standardRateTaxThisPeriod`
    *   *Step 4 — Higher-rate tax this period:*
        *EvalEx Formula:* `MAX(0, grossPayThisPeriod - emergencyCutOffAvailable) * higherRate`
        → stored as `higherRateTaxThisPeriod`
    *   *Step 5 — PAYE payable this period:*
        *EvalEx Formula:* `MAX(0, (standardRateTaxThisPeriod + higherRateTaxThisPeriod) - emergencyCreditAvailable)`

---

### C-02 - PRSI (Pay Related Social Insurance) Engine

*   **State Context Map:**
    *   `reckonablePayThisPeriod` (BigDecimal) — simplification note: true "reckonable pay" excludes certain non-cash benefits; Track B should confirm the exact reckonable-pay derivation against Revenue's PRSI guidance before wiring
    *   `prsiSubClass` (String) — e.g. `"A1"`, `"AX"`, `"AL"`, `"J9"` — RPN/employee-record supplied
    *   `weeklyLowerThreshold` (BigDecimal) — resolved from `ITaxYearRules`, period-scaled
    *   `prsiCreditUpperThreshold` (BigDecimal) — resolved from `ITaxYearRules`, period-scaled
    *   `prsiCreditMax` (BigDecimal) — resolved from `ITaxYearRules`, period-scaled
    *   `employeeRate` (BigDecimal) — resolved from `ITaxYearRules` **as of the pay date**, not the tax-year default (see note below)
    *   `employerLowerRate` (BigDecimal) — resolved as of pay date
    *   `employerHigherRate` (BigDecimal) — resolved as of pay date
    *   `employerHigherRateThreshold` (BigDecimal) — resolved from `ITaxYearRules`, period-scaled
    *   **2026 reference values, weekly basis (fortnightly/monthly are period-scaled by `ITaxYearRules`, not derived inline in the formula):** `weeklyLowerThreshold = 352`, `prsiCreditUpperThreshold = 424`, `prsiCreditMax = 12`, `employerHigherRateThreshold = 552` (fortnightly €1,104 / monthly €2,392, per the exemplar-adjacent Budget 2026 coverage). **Rate step-change mid-year:** `employeeRate = 0.0420` for pay dates before 1 Oct 2026, `0.0435` from 1 Oct 2026; `employerLowerRate = 0.09` → `0.0915`, `employerHigherRate = 0.1125` → `0.114` on the same date. This is the concrete case that makes `Paye2026Rules` need an **effective-dated** PRSI rate table (keyed by pay date, not a single per-year constant) rather than a flat field — flag this as a required refinement to §5's `ITaxYearRules` interface: it needs `PrsiRateSchedule resolvePrsiRates(LocalDate payDate)`, not a single rate per year.

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Employee PRSI before credit:*
        *EvalEx Formula:* `IF(reckonablePayThisPeriod <= weeklyLowerThreshold, 0, reckonablePayThisPeriod * employeeRate)`
        → stored as `employeePrsiRaw`
    *   *Step 2 — Tapered PRSI credit:*
        *EvalEx Formula:* `IF(reckonablePayThisPeriod > weeklyLowerThreshold && reckonablePayThisPeriod < prsiCreditUpperThreshold, MAX(0, prsiCreditMax - ((reckonablePayThisPeriod - weeklyLowerThreshold - 0.01) / 6)), 0)`
        → stored as `prsiCredit`
    *   *Step 3 — Employee PRSI payable this period:*
        *EvalEx Formula:* `MAX(0, employeePrsiRaw - prsiCredit)`
        → this is the payslip employee-PRSI line value
    *   *Step 4 — Employer PRSI payable this period:*
        *EvalEx Formula:* `IF(reckonablePayThisPeriod <= employerHigherRateThreshold, reckonablePayThisPeriod * employerLowerRate, reckonablePayThisPeriod * employerHigherRate)`
        → not shown on the payslip, posted to the employer PRSI cost account via the Journal Export (S-63)

---

### C-03 - USC (Universal Social Charge) Engine

*   **State Context Map:**
    *   `uscablePayThisPeriod` (BigDecimal) — note: USC applies to gross pay generally *before* pension-contribution relief, unlike PAYE — flag as a distinct base amount from `grossPayThisPeriod`, not a reused value
    *   `payPeriodsPerYear` (Integer)
    *   `periodNumber` (Integer)
    *   `cumulativeUscablePayPriorToThisPeriod` (BigDecimal)
    *   `cumulativeUscPaidToDate` (BigDecimal)
    *   `annualBand1Ceiling` (BigDecimal) — resolved from `ITaxYearRules`
    *   `annualBand2Ceiling` (BigDecimal) — resolved from `ITaxYearRules`
    *   `annualBand3Ceiling` (BigDecimal) — resolved from `ITaxYearRules`
    *   `band1Rate` / `band2Rate` / `band3Rate` / `band4Rate` (BigDecimal) — resolved from `ITaxYearRules`
    *   `uscExemptMarker` (Boolean) — RPN-supplied; **operate strictly on this flag, per the exemplar's explicit instruction that employers must never infer USC exemption from an employee's personal circumstances**
    *   `reducedRateCapApplies` (Boolean) — RPN-supplied (medical card / age 70+ with aggregate income ≤ €60,000)
    *   `reducedRateCapValue` (BigDecimal) — resolved from `ITaxYearRules`
    *   `calculationBasis` (String) — `"CUMULATIVE"` \| `"WEEK1"` \| `"EMERGENCY"`
    *   `emergencyUscRate` (BigDecimal) — resolved from `ITaxYearRules`
    *   **2026 reference values (confirmed against the exemplar's `2026 Budget - Employer Summary` and `USC - General Information` pages):** exemption threshold €13,000/yr; `annualBand1Ceiling = 12012`, `band1Rate = 0.005`; `annualBand2Ceiling = 28700`, `band2Rate = 0.02`; `annualBand3Ceiling = 70044`, `band3Rate = 0.03`; `band4Rate = 0.08` above €70,044; `reducedRateCapValue = 0.02` (extended through 2027 per the exemplar); `emergencyUscRate = 0.08` flat, no bands.

*   **Step-by-Step EvalEx Math (cumulative basis):**
    *   *Step 0 — Exemption short-circuit:*
        *EvalEx Formula:* `IF(uscExemptMarker == true, 0, -1)`
        A result of `0` means USC this period is `0` and no further steps run; `-1` is a sentinel telling the calling Java code to proceed to Step 1 (EvalEx has no early-return, so the orchestrating `IPayrollCalculationService` checks this sentinel rather than encoding full short-circuit branching in one giant expression — keeps each audit-trail step readable).
    *   *Step 1 — Cumulative USC-able pay to date:*
        *EvalEx Formula:* `cumulativeUscablePayPriorToThisPeriod + uscablePayThisPeriod`
        → stored as `cumulativeUscablePay`
    *   *Step 2 — Cumulative band ceilings to date:*
        *EvalEx Formula (band 1):* `(annualBand1Ceiling / payPeriodsPerYear) * periodNumber`
        *EvalEx Formula (band 2):* `(annualBand2Ceiling / payPeriodsPerYear) * periodNumber`
        *EvalEx Formula (band 3):* `(annualBand3Ceiling / payPeriodsPerYear) * periodNumber`
        → stored as `band1CeilingToDate`, `band2CeilingToDate`, `band3CeilingToDate`
    *   *Step 3 — Income falling in each band:*
        *EvalEx Formula (band 1 portion):* `MIN(cumulativeUscablePay, band1CeilingToDate)`
        *EvalEx Formula (band 2 portion):* `MAX(0, MIN(cumulativeUscablePay, band2CeilingToDate) - band1CeilingToDate)`
        *EvalEx Formula (band 3 portion):* `MAX(0, MIN(cumulativeUscablePay, band3CeilingToDate) - band2CeilingToDate)`
        *EvalEx Formula (band 4 portion):* `MAX(0, cumulativeUscablePay - band3CeilingToDate)`
    *   *Step 4 — Cumulative USC gross liability (reduced-rate-cap branch vs. standard branch):*
        *EvalEx Formula:* `IF(reducedRateCapApplies == true, (band1Portion * band1Rate) + (MAX(0, cumulativeUscablePay - band1CeilingToDate) * reducedRateCapValue), (band1Portion * band1Rate) + (band2Portion * band2Rate) + (band3Portion * band3Rate) + (band4Portion * band4Rate))`
        → stored as `cumulativeUscGross`
    *   *Step 5 — USC payable this period:*
        *EvalEx Formula:* `MAX(0, cumulativeUscGross - cumulativeUscPaidToDate)`

*   **Step-by-Step EvalEx Math (Emergency basis):**
    *   *Step 1 — USC payable this period (flat rate, no bands, no cut-off — per the exemplar's explicit rule):*
        *EvalEx Formula:* `uscablePayThisPeriod * emergencyUscRate`

---

### C-04 - LPT (Local Property Tax at Source) Engine

LPT is **not an independently calculated tax** — the exemplar is explicit that the employer deducts exactly what Revenue instructs via the RPN, using one of two Revenue-specified collection methods. This engine is a routing function over RPN-supplied instructions, not a rate-table calculation.

*   **State Context Map:**
    *   `lptDeductionMethod` (String) — `"FIXED_PERIODIC"` \| `"PERCENTAGE_OF_NET"` \| `"NONE"`, RPN-supplied
    *   `lptAnnualChargeFromRpn` (BigDecimal) — populated when method is `FIXED_PERIODIC`
    *   `payPeriodsPerYear` (Integer)
    *   `netPayThisPeriod` (BigDecimal) — populated when method is `PERCENTAGE_OF_NET` (used by Revenue for arrears collection scenarios); this value is only available *after* C-01/C-02/C-03 have run for the period, so LPT must be sequenced last in the per-payslip engine chain
    *   `lptPercentageRate` (BigDecimal) — RPN-supplied, only populated when method is `PERCENTAGE_OF_NET`

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Periodic LPT under the fixed-charge method:*
        *EvalEx Formula:* `lptAnnualChargeFromRpn / payPeriodsPerYear`
        → stored as `lptFixedPeriodic`
    *   *Step 2 — Periodic LPT under the percentage-of-net method:*
        *EvalEx Formula:* `netPayThisPeriod * lptPercentageRate`
        → stored as `lptPercentageOfNet`
    *   *Step 3 — LPT payable this period:*
        *EvalEx Formula:* `IF(lptDeductionMethod == "FIXED_PERIODIC", lptFixedPeriodic, IF(lptDeductionMethod == "PERCENTAGE_OF_NET", lptPercentageOfNet, 0))`
        Note for Track B: confirm EvalEx v3's `STRING` evaluation-value equality semantics for `==` during the Sprint 1 spike; if unsupported, resolve `lptDeductionMethod` to a pre-branched sub-expression in Java before evaluation rather than comparing strings inside the formula.

---

### C-05 - ASC (Additional Superannuation Contribution) Engine

Applies only to employees flagged as members of a public-service pension scheme (S-08 Deductions tab, narrative `"ASC"`). Band structure confirmed against the Department of Public Expenditure, NDP Delivery and Reform's public service pensions authority (`publicservicepensions.gov.ie`) — a simpler **two-band-plus-exempt** structure than the three-band structure this document originally guessed, and both groups share the same exempt ceiling; only the paid rates differ. These rates have applied unchanged since 1 January 2020 (ASC is a stable, rarely-adjusted charge — unlike PAYE/USC/PRSI it is not part of the annual Budget cycle), but Track B should still confirm no more recent change against the current Revenue Tax and Duty Manual before go-live, since the primary source consulted here is the scheme-administration authority rather than Revenue's own TDM.

*   **State Context Map:**
    *   `pensionableRemunerationThisPeriod` (BigDecimal) — basic pay excluding non-pensionable overtime, plus pensionable allowances
    *   `payPeriodsPerYear` (Integer)
    *   `periodNumber` (Integer)
    *   `cumulativePensionableRemunerationPriorToThisPeriod` (BigDecimal)
    *   `cumulativeAscPaidToDate` (BigDecimal)
    *   `ascGroup` (String) — `"STANDARD_ACCRUAL"` \| `"SINGLE_SCHEME"` — resolved from the employee record; used upstream by `AscGroupRateSelector` to pick which of the two constant sets below feeds `activeExemptCeiling`/`activeUpperBandCeiling`/`activeMidRate`/`activeTopRate`, so the formula itself stays group-agnostic. (BlueSeer caters for these two groups only — Fast Accrual Group is explicitly out of scope, matching the exemplar's own stated limitation.)
    *   `activeExemptCeiling` (BigDecimal) — resolved from `ITaxYearRules` — **€34,500/yr for both groups**
    *   `activeUpperBandCeiling` (BigDecimal) — resolved from `ITaxYearRules` — **€60,000/yr for both groups**
    *   `activeMidRate` (BigDecimal) — resolved from `ITaxYearRules` per `ascGroup` — **10% (Standard Accrual) / 3.33% (Single Scheme)**, applies to pensionable pay between the exempt ceiling and the upper band ceiling
    *   `activeTopRate` (BigDecimal) — resolved from `ITaxYearRules` per `ascGroup` — **10.5% (Standard Accrual) / 3.5% (Single Scheme)**, applies to pensionable pay above the upper band ceiling
    *   `hasManualOverrideAmount` (Boolean) — true when the user has typed a euro amount directly over the calculated ASC (per the exemplar's override feature)
    *   `manualOverrideAmount` (BigDecimal) — populated only when `hasManualOverrideAmount == true`
    *   `hasManualOverridePercentage` (Boolean) — true when the user has entered a Week1/Month1 override percentage instead
    *   `manualOverridePercentage` (BigDecimal) — populated only when `hasManualOverridePercentage == true`

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Cumulative pensionable remuneration to date:*
        *EvalEx Formula:* `cumulativePensionableRemunerationPriorToThisPeriod + pensionableRemunerationThisPeriod`
        → stored as `cumulativePensionablePay`
    *   *Step 2 — Cumulative exempt ceiling and upper band ceiling to date:*
        *EvalEx Formula (exempt):* `(activeExemptCeiling / payPeriodsPerYear) * periodNumber`
        *EvalEx Formula (upper band):* `(activeUpperBandCeiling / payPeriodsPerYear) * periodNumber`
        → stored as `exemptCeilingToDate`, `upperBandCeilingToDate`
    *   *Step 3 — Pensionable pay falling in each band:*
        *EvalEx Formula (mid-band portion, exempt ceiling to €60,000):* `MAX(0, MIN(cumulativePensionablePay, upperBandCeilingToDate) - exemptCeilingToDate)`
        *EvalEx Formula (top-band portion, above €60,000):* `MAX(0, cumulativePensionablePay - upperBandCeilingToDate)`
    *   *Step 4 — Cumulative ASC gross liability:*
        *EvalEx Formula:* `(midBandPortion * activeMidRate) + (topBandPortion * activeTopRate)`
        → stored as `cumulativeAscGross`
    *   *Step 5 — System-calculated ASC this period:*
        *EvalEx Formula:* `MAX(0, cumulativeAscGross - cumulativeAscPaidToDate)`
        → stored as `systemCalculatedAsc`
    *   *Step 6 — Final ASC this period, applying any manual override:*
        *EvalEx Formula:* `IF(hasManualOverrideAmount == true, manualOverrideAmount, IF(hasManualOverridePercentage == true, pensionableRemunerationThisPeriod * manualOverridePercentage, systemCalculatedAsc))`

---

### C-06 - Tax-Year Rules Registry (resolver, not a money-calculation engine)

Included here because C-01–C-05 are meaningless without it — but it does not itself run EvalEx expressions, so it does not follow the calculation-engine template above. It is the thing that populates every reference-value bullet listed in C-01–C-05.

*   **Responsibility:** Given a `(taxYear, payDate)` pair, return the fully-populated context-map fragment (rates, credits, bands, thresholds) that C-01–C-05 require. Concrete implementations: `Paye2025Rules`, `Paye2026Rules` (package `com.blueseer.pay.ty2025` / `com.blueseer.pay.ty2026`, per the main roadmap's §5).
*   **Resolution algorithm (Java, not EvalEx — there is no money arithmetic here, only lookup):**
    1. Resolve `taxYear` from the payslip's `tax_year` column (never from `LocalDate.now()`).
    2. Look up the registered `ITaxYearRules` bean for that year; fail loudly if none is registered (no silent fallback to the prior year's rules).
    3. Within that year's rules object, resolve any **effective-dated** sub-tables against `payDate` — currently only C-02's PRSI rate table needs this for 2026 (the 1 October 2026 step change), but the interface should accept an effective date for every table, not just PRSI, so a future in-year Budget adjustment doesn't require an interface change.
    4. Return the populated context-map fragment to the calling `IPayrollCalculationService`, which merges it with the payslip-specific values (`grossPayThisPeriod`, `cumulativeGrossPayPriorToThisPeriod`, RPN-supplied credits, etc.) before invoking C-01–C-05 in sequence: PAYE → PRSI → USC → LPT (last, since it may depend on net pay) → ASC (independent, can run any time after PRSI since ASC has no PRSI/USC relief interaction per the exemplar).

---

### C-07 - BIK Company Cars Engine

Source: Revenue Tax and Duty Manual **Part 05-01-01b, "Chapter 2 - Employer provided vehicles"** (document last updated December 2025), read in conjunction with **sections 121 and 121A of the Taxes Consolidation Act 1997**. Every numeric constant and every worked example below is taken directly from that manual (§4.1–§4.1.6, §5.1, Table A, Table B, Appendix A) — not estimated.

*   **State Context Map:**
    *   `omvOriginal` (BigDecimal) — Original Market Value, inclusive of VRT and Irish taxes/duties, before any discount or reduction (TDM §4.1.1)
    *   `co2EmissionsGramsPerKm` (BigDecimal) — manufacturer-stated CO₂ figure, used to resolve `vehicleCategory` via Table B (a lookup, not a formula — see Step 2)
    *   `vehicleCategory` (String) — `"A1"` \| `"A"` \| `"B"` \| `"C"` \| `"D"` \| `"E"`, resolved from `co2EmissionsGramsPerKm` per TDM Table B: A1 = 0g/km; A = >0–59g/km; B = >59–99g/km; C = >99–139g/km; D = >139–179g/km; E = >179g/km
    *   `actualBusinessKilometres` (BigDecimal) — kilometres necessarily travelled in the performance of employment duties this tax year (TDM §2.2 — commuting excluded)
    *   `daysVehicleAvailableInYear` (Integer) — full year unless the vehicle was provided/withdrawn mid-year (TDM §4.1.6)
    *   `daysInYear` (Integer) — 365 or 366
    *   `isElectricVehicle` (Boolean)
    *   `temporaryOmvReduction` (BigDecimal) — resolved from `ITaxYearRules` by tax year; applies to categories A1/A/B/C/D and to all vans, **not** to category E (TDM §4.1.3)
    *   `electricVehicleOmvReduction` (BigDecimal) — resolved from `ITaxYearRules`; stacks additively with `temporaryOmvReduction` for EVs only (TDM §6.3, §6.3.1)
    *   `amountMadeGoodByEmployee` (BigDecimal) — any amount the employee reimburses the employer toward the cost of providing/running the car (TDM §4)
    *   `qualifiesFor20PercentReduction` (Boolean) — true only if **all** of: ≥20 hrs/week worked, ≥8,000 business km/year, ≥70% of working time spent away from the employer's premises, with a certified daily logbook (TDM §5.1) — computed upstream, not by this engine
    *   `ratePercentAtActualMileage` (BigDecimal) — resolved via `BikCarRateTable.lookup(vehicleCategory, annualisedBusinessKm)` against TDM Table A (Appendix A, "Applicable with effect from 1 January 2026" table) — **a lookup, not an EvalEx expression**, because it is a 6-category × 4-band matrix; encoding it as nested `IF()` calls is technically possible but not idiomatic, so Track B should implement it as a small Java lookup table seeded from `ITaxYearRules`
    *   `ratePercentAtLowestMileageBand` (BigDecimal) — the same lookup at the `-- to 26,000` km band for the same category (i.e., the highest/undiscounted rate), needed for Step 4 below
    *   **2026 reference values (TDM Table A, "Applicable with effect from 1 January 2026"):** rates range 6%–37.5% depending on category (A1/A/B/C/D/E) and mileage band; `temporaryOmvReduction = €10,000` for 2026 (unchanged from 2023–2025, tapers to €5,000 in 2027, €2,500 in 2028); `electricVehicleOmvReduction = €20,000` for 2026 specifically (down from €35,000 in 2023–2025, tapering to €10,000 in 2027) — **note the mid-scale step-down between 2025 and 2026 is itself a concrete illustration of why `ITaxYearRules` must be re-instantiated per tax year rather than treated as a slowly-drifting constant.**

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Annualised business kilometres (only when the vehicle is available for part of the year):*
        *EvalEx Formula:* `actualBusinessKilometres * daysInYear / daysVehicleAvailableInYear`
        → stored as `annualisedBusinessKm`; used **only** to select the correct rate band in the Step-3 lookup, per TDM §4.1.6 Example 7 — the actual (non-annualised) kilometres are still what gets driven, only the *rate selection* uses the annualised figure
    *   *Step 2 — Reduced OMV:*
        *EvalEx Formula:* `MAX(0, omvOriginal - temporaryOmvReduction - IF(isElectricVehicle == true, electricVehicleOmvReduction, 0))`
        → stored as `reducedOmv`; TDM Example 6 (Peter's electric car) is the worked check for this exact formula: `€55,000 - €10,000 - €20,000 = €25,000`
    *   *Step 3 — Cash equivalent at actual tapered rate:*
        *EvalEx Formula:* `reducedOmv * ratePercentAtActualMileage`
        → stored as `taperedCashEquivalent`
    *   *Step 4 — Cash equivalent under the alternative 20% reduction (TDM §5.1 — a competing computation, not a discount applied on top of Step 3; the taxpayer is entitled to whichever of Step 3 or Step 4 is lower):*
        *EvalEx Formula:* `reducedOmv * ratePercentAtLowestMileageBand * 0.80`
        → stored as `twentyPercentReductionCashEquivalent`. Confirmed against TDM Example 8 (Shane: `OMV × 30% × 80% = OMV × 24%`, beneficial) and Example 9 (Aoibheann: the 20% route gives €6,000 vs. the tapered route's €3,600 — tapering wins, so the taxpayer would **not** elect this route even though `qualifiesFor20PercentReduction` is true).
    *   *Step 5 — Selected cash equivalent before proration:*
        *EvalEx Formula:* `IF(qualifiesFor20PercentReduction == true, MIN(taperedCashEquivalent, twentyPercentReductionCashEquivalent), taperedCashEquivalent)`
        → stored as `cashEquivalentBeforeProration`
    *   *Step 6 — Pro-rated for partial-year availability:*
        *EvalEx Formula:* `cashEquivalentBeforeProration * (daysVehicleAvailableInYear / daysInYear)`
        → stored as `cashEquivalentForPeriod`. Matches TDM Example 7's `334/365` proration exactly.
    *   *Step 7 — Final BIK chargeable this period:*
        *EvalEx Formula:* `MAX(0, cashEquivalentForPeriod - amountMadeGoodByEmployee)`
        → this is the payslip BIK notional-pay line, added to gross pay before C-01/C-02/C-03 run (BIK is chargeable to PAYE, PRSI, **and** USC per TDM §8.5's reference to "Income Tax, PRSI and USC")

---

### C-08 - BIK Company Vans Engine

Source: same TDM Part 05-01-01b, §4.1 (van cash-equivalent rate) and §5.2 (limited-private-use exemption).

*   **State Context Map:**
    *   `omvOriginal` (BigDecimal)
    *   `temporaryOmvReduction` (BigDecimal) — resolved from `ITaxYearRules`; the same reduction table applies to vans as to cars (TDM §4.1.3 explicitly includes "All vans")
    *   `amountMadeGoodByEmployee` (BigDecimal)
    *   `daysVehicleAvailableInYear` (Integer), `daysInYear` (Integer)
    *   `vanBikRate` (BigDecimal) — resolved from `ITaxYearRules`; **8% since 1 January 2023** (increased from 5%), applies flat regardless of business mileage (TDM §4.1, unlike cars there is no mileage-tapering table for vans)
    *   `qualifiesForLimitedPrivateUseExemption` (Boolean) — true only if **all four** TDM §5.2 conditions hold: van supplied for work purposes; employee required to bring it home; no private use other than the commute, and none in fact occurs; ≥80% of working time spent away from the employer's premises — computed upstream by an HR/policy attestation, not by this engine

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Reduced OMV:*
        *EvalEx Formula:* `MAX(0, omvOriginal - temporaryOmvReduction)`
        → stored as `reducedOmv`
    *   *Step 2 — Cash equivalent before proration:*
        *EvalEx Formula:* `IF(qualifiesForLimitedPrivateUseExemption == true, 0, reducedOmv * vanBikRate)`
        → stored as `cashEquivalentBeforeProration`
    *   *Step 3 — Pro-rated for partial-year availability:*
        *EvalEx Formula:* `cashEquivalentBeforeProration * (daysVehicleAvailableInYear / daysInYear)`
        → stored as `cashEquivalentForPeriod`
    *   *Step 4 — Final BIK chargeable this period:*
        *EvalEx Formula:* `MAX(0, cashEquivalentForPeriod - amountMadeGoodByEmployee)`

---

### C-09 - BIK Preferential Loans Engine

Source: Revenue Tax and Duty Manual **Part 05-01-01d, "Chapter 4 - The provision of preferential loans"** (document last reviewed October 2024), read in conjunction with **sections 122 and 122A of the Taxes Consolidation Act 1997**.

*   **State Context Map:**
    *   `loanCategory` (String) — `"HOME_LOAN"` \| `"OTHER"`. `HOME_LOAN` applies only where the loan is used solely to purchase/repair/develop/improve a residence occupied by the employee, a former/separated spouse, or a rent-free-housed dependent relative (TDM §2.3(1)); every other preferential loan (cars, general purposes, etc.) is `OTHER`
    *   `specifiedRateHomeLoan` (BigDecimal) — resolved from `ITaxYearRules`; **4%**, in force since 1 January 2013 (TDM §2.3)
    *   `specifiedRateOther` (BigDecimal) — resolved from `ITaxYearRules`; **13.5%**, in force since 1 January 2013 (TDM §2.3)
    *   `subPeriods` (List of sub-period records, each with: `openingPrincipalBalance` (BigDecimal), `daysInSubPeriod` (Integer), `actualInterestRateForSubPeriod` (BigDecimal), `actualInterestPaidForSubPeriod` (BigDecimal)) — the loan year must be split into sub-periods at every capital repayment or interest-rate change, per TDM §3 and its worked Examples 4, 6, 7, 9, 11, 13; this engine's steps run **once per sub-period**, then sum
    *   `daysInYear` (Integer)
    *   `isJointLoanWithNonEmployee` (Boolean) — TDM §6.2
    *   `isMarriedOrCivilPartnerJointLoan` (Boolean) — if true, 100% of the notional pay attaches to the employed spouse regardless of joint ownership (TDM §6.2, "not just half")
    *   `employeeSharePercentage` (BigDecimal) — only used when `isJointLoanWithNonEmployee == true` and `isMarriedOrCivilPartnerJointLoan == false`; the employee's actual proportional interest in the loan (TDM §6.2 Example 14 uses 50%)

*   **Step-by-Step EvalEx Math (run once per sub-period, per the loan's own interest-calculation basis — reducing-balance, start-of-year, or mid-year, matched consistently on both the actual and specified sides per TDM §3):**
    *   *Step 1 — Specified rate applicable to this sub-period:*
        *EvalEx Formula:* `IF(loanCategory == "HOME_LOAN", specifiedRateHomeLoan, specifiedRateOther)`
        → stored as `specifiedRateApplicable`
    *   *Step 2 — Interest that would have been payable at the specified rate:*
        *EvalEx Formula:* `openingPrincipalBalance * specifiedRateApplicable * (daysInSubPeriod / daysInYear)`
        → stored as `interestAtSpecifiedRate`. Verified against TDM Example 6 (furniture loan, 6 months, interest-free): `€10,000 × 13.5% × (6/12) = €675` — matches exactly.
    *   *Step 3 — Taxable benefit for this sub-period:*
        *EvalEx Formula:* `MAX(0, interestAtSpecifiedRate - actualInterestPaidForSubPeriod)`
        → stored per sub-period; **Java sums across all `subPeriods` to get the pre-apportionment annual notional pay** — this cross-record summation is orchestration, not a single EvalEx call, matching how TDM Examples 4, 7, 9, 11 and 13 each sum multiple sub-period lines
    *   *Step 4 — Joint-loan apportionment (applied once, to the summed annual figure):*
        *EvalEx Formula:* `IF(isJointLoanWithNonEmployee == true, IF(isMarriedOrCivilPartnerJointLoan == true, summedAnnualTaxableBenefit, summedAnnualTaxableBenefit * employeeSharePercentage), summedAnnualTaxableBenefit)`
        → this is the annual notional-pay figure added to gross pay for PAYE/PRSI/USC purposes; Track B should confirm with the calling `IPayrollCalculationService` how an annual notional-pay figure is spread across pay periods (the manual computes annually; per-period application is a payroll-system design choice not dictated by the TDM)

---

### C-10 - Termination Lump Sum Engine (SCSB / Basic Exemption)

Source: Revenue Tax and Duty Manual **Part 05-05-19, "Payments on Termination of an Office or Employment or Removal from an Office or Employment"** (document updated March 2026), read in conjunction with **sections 123, 201 and Schedule 3 of the Taxes Consolidation Act 1997**. This is the primary reference for §3.9 S-50 (`TerminationLumpSumCalculator`) in the main roadmap.

**Upstream exclusions this engine assumes have already happened** (per TDM §1.2, §2.1, §2.4): statutory redundancy payments (exempt in full, never enter this engine, and per TDM §2.1 "should not be included in the relevant payroll submission"), payments under a contract of employment (fully taxable under s.112, not s.123, no relief available), and pension-scheme lump sums proper (excluded from s.123 by s.201(2)(a)(iv), though their existence still feeds `relevantCapitalSum` below).

*   **State Context Map:**
    *   `exGratiaLumpSumAmount` (BigDecimal) — the s.123-chargeable ex-gratia/redundancy element only
    *   `completeYearsOfService` (Integer) — computed upstream by a `YearsOfServiceCalculator` implementing TDM §3.3.1's rules (breaks in service span the gap; career breaks and unpaid protective leave — carer's, maternity, parental — are excluded from the count but do not break continuity around them; part-time/work-share employees count full years without pro-ration; seasonal workers' periods are summed; group-company service can be aggregated if the severance terms say so)
    *   `basicExemptionFlatAmount` (BigDecimal) — resolved from `ITaxYearRules` — **€10,160** (TDM §3.3)
    *   `basicExemptionPerYearAmount` (BigDecimal) — resolved from `ITaxYearRules` — **€765** per complete year, not apportionable for a partial year (TDM §3.3)
    *   `relevantCapitalSum` (BigDecimal) — the RCS per TDM §3.6: any tax-free pension lump sum already received, plus the present/actuarial value of any future entitlement to one (including an unexercised commutation option, "whether or not the option is exercised") — **the present-value actuarial computation itself is out of scope for this engine**; TDM §3.6 defers to Appendix V of the Revenue Pensions Manual, so Track B should treat RCS as a value supplied by a separate pensions-actuarial service, not computed here
    *   `eligibleForIncreasedExemption` (Boolean) — true only if no s.201 relief in excess of the basic exemption has been claimed by this individual in the preceding 10 years (TDM §3.4 condition 1) — a lookup against Revenue/employer history, not computed here
    *   `increasedExemptionCap` (BigDecimal) — resolved from `ITaxYearRules` — **€10,000** (TDM §3.4)
    *   `averageAnnualRemunerationLast36Months` (BigDecimal) — "A" in the SCSB formula; computed upstream per TDM §3.5's definition of taxable emoluments (includes salary, bonus, commission, overtime, holiday pay, BIK notional pay, and items merely *relieved* elsewhere such as travel-pass or cycle-to-work sacrifice amounts) and adjusted per TDM §3.7 for any Covid-era PUP/TWSS period distortion (PUP weeks excluded and backfilled with earlier paid weeks; TWSS included as normal pay) — this 36-month lookback and its Covid-period adjustment is itself a distinct upstream calculation, not reproduced here
    *   `scsbDivisor` (BigDecimal) — resolved from `ITaxYearRules` — **15**, fixed by the Schedule 3 formula `A × B ÷ 15 − C`
    *   `lifetimeReliefCapAmount` (BigDecimal) — resolved from `ITaxYearRules` — **€200,000**, per s.201(8) (TDM §3.8)
    *   `priorLifetimeReliefClaimed` (BigDecimal) — cumulative s.201 basic/increased/SCSB relief already granted to this individual across any prior employments (excludes the separate €5,000 retraining exemption and the separate €200,000 death/injury/disability cap under s.201(2)(a), which sit outside this cap per TDM §3.8)

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Standard basic exemption:*
        *EvalEx Formula:* `basicExemptionFlatAmount + (basicExemptionPerYearAmount * completeYearsOfService)`
        → stored as `standardBasicExemption`. Matches TDM's worked example (Jim, 35 years): `€10,160 + (€765 × 35) = €36,935`.
    *   *Step 2 — Increased exemption:*
        *EvalEx Formula:* `IF(eligibleForIncreasedExemption == true, MAX(0, increasedExemptionCap - relevantCapitalSum), 0)`
        → stored as `increasedExemption`. Matches Tony's example (RCS €5,000 → increase €5,000) and the note that an RCS of €12,000 yields nil increase.
    *   *Step 3 — Total basic exemption:*
        *EvalEx Formula:* `standardBasicExemption + increasedExemption`
        → stored as `totalBasicExemption`
    *   *Step 4 — SCSB:*
        *EvalEx Formula:* `MAX(0, ((averageAnnualRemunerationLast36Months / scsbDivisor) * completeYearsOfService) - relevantCapitalSum)`
        → stored as `scsb`. Matches Lorna's example: `(€180,000/15 × 39) − €200,000(capped) = €268,000`.
    *   *Step 5 — Additional exemption arising from SCSB (only the excess over the basic exemption is additive — SCSB and the basic exemption are not simply summed):*
        *EvalEx Formula:* `MAX(0, scsb - totalBasicExemption)`
        → stored as `additionalExemptionFromScsb`
    *   *Step 6 — Total exemption before the lifetime cap:*
        *EvalEx Formula:* `totalBasicExemption + additionalExemptionFromScsb`
        → stored as `totalExemptionUncapped` (algebraically equals `MAX(totalBasicExemption, scsb)`, but kept as a two-term sum to mirror the manual's own presentation and keep both audit-trail lines visible)
    *   *Step 7 — Remaining lifetime cap headroom:*
        *EvalEx Formula:* `MAX(0, lifetimeReliefCapAmount - priorLifetimeReliefClaimed)`
        → stored as `remainingLifetimeCapHeadroom`. Matches Alan's example: `€200,000 − €110,000 = €90,000` remaining.
    *   *Step 8 — Final exemption applied:*
        *EvalEx Formula:* `MIN(totalExemptionUncapped, remainingLifetimeCapHeadroom, exGratiaLumpSumAmount)`
        → stored as `finalExemptionApplied`
    *   *Step 9 — Taxable lump sum amount:*
        *EvalEx Formula:* `MAX(0, exGratiaLumpSumAmount - finalExemptionApplied)`
        → this balance is charged to PAYE and USC in the pay period it is *paid* (receipts basis since 2018, TDM §4/§1.1) but is **explicitly excluded from PRSI** ("not regarded as reckonable income for the purpose of PRSI" — TDM §4) — Track B must route this amount into C-01 and C-03 but bypass C-02 entirely for this specific payslip line.

---

### C-11 - Cycle to Work / Salary Sacrifice Engine

Source: Revenue Tax and Duty Manual **Part 05-01-01k, "Chapter 11 - Salary sacrifice arrangements"** (document last reviewed November 2025), read in conjunction with **section 118B of the Taxes Consolidation Act 1997**. The Cycle to Work-specific exemption figures are now confirmed directly from Revenue's own dedicated page, **"Cycle to Work scheme"** — [revenue.ie/en/jobs-and-pensions/taxation-of-employer-benefits/cycle-to-work-scheme.aspx](https://www.revenue.ie/en/jobs-and-pensions/taxation-of-employer-benefits/cycle-to-work-scheme.aspx) (fetched directly), correcting an earlier version of this document that only carried two categories from a secondary snippet — **there are three, and the earlier draft was missing the highest one entirely.**

*   **State Context Map:**
    *   `benefitValue` (BigDecimal) — cost of the bicycle and/or safety equipment
    *   `bikeCategory` (String) — `"STANDARD"` \| `"PEDELEC_OR_EBIKE"` \| `"CARGO_OR_ECARGO"`
    *   `exemptionLimitStandardBike` (BigDecimal) — resolved from `ITaxYearRules` — **€1,250**
    *   `exemptionLimitPedelecOrEbike` (BigDecimal) — resolved from `ITaxYearRules` — **€1,500**
    *   `exemptionLimitCargoOrEcargoBike` (BigDecimal) — resolved from `ITaxYearRules` — **€3,000** (the category missed in the prior draft — cargo and e-cargo bikes have their own, higher limit, distinct from ordinary e-bikes)
    *   `salaryForgoneAmount` (BigDecimal) — the amount the employee has contractually agreed to sacrifice
    *   `isSameTaxYearAsBenefit` (Boolean) — TDM §3.3: the sacrificed remuneration must be from the same year of assessment the benefit is provided in (Example 3: a bonus payable in February 2022 cannot retroactively fund an October 2021 bike)
    *   `isProvidedToConnectedPerson` (Boolean) — TDM §3.1: benefit provided to a spouse/civil partner/other connected person (as defined by s.10 TCA 1997) voids the exemption entirely
    *   `hasCompensatingPayment` (Boolean) — TDM §3.2: if the employer pays the employee a compensating amount alongside the sacrifice, the exemption is voided and **both** the benefit and the compensating payment become fully taxable
    *   `eligibleForExemptionThisCycle` (Boolean) — the once-per-four-years eligibility check (**confirmed**: "once every four years," with the countdown starting in the tax year the bicycle is provided), supplied upstream by a usage-history lookup

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Exemption limit applicable:*
        *EvalEx Formula:* `IF(bikeCategory == "CARGO_OR_ECARGO", exemptionLimitCargoOrEcargoBike, IF(bikeCategory == "PEDELEC_OR_EBIKE", exemptionLimitPedelecOrEbike, exemptionLimitStandardBike))`
        → stored as `exemptionLimitApplicable`
    *   *Step 2 — Exempt benefit qualifies at all:*
        *EvalEx Formula:* `IF(isSameTaxYearAsBenefit == false, false, IF(isProvidedToConnectedPerson == true, false, IF(hasCompensatingPayment == true, false, IF(eligibleForExemptionThisCycle == false, false, true))))`
        → stored as `exemptBenefitQualifies` (a boolean chain, since EvalEx `IF` nests cleanly for exclusive disqualifying conditions — Track B should confirm during the Sprint 1 spike whether chained `IF` or `&&`/`||` composition reads better for the audit trail; functionally equivalent)
    *   *Step 3 — Taxable BIK on the benefit itself (TDM Example 4 vs. Example 5 — even a qualifying benefit is taxable on any value above the exemption ceiling):*
        *EvalEx Formula:* `IF(exemptBenefitQualifies == true, MAX(0, benefitValue - exemptionLimitApplicable), benefitValue)`
        → stored as `taxableBikOnBenefit`
    *   *Step 4 — Salary not deemed forgone (only arises when the employee sacrificed more salary than the benefit was worth — TDM Example 5: sacrifice €20k against a €10k benefit leaves €10k of "salary not deemed forgone", separately taxable, distinct from the BIK line in Step 3):*
        *EvalEx Formula:* `MAX(0, salaryForgoneAmount - benefitValue)`
        → stored as `salaryNotDeemedForgone`
    *   *Step 5 — Total addition back to taxable pay this period:*
        *EvalEx Formula:* `taxableBikOnBenefit + salaryNotDeemedForgone`
        → added back into `grossPayThisPeriod` before C-01/C-02/C-03 run. Where `exemptBenefitQualifies` is false, note the employer never actually reduced gross pay in the first place under TDM §2.2 ("the employee will not be regarded as having sacrificed a portion of his or her salary") — this is a payroll-sequencing point for Track B: an unapproved/disqualified sacrifice should not be netted off gross pay at entry (S-18/S-19) at all, rather than being netted off and then added back here.

---

---

### C-12 - Statutory Sick Leave (SSL) Engine

Source: **S.I. No. 607/2022 — Sick Leave Act 2022 (Prescribed Daily Rate of Payment) Regulations 2022** — [irishstatutebook.ie/eli/2022/si/607/made/en/print](https://www.irishstatutebook.ie/eli/2022/si/607/made/en/print) (fetched directly, full regulation text — this is the primary legislative instrument itself, not Revenue/WRC's summary of it, and it corrects two real errors in an earlier draft of this engine: the reference period is **13 weeks, not 4**, and there are **three** daily-rate calculation categories, not two), Workplace Relations Commission, **"Sick Leave"** — [workplacerelations.ie/en/what_you_should_know/leave/sick-leave/](https://www.workplacerelations.ie/en/what_you_should_know/leave/sick-leave/) (updated 9 April 2025), and Department of Social Protection, **"Illness Benefit, Injury Benefit and Statutory Sick Leave in 2026"** — [gov.ie/en/department-of-social-protection/publications/illness-benefit-injury-benefit-and-statutory-sick-leave-in-2025/](https://www.gov.ie/en/department-of-social-protection/publications/illness-benefit-injury-benefit-and-statutory-sick-leave-in-2025/) (last updated 14 January 2026). The three sources together confirm the entitlement is frozen at **5 days for 2026** — the Act's own phased schedule (3/5/7/10 days for 2023/2024/2025/2026) was legislated but the government publicly reversed the 2025→7-day and 2026→10-day steps.

*   **State Context Map:**
    *   `payCalculationCategory` (String) — `"FIXED_PERIOD"` \| `"FIXED_RATE_VARIABLE_HOURS"` \| `"FULLY_VARIABLE"`, matching the Regulation's own three categories exactly:
        *   **(a) `FIXED_PERIOD`** — pay calculated by reference to a fixed wage/salary/allowance/bonus for a fixed period, **or** a fixed hourly rate for a *set* number of hours per fixed period
        *   **(b) `FIXED_RATE_VARIABLE_HOURS`** — a fixed hourly rate, but the number of hours varies period to period (e.g. rostered/variable shifts at a known hourly wage)
        *   **(c) `FULLY_VARIABLE`** — pay calculated by neither of the above (genuinely casual/variable pay with no fixed rate at all)
    *   `normalDailyHoursLastWorkedPay` (BigDecimal) — only populated for category (a): "the sum... paid to the employee in respect of the normal daily hours last worked by him or her before the statutory sick leave day" (Regulation 3(a)) — **includes** any regular bonus/allowance that doesn't vary with work done, **excludes** overtime and commission
    *   `fixedHourlyRate` (BigDecimal) — only populated for category (b)
    *   `scheduledHoursOnSickDay` (BigDecimal) — populated for categories (b) and (c): the hours the employee "was due to work on the statutory sick leave day" (Regulation 3(b), 3(c))
    *   `averageHourlyRateOver13Weeks` (BigDecimal) — only populated for category (c); computed upstream over "the period of 13 weeks ending immediately before the statutory sick leave day commences, or, if no time was worked by the employee during that period, the period of 13 weeks ending on the day on which time was last worked" (Regulation 3(c) — quoted exactly; **13 weeks**, not the 4 weeks an earlier draft of this engine assumed) — this averaging computation itself (sum of qualifying pay ÷ sum of hours over that window) is deferred to a separate upstream service, but the window definition above is now precise enough to implement directly
    *   `sslRatePercentage` (BigDecimal) — resolved from `ITaxYearRules` — **70%**, applies to categories (a) and (b); category (c) instead applies the rate directly inside the average-hourly-rate figure per the Regulation's own wording ("70% of the average hourly rate") — see Step 1's category-(c) branch
    *   `sslDailyCap` (BigDecimal) — resolved from `ITaxYearRules` — **€110**
    *   `statutorySickLeaveDaysEntitlementPerYear` (Integer) — resolved from `ITaxYearRules` — **5** for 2026
    *   `statutorySickLeaveDaysUsedYearToDate` (Integer) — running count per employee per calendar year, tracked across all instances of illness that year (WRC: unused days from a first short illness carry forward within the year to later instances)
    *   `hasCompletedQualifyingService` (Boolean) — 13 continuous weeks with this employer, computed upstream
    *   `hasMedicalCertificate` (Boolean) — required from day 1 of every instance, per a registered medical practitioner

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Daily rate base, per the Regulation's own three categories:*
        *EvalEx Formula:* `IF(payCalculationCategory == "FIXED_PERIOD", normalDailyHoursLastWorkedPay, IF(payCalculationCategory == "FIXED_RATE_VARIABLE_HOURS", fixedHourlyRate * scheduledHoursOnSickDay, averageHourlyRateOver13Weeks * scheduledHoursOnSickDay))`
        → stored as `dailyRateBase`. Note that for category (c), `averageHourlyRateOver13Weeks` is the *raw* average hourly rate (not yet reduced to 70%) — the 70% reduction is applied uniformly to all three categories together in Step 4 below via `sslRatePercentage`, keeping one shared final-rate step rather than baking the percentage into category (c)'s formula differently from (a)/(b).
    *   *Step 2 — SSL days remaining before today's claim:*
        *EvalEx Formula:* `MAX(0, statutorySickLeaveDaysEntitlementPerYear - statutorySickLeaveDaysUsedYearToDate)`
        → stored as `sslDaysRemaining`
    *   *Step 3 — Eligible for SSL pay today:*
        *EvalEx Formula:* `IF(hasCompletedQualifyingService == false, false, IF(hasMedicalCertificate == false, false, IF(sslDaysRemaining <= 0, false, true)))`
        → stored as `eligibleForSslPayToday`
    *   *Step 4 — SSL pay for this day before the daily cap:*
        *EvalEx Formula:* `dailyRateBase * sslRatePercentage`
        → stored as `sslPayBeforeCap`
    *   *Step 5 — SSL pay for this day, final:*
        *EvalEx Formula:* `IF(eligibleForSslPayToday == true, MIN(sslPayBeforeCap, sslDailyCap), 0)`
        → this is ordinary employer-paid wages for the day, fully subject to PAYE/PRSI/USC via C-01/C-02/C-03 exactly like any other pay — it is **not** a DSP benefit and carries none of Illness Benefit's USC/PRSI exemptions. Once `statutorySickLeaveDaysUsedYearToDate` reaches the entitlement, the WRC/DSP sources confirm DSP Illness Benefit or Injury Benefit takes over from day 6 of that instance of illness — that handoff is out of this engine's scope (see S-57 `IllnessBenefitReconciliationService` in the main roadmap).

---

### C-13 - Director PRSI (Class S) Engine

Source: Department of Social Protection, **"PRSI Class S Rates"** — [gov.ie/en/department-of-social-protection/publications/prsi-class-s-rates/](https://www.gov.ie/en/department-of-social-protection/publications/prsi-class-s-rates/), published 4 February 2020, last updated 20 January 2026 (fetched directly, full rate table). The 50%-shareholding proprietary-director test is cross-confirmed across multiple gov.ie/Citizens Information pages ("PRSI and Family Employment," "The different classes of PRSI," Citizens Information's Class S page) but was not independently fetched as a single primary document this session — flagged in Open Questions.

*   **State Context Map:**
    *   `shareholdingPercentage` (BigDecimal) — the director's direct-or-indirect ownership/control of the company's shareholding
    *   `isProprietaryDirector` (Boolean) — resolved upstream: automatically `true` if `shareholdingPercentage >= 0.50` (in force since 1 July 2013); for holdings below 50%, classification is a case-by-case determination against Revenue's "Code of Practice for Determining Employment or Self-Employment Status of Individuals" and is **not** something this engine decides — it consumes the resulting flag
    *   `totalIncomeForPrsiPurposes` (BigDecimal) — **total income for tax purposes** (gross less allowable expenses) this period; note this is a fundamentally different base from Class A's per-employment reckonable pay, since Class S PRSI is assessed on the individual's aggregate income
    *   `classSRatePercentage` (BigDecimal) — resolved from `ITaxYearRules` **as of the pay date**, mirroring C-02's PRSI effective-dating requirement: **4.20%** before 1 October 2026, **4.35%** from 1 October 2026 (confirmed on the same primary page, same step-change date as Class A)
    *   `annualMinimumContribution` (BigDecimal) — resolved from `ITaxYearRules` — **€650** per annum
    *   `cumulativeClassSContributionsYearToDate` (BigDecimal) — needed to test against the annual minimum at year-end or cessation

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — PRSI treatment routing (not itself money, but the gate for everything else):*
        *EvalEx Formula:* `IF(isProprietaryDirector == true, 1, 0)`
        → if `0`, this engine does not apply at all and C-02 (Class A) runs instead for this individual
    *   *Step 2 — Class S contribution this period:*
        *EvalEx Formula:* `totalIncomeForPrsiPurposes * classSRatePercentage`
        → stored as `classSContributionThisPeriod`. **There is no employer-PRSI component for Class S** — the gov.ie rate table has no "Employer %" column, consistent with a proprietary director being deemed self-employed for PRSI purposes; the employing company does not pay employer PRSI on this individual's pay.
    *   *Step 3 — Annual minimum top-up check (run at the final period of the year or on cessation, not every period):*
        *EvalEx Formula:* `MAX(0, annualMinimumContribution - (cumulativeClassSContributionsYearToDate + classSContributionThisPeriod))`
        → stored as `annualMinimumTopUpDue`

---

### C-14 - Auto-Enrolment (My Future Fund / NAERSA) Engine

Source: Department of Social Protection, **"Auto-enrolment retirement savings system for employers"** — [gov.ie/en/department-of-social-protection/publications/auto-enrolment-retirement-savings-system-for-employers/](https://www.gov.ie/en/department-of-social-protection/publications/auto-enrolment-retirement-savings-system-for-employers/), published 30 August 2024, last updated 14 January 2026 (fetched directly, full contribution-rate table and eligibility text). **Important correction to the main roadmap's §3.10 S-61 description:** the scheme's own press release (`gov.ie/en/department-of-social-protection/press-releases/my-future-fund/`, published 1 January 2026, "MyFutureFund is now officially launched... collection of contributions begins for over 760,000 employees") confirms **the scheme went live 1 January 2026**, not a later 2026 date as some secondary sources still claim — as of this session's date (26 July 2026), the scheme is already operating.

*   **State Context Map:**
    *   `employeeAge` (Integer)
    *   `aggregateAnnualEarningsAcrossEmployments` (BigDecimal) — NAERSA determines this from Revenue payroll data across *all* the employee's employments, not just this one; treat as an externally-supplied input, not something BlueSeer computes standalone for a multi-employer employee
    *   `hasExistingSupplementaryPensionCoverage` (Boolean) — true if this employment already has an occupational pension scheme/trust, RAC, PRSA, or PEPP contribution recorded in payroll — per the source, this exempts **this employment specifically**; a second job without such coverage can still trigger auto-enrolment for that job even if this one is exempt
    *   `isEligibleForAutoEnrolment` (Boolean) — resolved upstream (ultimately by NAERSA, not BlueSeer) = `(23 ≤ age ≤ 60) AND (aggregateAnnualEarnings ≥ €20,000) AND (hasExistingSupplementaryPensionCoverage == false)`
    *   `hasOptedInVoluntarily` (Boolean) — employees outside the age/earnings gate may opt in; once opted in, full matching applies identically to auto-enrolled employees
    *   `hasOptedOutOrSuspended` (Boolean)
    *   `grossPayThisPeriod` (BigDecimal)
    *   `annualEarningsCapForAeContributions` (BigDecimal) — resolved from `ITaxYearRules` — **€80,000**; the source is explicit that "contributions will not... be levied on any gross pay over €80,000" — this caps **all three** contributor streams (employee, employer, State), not only the employer/State match
    *   `cumulativeAeAssessableGrossPayYearToDate` (BigDecimal) — tracks how much of the €80,000 annual ceiling has already been consumed this year
    *   `contributionSchemeYear` (Integer) — which year of the scheme's own 10-year phase-in this pay date falls in (scheme-wide, not per-employee tenure) — resolved from `ITaxYearRules`; 2026 = Year 1
    *   `employeeContributionRate` / `employerContributionRate` (BigDecimal) — resolved from `ITaxYearRules` by `contributionSchemeYear`: Years 1–3 = 1.5%/1.5%; Years 4–6 = 3%/3%; Years 7–9 = 4.5%/4.5%; Years 10+ = 6%/6% (employer always matches employee exactly)
    *   `stateTopUpDivisor` (BigDecimal) — resolved from `ITaxYearRules` — **3** (the State contributes €1 for every €3 the employee contributes — this mechanically reproduces the 0.5%/1%/1.5%/2% State figures shown in the source's own table without needing them as separate constants)

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Active participation this period:*
        *EvalEx Formula:* `IF(isEligibleForAutoEnrolment == false && hasOptedInVoluntarily == false, false, IF(hasOptedOutOrSuspended == true, false, true))`
        → stored as `participationActive`
    *   *Step 2 — Assessable gross pay this period, capped at the annual €80,000 ceiling:*
        *EvalEx Formula:* `MAX(0, MIN(grossPayThisPeriod, annualEarningsCapForAeContributions - cumulativeAeAssessableGrossPayYearToDate))`
        → stored as `assessableGrossPayThisPeriod`
    *   *Step 3 — Employee AE contribution this period:*
        *EvalEx Formula:* `IF(participationActive == true, assessableGrossPayThisPeriod * employeeContributionRate, 0)`
        → stored as `employeeAeContributionThisPeriod`. **Critical routing note, not itself an EvalEx step:** the source states "Instead of tax relief on employee contributions, the State will provide a top-up contribution" — this is explicitly *not* the same tax treatment as a standard occupational pension deduction (C-15 below). A standard pension AVC is deducted from **gross** pay before C-01 (PAYE) runs, reducing the taxable base. AE's employee contribution gets **no such PAYE relief** — it must be deducted from **net** pay, after C-01/C-02/C-03 have already run on the full gross. Track B should model `IPayrollCalculationService`'s engine-ordering accordingly: AE employee contribution is a post-tax deduction, structurally different from every other deduction engine in this document.
    *   *Step 4 — Employer AE contribution this period:*
        *EvalEx Formula:* `IF(participationActive == true, assessableGrossPayThisPeriod * employerContributionRate, 0)`
        → an employer cost, tax-relievable for the employer, and — per the source's explicit statement — **not a BIK to the employee**.
    *   *Step 5 — State top-up this period:*
        *EvalEx Formula:* `employeeAeContributionThisPeriod / stateTopUpDivisor`
        → paid by NAERSA directly into the employee's pot, not routed through employer payroll at all; included here only so the payslip/Payslip-Workings audit trail (S-23) can show the employee the full picture of what's being saved on their behalf, per the source's own "Total Yearly Contributions" example table (a €20,000 earner in Year 1–3 sees €300 employee + €300 employer + €100 State = €700 total).

---

### C-15 - Standard Pension Contribution Relief Engine

Source: Revenue Commissioners, **"Tax relief limits on pension contributions"** — [revenue.ie/en/jobs-and-pensions/pension/relief/tax-relief-limits.aspx](https://www.revenue.ie/en/jobs-and-pensions/pension/relief/tax-relief-limits.aspx) (fetched directly, full age-band table and earnings cap). This engine governs standard employee AVC/PRSA/occupational-scheme contributions (§3.10 S-59 `PensionDeductionEngine` in the main roadmap) — it is structurally distinct from C-14's Auto-Enrolment engine, which explicitly does **not** follow this relief mechanism.

*   **State Context Map:**
    *   `employeeAge` (Integer) — determines the applicable age band
    *   `netRelevantEarningsOrRemuneration` (BigDecimal) — for a PAYE employee, this is remuneration from the employment
    *   `earningsCap` (BigDecimal) — resolved from `ITaxYearRules` — **€115,000**
    *   `ageRelatedPercentageLimit` (BigDecimal) — resolved from `ITaxYearRules` by age band: under 30 = 15%; 30–39 = 20%; 40–49 = 25%; 50–54 = 30%; 55–59 = 35%; 60 or over = 40%
    *   `proposedContributionThisPeriod` (BigDecimal) — the amount the employee has elected to contribute (fixed amount or % of pay, entered on S-59)
    *   `cumulativeReliefEligibleContributionsYearToDate` (BigDecimal) — running total already relieved this tax year, needed because the age-related limit is an **annual** cap being tested period-by-period

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Earnings for relief purposes, capped:*
        *EvalEx Formula:* `MIN(netRelevantEarningsOrRemuneration, earningsCap)`
        → stored as `earningsForReliefCapped`. Confirmed by the source: "even if you earn above €115,000, the earnings cap of €115,000 applies."
    *   *Step 2 — Maximum annual tax-relievable contribution:*
        *EvalEx Formula:* `earningsForReliefCapped * ageRelatedPercentageLimit`
        → stored as `maximumAnnualTaxRelievableContribution`
    *   *Step 3 — Remaining relief headroom for the year:*
        *EvalEx Formula:* `MAX(0, maximumAnnualTaxRelievableContribution - cumulativeReliefEligibleContributionsYearToDate)`
        → stored as `remainingReliefHeadroomForYear`
    *   *Step 4 — Tax-relievable portion of this period's contribution:*
        *EvalEx Formula:* `MIN(proposedContributionThisPeriod, remainingReliefHeadroomForYear)`
        → stored as `taxRelievablePortionThisPeriod`; this is the amount that reduces the gross base fed into C-01 (PAYE)
    *   *Step 5 — Non-relievable excess:*
        *EvalEx Formula:* `MAX(0, proposedContributionThisPeriod - taxRelievablePortionThisPeriod)`
        → still deducted from the employee's pay if they elected the full `proposedContributionThisPeriod` (it still goes into the pension pot), but does **not** reduce the PAYE-taxable base — Track B must route this excess back into taxable gross for C-01 purposes even though the cash still leaves the payslip as a pension deduction.
    *   **USC/PRSI treatment — now confirmed, not an open question.** Revenue's own guidance is explicit and was fetched directly: *"You should not deduct these contributions from your employee's gross pay when you are calculating... Universal Social Charge (USC) and Pay Related Social Insurance (PRSI)"* — the same rule is repeated verbatim for AVCs specifically. This means **neither** `taxRelievablePortionThisPeriod` **nor** the non-relievable excess ever reduces the base fed into C-02 (PRSI) or C-03 (USC) — pension contributions of any kind get PAYE relief only. Concretely: `grossPayThisPeriod` passed into C-01 should have `taxRelievablePortionThisPeriod` subtracted; `reckonablePayThisPeriod` (C-02) and `uscablePayThisPeriod` (C-03) should **both** use the full pre-deduction gross, unreduced by any part of this engine's output. This is a real cross-engine routing rule, not an implementation detail internal to C-15 — Track B should make sure `IPayrollCalculationService`'s orchestration passes a different "gross" figure into C-01 than it passes into C-02/C-03 whenever a pension deduction (C-15) is present on the payslip.

---

---

### C-16 - RSU / Share-Based Remuneration Engine

Source: Revenue **Share Schemes Manual, Chapter 2, "Restricted Stock Units (RSU)"** — [revenue.ie/en/tax-professionals/tdm/share-schemes/chapter-02.pdf](https://www.revenue.ie/en/tax-professionals/tdm/share-schemes/chapter-02.pdf), last reviewed March 2025, read in conjunction with **sections 112, 897B and 985 of the Taxes Consolidation Act 1997** (fetched and read in full). This is the payroll-relevant mechanism for §3.4 S-26 `ShareRemunerationCalculator` — RSUs, not unapproved share options, because since 1 January 2024 the older RTSO self-assessment route (flat 40% income tax + 8% USC + PRSI on the gain, self-paid by the employee to the Collector-General within 30 days via Form RTSO1) has been **superseded by payroll withholding** for share options too; Revenue's dedicated RTSO calculation page confirms "If you realise a gain on, or after, 1 January 2024, your employer is responsible for remitting the tax via payroll" — the flat 40%/8% RTSO rates now apply only to legacy pre-2024 exercises. This engine models the RSU mechanism, which is structurally the dominant payroll case; BlueSeer's `S-26` screen should route unapproved-option gains through this same engine (using the employee's actual RPN-based marginal rates, not a flat 40%/8%) for exercises on/after 1 January 2024.

*   **State Context Map:**
    *   `rsuMarketValuePerShare` (BigDecimal) — market value of the underlying share **at the chargeable date** (TDM §2.6: "the full market value of the shares... is liable to PAYE, USC and PRSI" — no OMV-style reduction, unlike C-07's BIK cars)
    *   `numberOfSharesVesting` (BigDecimal)
    *   `isCashSettled` (Boolean)
    *   `isSharesInEmployingCompanyOrControllingCompany` (Boolean) — condition for the employer-PRSI exemption (TDM §2.6)
    *   `vestingDate` (Date) — the chargeable date is the **earlier** of vesting or the date shares/cash actually pass to the employee, per TDM §2.2
    *   `settlementDate` (Date) — the actual delivery date, which may lag vesting (a "blocking/lock-in period")
    *   `daysBetweenVestingAndSettlement` (Integer)
    *   `isWithin60DaysOfVesting` (Boolean) — resolved from the above; Revenue permits collection deferral to the settlement date only within this window, and only for share-settled RSUs (TDM §2.4)
    *   `isFinalPayDateOfTaxYear` (Boolean) — the deferral is overridden if it would push remittance past the tax year's last payment date (14 or 23 January, as appropriate) — TDM §2.4 explicitly: "tax in respect of shares that vest towards the end of a tax year may have to be paid before the settlement date"
    *   `employeeIsIrishTaxResidentAtChargeableDate` (Boolean)
    *   `isDirector` (Boolean) — TDM §2.6: RSU vesting is "generally always taxable in the hands of a director, notwithstanding his/her tax residence position" — the residence-based exclusion below does not apply to directors
    *   `dividendEquivalentAmount` (BigDecimal) — taxed as an ordinary emolument in the period paid, no special mechanism (TDM §2.5)

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Taxable in the State at all:*
        *EvalEx Formula:* `IF(employeeIsIrishTaxResidentAtChargeableDate == true, true, IF(isDirector == true, true, false))`
        → stored as `isTaxableInState`. Per TDM §2.6, if the RSU vests while the holder is *not* Irish resident, it is not taxable in Ireland at all, "regardless of the fact that the holder may have been resident in Ireland... during the vesting period" — no apportionment.
    *   *Step 2 — Gross taxable value at vesting:*
        *EvalEx Formula:* `IF(isTaxableInState == true, numberOfSharesVesting * rsuMarketValuePerShare, 0)`
        → stored as `taxableValueAtVesting`; valuation is always fixed at the vesting date's market value even where remittance is deferred to a later settlement date (TDM §2.4: "the date of valuation... will continue to be the vesting date")
    *   *Step 3 — Employer PRSI exemption:*
        *EvalEx Formula:* `IF(isCashSettled == true, false, IF(isSharesInEmployingCompanyOrControllingCompany == true, true, false))`
        → stored as `employerPrsiExempt`. Confirmed explicitly: "cash payments are not exempt from employer PRSI" — there is no scenario in which a cash-settled RSU gets this exemption.
    *   *Step 4 — Collection-date deferral eligibility:*
        *EvalEx Formula:* `IF(isCashSettled == true, false, IF(isWithin60DaysOfVesting == true, true, false))`
        → stored as `collectionDeferralEligible`
    *   *Step 5 — Effective PAYE/USC/employee-PRSI remittance date:*
        *EvalEx Formula:* `IF(collectionDeferralEligible == true && isFinalPayDateOfTaxYear == false, settlementDate, vestingDate)`
        → stored as `remittanceDate`; `taxableValueAtVesting` is injected as ordinary notional pay into the payslip for whichever pay period contains `remittanceDate`, using the employee's RPN-supplied credits/cut-off/USC bands **in effect at that later pay date**, then processed through C-01/C-02/C-03 exactly like any other pay
    *   *Step 6 — Dividend equivalents (if any), added to gross pay in the period paid:*
        *EvalEx Formula:* `dividendEquivalentAmount`
        → no special treatment; flows straight into `grossPayThisPeriod` for C-01/C-02/C-03

---

### C-17 - Holiday Pay Advance Spreading Engine

Source: Revenue, **"Holiday pay and advance payments"** — [revenue.ie/en/employing-people/becoming-an-employer-and-ongoing-obligations/payments-to-employees/holiday-pay-and-advance-payments.aspx](https://www.revenue.ie/en/employing-people/becoming-an-employer-and-ongoing-obligations/payments-to-employees/holiday-pay-and-advance-payments.aspx) (fetched directly). **This source is thinner than the others in this document** — Revenue states the *principle* ("the employer may apply those weeks' tax credits, rate bands and USC cut-off points to the holiday pay") but does not publish a granular arithmetic worked example the way the BIK or termination-payment manuals do. The engine below is BlueSeer's standard interpretation of that principle, built by reusing C-01's and C-03's own cumulative mechanics rather than inventing new tax math — which is the only interpretation consistent with how cumulative PAYE already works. Track B should treat this as a *lower-confidence* engine relative to C-01–C-16 and sanity-check it against a payroll-software peer's implementation before shipping.

*   **State Context Map:**
    *   `weeksOfAdvancePayCovered` (Integer) — how many pay periods' worth of salary this single advance payment represents (e.g. 3, if the employee is paid for the current week plus 2 holiday weeks in one go)
    *   `periodNumber` (Integer) — the current period number, as used by C-01/C-03
    *   `isEmployeeLeavingImmediatelyAfter` (Boolean) — Revenue's explicit restriction: this treatment "cannot be done if the employee is leaving employment immediately after receiving holiday pay" (their leave date and final PSR must instead process normally, period by period, per S-48/S-49)
    *   All other keys are exactly C-01's and C-03's existing state context (`annualTaxCredit`, `annualCutOffPoint`, `annualBand1Ceiling` etc., `cumulativeGrossPayPriorToThisPeriod`, `cumulativeTaxPaidToDate`, `cumulativeUscPaidToDate`) — this engine does not introduce new constants, it changes **which `periodNumber` C-01/C-03 are invoked with**

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Eligibility gate:*
        *EvalEx Formula:* `IF(isEmployeeLeavingImmediatelyAfter == true, false, true)`
        → stored as `advanceSpreadEligible`; if `false`, the payment must instead be processed as ordinary pay for the single current period only (no spreading), and C-01/C-03 run with the unmodified `periodNumber`
    *   *Step 2 — Effective period number for this calculation:*
        *EvalEx Formula:* `IF(advanceSpreadEligible == true, periodNumber + weeksOfAdvancePayCovered - 1, periodNumber)`
        → stored as `effectivePeriodNumberForCredit`; this is the value passed into C-01 Step 2/Step 3 and C-03 Step 2 (the cumulative-credit and cumulative-band-ceiling formulas: `(annualTaxCredit / payPeriodsPerYear) * periodNumber`) in place of the ordinary `periodNumber` — mechanically, this gives the single advance payment `weeksOfAdvancePayCovered` periods' worth of credit and cut-off/band headroom in one pass, exactly matching Revenue's stated principle
    *   *Step 3 — Gross pay for the advance payment itself:*
        *EvalEx Formula:* `weeksOfAdvancePayCovered * normalWeeklyPay`
        → this is `grossPayThisPeriod` fed into C-01/C-02/C-03; note C-02 (PRSI) is **not** altered by this engine — PRSI has no equivalent "spread" concept in Revenue's guidance, so PRSI should be calculated on each constituent week's pay independently (`weeksOfAdvancePayCovered` separate PRSI calculations at the normal weekly threshold, summed) rather than once on the lump sum, to avoid distorting the weekly PRSI-free threshold and tapered credit in C-02. This asymmetry — PAYE/USC spread, PRSI not spread — is not explicitly stated by the fetched source and is flagged as an assumption in the Open Questions below.
    *   *Step 4 — Next real pay period after the holiday weeks:*
        *EvalEx Formula:* n/a — Java orchestration, not EvalEx. `periodNumber` resumes at `effectivePeriodNumberForCredit + 1` for the employee's next actual payslip (the weeks "used up" by the advance are skipped, not paid again), and Revenue's own guidance requires the employer to pull a fresh RPN before that next payment in case credits changed in the interim.

---

### C-18 - Maternity / Paternity / Parent's / Adoptive / Health-and-Safety Benefit Tax Treatment

Source: Revenue Tax and Duty Manual **Part 05-05-31, "Taxation of Maternity Benefit, Paternity Benefit, Parent's Benefit, Adoptive Benefit and Health and Safety Benefit"** — [revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-05-31.pdf](https://www.revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-05-31.pdf), last reviewed June 2025, read in conjunction with **section 126(2A) of the Taxes Consolidation Act 1997**. This is the §3.10 S-58 `ParentingBenefitCalculator` reference.

**This engine is deliberately trivial, and that triviality is itself the correct finding, not a shortcut.** TDM §4 is explicit: *"In PAYE cases, the employer will not be required to include the benefit as part of pay for PAYE purposes. Instead, where possible, Revenue will collect the tax due on this income by adjusting the tax credits and standard rate band... and issuing a revised Revenue Payroll Notification to the employer."* Revenue computes the tax on the DSP benefit **itself**, off-payroll, and communicates the result purely as a reduced credit/cut-off point on the employee's RPN. Since C-01 already treats `annualTaxCredit` and `annualCutOffPoint` as RPN-supplied values (§C-01 State Context Map), **the DSP benefit's tax collection is already fully handled by the existing engine chain with zero code changes** — there is no separate "notional pay" line, no separate tax computation, and no separate USC/PRSI handling to build, because §3 of the TDM confirms these benefits are exempt from both. TDM §4 also confirms the reverse: **these benefits are never added to `grossPayThisPeriod`.**

*   **State Context Map:**
    *   `isReceivingQualifyingDspBenefit` (Boolean) — Maternity/Paternity/Parent's/Adoptive/Health-and-Safety Benefit; drives the S-58 screen's leave-tracking UI and the "request updated RPN" prompt, not any payroll calculation
    *   `benefitCommencementReportedToRevenue` (Boolean) — per TDM §4, the revised RPN only issues once commencement/cessation has been reported; BlueSeer's obligation is to trigger that report (an EDI/RPN-flow concern, S-15/S-16), not to compute anything
    *   `employerTopUpAmountThisPeriod` (BigDecimal) — any employer-paid supplement bridging the gap between the DSP benefit and full salary

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Amount added to gross pay in respect of the DSP benefit itself:*
        *EvalEx Formula:* `0`
        → always zero, by design, per TDM §4 — this line exists in the audit trail (S-23 Payslip Workings) purely so a reviewer sees an explicit confirmation that the benefit was correctly excluded from gross pay, rather than a silent omission that looks like a bug.
    *   *Step 2 — Employer top-up, if any:*
        *EvalEx Formula:* `employerTopUpAmountThisPeriod`
        → this is ordinary taxable pay, added to `grossPayThisPeriod` and processed through C-01/C-02/C-03 with **no** special treatment — the exemption in TDM §3 applies only to the DSP-paid benefit, not to anything the employer separately chooses to pay.
    *   *Step 3 — Action required (not money, a workflow trigger):*
        *EvalEx Formula:* n/a — when `benefitCommencementReportedToRevenue` transitions to `true`, or on cessation, this should trigger the S-15/S-16 RPN-retrieval flow so the reduced credits/cut-off point flow onto the employee's record before the next payslip is finalised (S-22), consistent with the TDM's description of *when* the revised RPN becomes available.

---

### C-19 - General Additions/Deductions Classification Engine (Travel & Subsistence worked example)

Source: general taxability principle from **section 112 of the Taxes Consolidation Act 1997** (already cited via C-10 and C-16 — "salaries, fees, wages... perquisites, or profits whatsoever from an office or employment" are taxable pay by default), combined with Revenue **Tax and Duty Manual Part 05-01-06** and the associated **"Civil Service rates"** page — [revenue.ie/en/employing-people/employee-expenses/travel-and-subsistence/civil-service-rates.aspx](https://www.revenue.ie/en/employing-people/employee-expenses/travel-and-subsistence/civil-service-rates.aspx) (fetched directly, full rate tables) — as the flagship worked example of a specific tax-free carve-out from that general rule. This is the §3.2 S-07/S-08 `AdditionCalculationEngine`/`DeductionCalculationEngine` reference; travel/subsistence is presented in full as the pattern to replicate for any other exempt-addition category (small benefit exemption already covered in C-05's preface, cycle to work in C-11) — this document does not re-derive every possible addition type, since s.112's default (fully taxable unless a specific exemption is cited) already governs anything not explicitly listed.

*   **State Context Map:**
    *   `additionCategory` (String) — `"MILEAGE_ALLOWANCE"` \| `"SUBSISTENCE_ALLOWANCE"` \| `"OTHER_TAXABLE"` (the s.112 default — bonus, commission, allowances with no specific exemption)
    *   `claimedAmount` (BigDecimal) — what the employer is actually paying the employee for this claim
    *   `vehicleEngineCapacityBand` (String) — `"UP_TO_1200CC"` \| `"1201_TO_1500CC"` \| `"1501CC_PLUS"`, only populated for `MILEAGE_ALLOWANCE`
    *   `cumulativeAnnualKmBeforeThisClaim` (BigDecimal) — needed because the mileage rate is itself banded by cumulative annual distance (see reference values)
    *   `kmClaimedThisPeriod` (BigDecimal)
    *   `civilServiceMileageRatePerKm` (BigDecimal) — resolved via a Java lookup (`CivilServiceMileageRateTable.lookup(vehicleEngineCapacityBand, cumulativeAnnualKmBeforeThisClaim)`), the same pattern as C-07's `BikCarRateTable` — a distance-band × engine-size matrix, not a single formula
    *   `subsistenceDurationCategory` (String) — `"OVERNIGHT_NORMAL"` \| `"OVERNIGHT_REDUCED"` \| `"OVERNIGHT_DETENTION"` \| `"TEN_HOURS_PLUS"` \| `"FIVE_TO_TEN_HOURS"`
    *   `civilServiceSubsistenceRate` (BigDecimal) — resolved via `ITaxYearRules` lookup by `subsistenceDurationCategory`
    *   **Reference values (Civil Service rates, confirmed directly from the fetched page):** motor travel per km — up to 1,500 km/year: 41.80¢ (≤1200cc) / 43.40¢ (1201–1500cc) / 51.82¢ (1501cc+); 1,501–5,500 km: 72.64¢ / 79.18¢ / 90.63¢; 5,501–25,000 km: 31.78¢ / 31.79¢ / 39.22¢; 25,001+ km: 20.56¢ / 23.85¢ / 25.87¢. Subsistence (effective 29 January 2025): overnight normal €205.53, overnight reduced €184.98, overnight detention €102.76; day rates: 10 hours+ €46.17, 5–10 hours €19.25.

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Tax-free reimbursement ceiling for this claim:*
        *EvalEx Formula:* `IF(additionCategory == "MILEAGE_ALLOWANCE", kmClaimedThisPeriod * civilServiceMileageRatePerKm, IF(additionCategory == "SUBSISTENCE_ALLOWANCE", civilServiceSubsistenceRate, 0))`
        → stored as `taxFreeReimbursementCeiling`
    *   *Step 2 — Taxable excess:*
        *EvalEx Formula:* `IF(additionCategory == "OTHER_TAXABLE", claimedAmount, MAX(0, claimedAmount - taxFreeReimbursementCeiling))`
        → stored as `taxableExcess`; per the Civil Service rates page, "payments... may not exceed the Civil Service approved rates" for the exemption to apply at all — if the employer pays above the rate, only the excess over the rate is added to `grossPayThisPeriod` for C-01/C-02/C-03, the reimbursement up to the rate remains tax-free.
    *   *Step 3 — Amount added to gross pay this period:*
        *EvalEx Formula:* `taxableExcess`
        → the tax-free portion is paid but never enters PAYE/PRSI/USC at all.

---

### C-20 - Net-to-Gross Solver

**No Tax and Duty Manual governs this** — unlike every other engine in this document, "how do I back-calculate a gross figure from a target net figure" is a payroll-software implementation technique, not a Revenue-specified calculation. Revenue publishes the forward direction only (gross → PAYE/PRSI/USC → net), which is exactly C-01+C-02+C-03 combined. This engine is included for completeness of the §3.4 S-20 `NetToGrossSolver` reference, but it is honestly a numerical method wrapping already-cited engines, not new legislation-derived math, and the EvalEx template doesn't fit it cleanly — there is no single "formula," there is a convergence loop.

*   **State Context Map:**
    *   `targetNetPay` (BigDecimal) — the take-home figure the employer wants the employee to receive this period
    *   `initialGrossEstimate` (BigDecimal) — seed value, e.g. `targetNetPay / 0.65` as a rough starting point (roughly netting off standard-rate PAYE + PRSI + USC)
    *   `convergenceTolerance` (BigDecimal) — e.g. €0.01
    *   `maxIterations` (Integer) — e.g. 25, as a safety bound
    *   All of C-01's, C-02's and C-03's own context keys, since each iteration re-invokes those engines in full

*   **Method (Java orchestration, not an EvalEx formula):**
    1. Set `grossEstimate = initialGrossEstimate`.
    2. Run C-01 (PAYE), C-02 (PRSI), C-03 (USC) with `grossPayThisPeriod = grossEstimate` (and any other deductions the employee has elected, e.g. C-15 pension contributions, applied per their own rules); sum to get `computedNetPay = grossEstimate - paye - prsi - usc - otherDeductions`.
    3. Compute the error: `computedNetPay - targetNetPay`.
    4. If `ABS(error) <= convergenceTolerance`, stop — `grossEstimate` is the answer.
    5. Otherwise, adjust `grossEstimate` (e.g. `grossEstimate = grossEstimate + error` works as a simple fixed-point iteration for most cases since the effective marginal rate is piecewise-linear and usually close to 1:1 in slope terms after one or two corrections; a bisection or secant-method approach converges faster and more safely near a rate-band boundary) and repeat from Step 2, up to `maxIterations`.
    6. If `maxIterations` is exhausted without convergence (which should not happen in practice given PAYE/PRSI/USC are all piecewise-linear, monotonic functions of gross pay), fail loudly rather than silently returning an unconverged estimate — this would indicate a bug in C-01/C-02/C-03, not a legitimate edge case.
    *   Every iteration's `(grossEstimate, computedNetPay, error)` triple should be persisted for the S-20 screen's own audit trail, distinct from but alongside the final winning iteration's normal C-01/C-02/C-03 Payslip Workings breakdown (S-23) — a reviewer should be able to see how many iterations it took and confirm the solver didn't silently settle on a wrong figure.

---

---

### C-21 - Week 53 Engine

Source: Revenue Tax and Duty Manual **Part 42-04-07, "PAYE Reviews where Week 53 applies"** (document last updated July 2022 — the page carried a "a more recent manual is available" banner, meaning a newer edition may exist; the governing statute, s.480B TCA 1997, is stable and the mechanism below is unlikely to have changed, but Track B should pull the current edition before coding), read in conjunction with **section 480B of the Taxes Consolidation Act 1997** (inserted by s.14 Finance Act 2018) and **Regulation 15 of the Income Tax (Employments) Regulations 2018, S.I. No. 345 of 2018**. Fetched and read in full, including every worked example.

**This corrects a real misunderstanding in the original roadmap.** §3.4 S-30 described `Week53TaxTreatment` as if the payroll *run itself* needed a bespoke "extra week's credit" calculation. The actual mechanism is the opposite, and simpler in the part that matters for real-time payroll:

*   **In the pay period itself** (what BlueSeer's payroll run must do): Regulation 15 requires the employer to deduct income tax **on a plain Week 1 basis** for the Week 53 payment, using the latest RPN — **no additional credit or cut-off point is given at the point of payment.** This is not a new calculation; it is C-01's *already-specified* Week1/Month1 basis (§C-01, "Step-by-Step EvalEx Math (Week1/Month1 basis)"), simply forced for this one period regardless of what basis the employee is normally on. USC follows the same forced-non-cumulative principle per TDM Part 18D-00-01 §4 (not independently fetched this session, but USC's existing Week1 mechanics in C-03 are the same shape).
*   **At end-of-year review** (a *Revenue-side* reconciliation, not a payroll-time calculation): s.480B provides that, on review of the employee's annual liability, Revenue grants an increase to certain tax credits and the standard rate band, capped at the actual Week 53 emoluments. This is genuinely a taxpayer/Revenue-level year-end adjustment — the TDM's own stated purpose is "procedures to be adopted where **reviews are being carried out**," i.e. an Income Tax return / balancing-statement process, not a payroll submission. It is specified below as an **informational preview only**, useful for feeding an accountant preparing the employee's return via the Year End Summary (S-46), never as something BlueSeer applies to a payslip.

*   **State Context Map (in-period gate):**
    *   `payDate` (LocalDate)
    *   `payFrequency` (String) — `"WEEKLY"` \| `"FORTNIGHTLY"` \| `"FOUR_WEEKLY"` \| `"MONTHLY"`
    *   `isWeek53Period` (Boolean) — resolved from `payDate`/`payFrequency` against the payroll calendar: true when a pay day falls on 31 December (or 30/31 December in a leap year) for weekly/fortnightly/4-weekly payrolls — **monthly payrolls are never affected** (TDM §2, explicit)
    *   `changedPaydayDuringYear` (Boolean) — if the employer changed the payday during the current or preceding year such that a Week 53 payday resulted, **no relief is due at all** (TDM §3.3) — this flag guards both the in-period and end-of-year paths
    *   `isNotEmployeesNormalPayday` (Boolean) — a payment (including a notional payment) made on 31 December that is *not* the employee's normal payday also gets no relief (TDM §3.3)

*   **Step-by-Step EvalEx Math (in-period — this is the part every payroll run actually needs):**
    *   *Step 1 — Force Week 1 basis for this period:*
        *EvalEx Formula:* `IF(isWeek53Period == true && changedPaydayDuringYear == false && isNotEmployeesNormalPayday == false, "WEEK1", calculationBasisOtherwise)`
        → stored as `effectiveCalculationBasisForPeriod`, passed into C-01 and C-03 as their `calculationBasis` input in place of the employee's normal RPN-supplied value for this one period only. **No new formula is required in C-01/C-03 themselves** — this engine's entire in-period job is selecting which existing basis they run under.

*   **State Context Map (end-of-year preview, informational only — not applied to any payslip):**
    *   `week53EmolumentsThisSource` (BigDecimal) — the pay actually received in the Week 53 period from this employment
    *   `annualTaxCreditsEligibleForIncrease` (BigDecimal) — the sum of whichever of the TDM §3.1 list applies to this employee (Basic Personal, Employee Tax Credit, Age, Incapacitated Child, Dependent Relative, Home Carer, Blind Person's, Widowed Parent, Single Person Child Carer, Medical Insurance, Earned Income, Seafarer, Fisher — **not** every credit an employee might have, only this named list)
    *   `payPeriodsPerYear` (Integer) — 52 or 26 (fortnightly); TDM §3.1/§3.2 state the increase is "1/52 (or 1/26 in the case of fortnightly paid employees)" — four-weekly is not separately listed in the credit/band-increase section, only in the in-period Week-53-occurrence definition, so Track B should confirm the 4-weekly treatment against the current TDM edition before this preview covers that frequency
    *   `annualStandardRateBand` (BigDecimal) — the employee's SRCOP for the relevant marital-status category
    *   `spouseOrCivilPartnerWeek53Emoluments` (BigDecimal) — nullable; only relevant for married/civil-partner cases, per TDM Examples 2, 5, 6

*   **Step-by-Step EvalEx Math (end-of-year preview):**
    *   *Step 1 — Uncapped credit increase:*
        *EvalEx Formula:* `annualTaxCreditsEligibleForIncrease / payPeriodsPerYear`
        → stored as `uncappedCreditIncrease`. TDM Example 1 confirms the arithmetic exactly: `€1,700/52 = €32.69` per credit line.
    *   *Step 2 — Credit increase capped at Week 53 emoluments (regrossed at the standard rate):*
        *EvalEx Formula:* `MIN(uncappedCreditIncrease, week53EmolumentsThisSource * standardRate)`
        → stored as `creditIncreaseFinal`. Matches TDM Example 1: uncapped credits of €65.38 regross to €326.90, but emoluments were only €300, so the cap of `€300 × 20% = €60.00` applies instead.
    *   *Step 3 — Uncapped rate band increase:*
        *EvalEx Formula:* `annualStandardRateBand / payPeriodsPerYear`
        → stored as `uncappedBandIncrease`. Matches TDM Example 4: `€36,800/52 = €707.69`.
    *   *Step 4 — Rate band increase capped at Week 53 emoluments:*
        *EvalEx Formula:* `MIN(uncappedBandIncrease, week53EmolumentsThisSource)`
        → stored as `bandIncreaseFinal`. Matches TDM Example 4: capped to €300.00, not the full €707.69.
    *   *Step 5 (married/civil-partner cases only) — joint band increase apportionment:* per TDM Examples 5–6, when both spouses have Week 53 emoluments, each spouse's own band increase is independently capped at their *own* Week 53 pay against their *own* allocated rate band, then summed — this is Steps 3–4 run twice (once per spouse) and added, not a new formula, so no separate EvalEx expression is needed beyond re-invoking Steps 3–4 with each spouse's own inputs.
    *   *Step 6 — Multiple-employment restriction:* per TDM Example 3, where an individual has more than one source of Week 53 emoluments, **only the more beneficial source** gets the increase — `MAX` across each employment's Step 2/Step 4 results, not a sum across employments. Since BlueSeer processes one employer at a time, this cross-employer comparison is out of scope for a single-employer payroll run and belongs in the accountant-facing preview only, using data the accountant supplies about the employee's other employment(s).

---

### C-22 - Illness Benefit Tax Treatment

Source: Revenue, **"Taxation of illness, occupational injury and partial capacity benefits"** — [revenue.ie/en/jobs-and-pensions/taxation-of-social-welfare-payments/illness-occupational-injury-partial-capacity-benefits.aspx](https://www.revenue.ie/en/jobs-and-pensions/taxation-of-social-welfare-payments/illness-occupational-injury-partial-capacity-benefits.aspx) (fetched directly), cross-checked against gov.ie's Illness Benefit pages and MyWelfare.ie for the employer-mandate mechanism.

**This engine is structurally identical to C-18 (Maternity/Paternity/Parent's/Adoptive/Health-and-Safety Benefit).** Illness Benefit, Occupational Injury Benefit, and Partial Capacity Benefit are taxable but **exempt from USC and PRSI**, and — exactly as with C-18 — Revenue collects the tax due by reducing the employee's tax credits and rate band directly on the RPN, never by adding the benefit to gross pay. Child dependant increases are fully exempt from Income Tax, USC and PRSI (unlike the base benefit, which is Income-Tax-liable). The one genuinely new piece, not present in C-18, is the **employer-mandate reimbursement scenario**: an employee can mandate (assign) their DSP Illness Benefit payment to be paid directly to their employer, as reimbursement for the employer continuing to pay full salary during the illness. This is confirmed by both gov.ie/MyWelfare's application guidance and Revenue's own framing that, where a sick pay scheme is in operation, *"the employer should only tax the pay-related portion of an employee's salary, and Revenue collects the tax due on any Illness Benefit payments by reducing the employee's tax credits and rate bands"* — i.e. the mandate changes who physically receives the DSP cheque, but changes **nothing** about the payroll tax treatment.

*   **State Context Map:**
    *   `employerPolicyVariant` (String) — `"NO_EMPLOYER_SICK_PAY"` \| `"EMPLOYER_PAYS_FULL_OR_PARTIAL_SALARY"` \| `"EMPLOYER_RECEIVES_MANDATE_REIMBURSEMENT"` — the three variants named in the main roadmap's S-57
    *   `employerPaidAmountThisPeriod` (BigDecimal) — whatever salary the employer is actually paying this period (zero for `NO_EMPLOYER_SICK_PAY`)
    *   `dspMandateReimbursementAmount` (BigDecimal) — only relevant for `EMPLOYER_RECEIVES_MANDATE_REIMBURSEMENT`; this is money DSP pays **to the employer's bank account**, not through payroll at all

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Amount added to gross pay in respect of the DSP Illness Benefit itself:*
        *EvalEx Formula:* `0`
        → always zero, for all three variants, for the same reason as C-18 Step 1 — Revenue's credit-reduction mechanism means the benefit never enters payroll as pay, regardless of who physically receives the DSP payment.
    *   *Step 2 — Employer-paid salary this period, if any:*
        *EvalEx Formula:* `employerPaidAmountThisPeriod`
        → ordinary taxable pay, flows into `grossPayThisPeriod` for C-01/C-02/C-03 with **no** special treatment — identical to C-18 Step 2. This applies whether or not a mandate is in effect: the mandate changes DSP's payment routing, not the employer's own payroll obligation.
    *   *Step 3 — Mandate reimbursement handling (routing note, not payroll math):*
        *EvalEx Formula:* n/a — `dspMandateReimbursementAmount` is **never** processed through employee payroll. It is a receipt into the employer's own bank account / general ledger (a debtor/AR matter), and belongs in the employer's own bookkeeping, not on any payslip or in any PAYE/USC/PRSI calculation. Track B should expose this as a simple ledger note on S-57, explicitly not wired into C-01/C-02/C-03.

---

### C-23 - CWPS (Construction Workers' Pension Scheme) Engine

Source: Construction Workers' Pension Scheme, **"Annual Contribution Rates"** — [cwps.ie/\_files/20250617_CWPS_AnnualContributionRates_RevisedSEO_1AugV2.pdf](https://www.cwps.ie/_files/20250617_CWPS_AnnualContributionRates_RevisedSEO_1AugV2.pdf) (fetched directly), rates effective **1 August 2025**, mirroring the **Sectoral Employment Order (Construction Sector) 2023**. This is a sector-body-published rate card, not a Revenue TDM — CWPS itself is the scheme administrator, and its own document is the correct primary source for its own contribution rates.

**Structurally different from C-15**: CWPS is a **flat weekly euro amount**, not a percentage of earnings — there is no age-related limit, no earnings cap, and no per-employee variation in the rate. Every CWPS-registered worker pays (and is paid for by their employer) the same weekly amount regardless of what they earn that week.

*   **State Context Map:**
    *   `isCwpsRegistered` (Boolean) — resolved from the employee's Sectoral Employment Order coverage status
    *   `employerPensionContributionWeekly` (BigDecimal) — resolved from `ITaxYearRules` — **€31.87/week**
    *   `memberPensionContributionWeekly` (BigDecimal) — resolved from `ITaxYearRules` — **€21.27/week**
    *   `employerDeathInServiceContributionWeekly` (BigDecimal) — resolved from `ITaxYearRules` — **€1.17/week**
    *   `memberDeathInServiceContributionWeekly` (BigDecimal) — resolved from `ITaxYearRules` — **€1.17/week**
    *   `employerSickPayContributionWeekly` (BigDecimal) — resolved from `ITaxYearRules` — **€2.37/week**
    *   `memberSickPayContributionWeekly` (BigDecimal) — resolved from `ITaxYearRules` — **€0.63/week**
    *   `includesVoluntaryHealthTrust` (Boolean) → if true, adds `memberHealthTrustContributionWeekly` = **€1.00/week** (member only, no employer portion)
    *   `includesVoluntaryBenevolentFund` (Boolean) → if true, adds `employerBenevolentFundContributionWeekly` = **€0.19/week** and `memberBenevolentFundContributionWeekly` = **€0.50/week**

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Member's total weekly CWPS deduction (mandatory elements):*
        *EvalEx Formula:* `memberPensionContributionWeekly + memberDeathInServiceContributionWeekly + memberSickPayContributionWeekly`
        → stored as `memberMandatoryTotal` = €21.27 + €1.17 + €0.63 = **€23.07/week**, matching the source's own "Standard Contribution Total."
    *   *Step 2 — Member's total weekly CWPS deduction including voluntary elements:*
        *EvalEx Formula:* `memberMandatoryTotal + IF(includesVoluntaryHealthTrust == true, memberHealthTrustContributionWeekly, 0) + IF(includesVoluntaryBenevolentFund == true, memberBenevolentFundContributionWeekly, 0)`
        → stored as `memberTotalDeduction` — this is the payslip deduction line
    *   *Step 3 — Employer's total weekly CWPS cost (mandatory elements):*
        *EvalEx Formula:* `employerPensionContributionWeekly + employerDeathInServiceContributionWeekly + employerSickPayContributionWeekly`
        → stored as `employerMandatoryTotal` = €31.87 + €1.17 + €2.37 = **€35.41/week**, matching the source
    *   *Step 4 — PAYE-relievable portion of the member deduction:*
        *EvalEx Formula:* `memberPensionContributionWeekly + memberDeathInServiceContributionWeekly`
        → stored as `memberPayeRelievableAmount`. The source is explicit and asterisked: *"Contributions for pension and death in service benefits are made before PAYE is calculated"* — the Sick Pay contribution and both voluntary elements are **not** marked this way, so they are deducted from **net** pay, not gross. This is a real distinction Track B must not collapse into "all CWPS deductions are pre-tax" — only two of the five/seven line items are.
    *   *Step 5 — USC/PRSI treatment:* per C-15's now-confirmed rule (pension contributions never reduce the USC/PRSI base), `memberPayeRelievableAmount` reduces the gross fed to C-01 only, never the base fed to C-02/C-03 — the same cross-engine routing rule as C-15 applies here too, since CWPS's pension element is still, fundamentally, a pension contribution for USC/PRSI purposes even though its *rate* is flat rather than percentage-based.

---

### C-24 - NECI (National Electrical Contractors of Ireland) Pension Engine

**Confidence tier is lower than every other engine in this document — flagged explicitly rather than presented as equally solid.** No primary NECI-published rate card or Revenue TDM was located or fetched this session; the figures below come from two independently-consistent secondary sources (a competitor payroll vendor's public documentation, cited twice, giving the same total and the same split). They are internally consistent with each other but neither was confirmed against NECI's own scheme documentation or a Sectoral Employment Order text the way CWPS's figures were. **Do not code `Paye2026Rules`'s NECI table from this section alone — verify directly against NECI's own published rates first.**

*   **State Context Map:**
    *   `isNeciRegistered` (Boolean)
    *   `pensionableEarningsThisPeriod` (BigDecimal) — unlike CWPS, NECI's contribution is **percentage-of-earnings**, not a flat weekly amount (matching the general shape of C-15's Standard-scheme mechanism, not CWPS's flat-rate mechanism)
    *   `employerContributionRate` (BigDecimal) — resolved from `ITaxYearRules` — **3.7%** (unverified tier — see above)
    *   `memberContributionRate` (BigDecimal) — resolved from `ITaxYearRules` — **2.5%** (unverified tier — see above)
    *   `memberMinimumWeeklyContribution` (BigDecimal) — resolved from `ITaxYearRules` — **€4.11/week** (unverified tier — see above)

*   **Step-by-Step EvalEx Math:**
    *   *Step 1 — Member contribution before minimum floor:*
        *EvalEx Formula:* `pensionableEarningsThisPeriod * memberContributionRate`
        → stored as `memberContributionRaw`
    *   *Step 2 — Member contribution, floored at the stated minimum:*
        *EvalEx Formula:* `MAX(memberContributionRaw, memberMinimumWeeklyContribution)`
        → stored as `memberContributionFinal`
    *   *Step 3 — Employer contribution:*
        *EvalEx Formula:* `pensionableEarningsThisPeriod * employerContributionRate`
        → stored as `employerContributionFinal` — no minimum-floor equivalent found in the (unverified) sourcing for the employer side; confirm whether one exists before implementation
    *   *Step 4 — PAYE/USC/PRSI treatment:* pending primary-source confirmation of whether NECI's contribution is structured as a standard occupational pension contribution (in which case C-15's/C-23 Step 5's routing rule applies directly — PAYE relief only, no USC/PRSI relief) or has scheme-specific treatment of its own. Track B should not assume parity with CWPS/Standard without checking.

---

## Open Questions for This Expansion

1. **ASC — confirm against Revenue's own Tax and Duty Manual, not just the pensions-authority page.** The €34,500/€60,000/10%/10.5%/3.33%/3.5% structure (C-05) is now sourced from `publicservicepensions.gov.ie`, a primary authority for the scheme itself, but Track B should still cross-check Revenue's ASC Tax and Duty Manual directly before `Paye2026Rules` ships, since Revenue (not the pensions authority) is the body that operationally instructs employers via payroll guidance.
2. **PRSI "reckonable pay" derivation** (C-02) is simplified to `reckonablePayThisPeriod == grossPayThisPeriod` — confirm whether any BIK/notional-pay components need exclusion before Track B implements. (The DSP's SW14/"PRSI Class A Rates" page confirmed the rate table itself but does not define reckonable pay's exact composition.) **Now sharper given C-07/C-08/C-09's BIK notional pay and C-10's termination lump sum:** confirm which of these notional-pay types are PRSI-reckonable — C-10 is explicitly confirmed **excluded** from PRSI (TDM Part 05-05-19 §4); C-07/C-08/C-09's BIK is explicitly confirmed **included** (TDM Part 05-01-01b §8.5's reference to "Income Tax, PRSI and USC"); C-11's cycle-to-work add-backs are not yet confirmed either way.
3. **EvalEx v3 string-equality support** (flagged in C-04) — needs a short Track B spike; if unsupported, `IF(stringVar == "X", …)` patterns throughout C-01, C-04, C-05, C-07, C-08, C-09, C-10 and C-11 need to become pre-branched Java dispatch instead of in-formula string comparison.
4. **USC-able pay vs. PAYE-able gross** (C-03) — confirm whether pension salary-sacrifice reduces the USC base the same way it reduces the PAYE base in your target scheme designs, since the two are *not* always identical under Irish rules.
5. **Legislative basis, for the Track B ticket that cites it:** PAYE/USC/BIK/termination payments/salary sacrifice all operate under the Taxes Consolidation Act 1997 (as amended by each year's Finance Act — Finance Act 2025 for the 2026 tax year, and Finance Act 2024/2023/2019/2017 for various BIK/EV provisions still in force per C-07 §6); PRSI operates under the Social Welfare Consolidation Act 2005 (as amended); ASC operates under the Public Service Pay and Pensions Act 2017. The Tax and Duty Manuals fetched this session are Revenue's own administrative interpretation of these Acts, not the statutory text itself — cite the specific TCA 1997 section numbers given in each engine's header if/when a compliance sign-off requires the primary legislative text rather than Revenue's guidance on it.
6. **C-07's rate-table lookup (`BikCarRateTable`)** is deliberately specified as a Java lookup rather than an EvalEx expression (Step 3) — confirm this is an acceptable exception to the "every number lives in the context map, every formula is EvalEx" principle stated in the Engine Integration Pattern, given a 6×4 matrix is unwieldy as nested `IF()` calls. If full EvalEx-only compliance is required for audit-trail consistency, Track B can flatten the matrix into 24 named context-map keys (e.g. `rateA1Band1`, `rateA1Band2`, …) and a single large `IF/IF/IF` chain instead.
7. **C-10's SCSB 36-month average pay calculation and RCS present-value calculation** are both explicitly deferred to upstream services (TDM §3.5's Covid-period adjustment logic and TDM §3.6's actuarial present-value method, itself deferred by Revenue to "Appendix V of the Revenue Pensions Manual" — not fetched this session). These are non-trivial calculations in their own right and are strong candidates for their own further expansion on request.
8. ~~C-11's Cycle to Work figures unverified~~ — **resolved.** Fetched directly from Revenue's own Cycle to Work page; corrected to three categories (€1,250 / €1,500 / **€3,000 cargo-ecargo, previously missing entirely**) and confirmed the four-year re-use rule.
9. ~~C-12's variable-hours reference-period averaging unverified~~ — **resolved.** Fetched S.I. No. 607/2022 directly from the Irish Statute Book. Corrected two errors: the reference period is **13 weeks, not 4**, and there are **three** daily-rate categories, not two (a fixed-hourly-rate-but-variable-hours case was missing entirely).
10. **C-13's sub-50%-shareholding proprietary-director test** (the Code of Practice case-by-case determination) is out of this engine's scope by design — it's a judgment call, not a formula, and the roadmap's Employee Maintenance screen (S-04/S-06) should expose `isProprietaryDirector` as a manually-set flag with the ≥50% case pre-populated automatically and the <50% case left to the preparer's professional judgment, not auto-computed.
11. ~~C-15's USC/PRSI treatment of pension contributions unconfirmed~~ — **resolved.** Revenue's own page states directly that pension contributions (including AVCs) must **not** be deducted from gross pay when calculating USC or PRSI — confirmed for the Standard scheme (C-15) and, by extension, applied consistently to CWPS's pension element (C-23) and flagged as unconfirmed-by-extension for NECI (C-24, itself a lower-confidence engine — see item 16).
12. **Both C-13 (Director PRSI) and C-14 (Auto-Enrolment) interact with C-02's employer-PRSI posting to the Journal Export (S-63)** — Track B should confirm the chart-of-accounts mapping treats "no employer PRSI" (Class S) and "employer AE contribution, not PRSI" as distinct GL lines from ordinary Class A employer PRSI, since conflating them would misstate the employer's PRSI liability on the Revenue Payments Record (S-68).
13. **C-17's PRSI-not-spread assumption is BlueSeer's own inference, not a Revenue statement.** The fetched Holiday Pay page only addresses PAYE/USC spreading; it says nothing about PRSI. Track B should get written confirmation (Revenue technical query, or corroboration from a payroll-software peer's documented behaviour) before shipping, since getting this wrong either overcharges or undercharges PRSI on every advance-holiday-pay run.
14. **C-16's RTSO-for-unapproved-options-post-2024 claim** ("employer remits via payroll using RPN-based marginal rates, not the flat 40%/8%") is inferred from the RTSO calculation page's framing rather than quoted verbatim with a rate mechanism spelled out — the exact payroll mechanics for unapproved share options specifically (as opposed to RSUs, which are fully documented in Chapter 2) should be confirmed against the Share Schemes Manual's options-specific chapter before S-26 is built to handle both instrument types identically.
15. **C-19 deliberately does not enumerate every s.112 exemption category** (only travel/subsistence, cross-referencing the small-benefit and cycle-to-work exemptions already covered in C-05/C-11). If BlueSeer's actual customer base uses other common tax-free addition types (e.g. flat-rate expense allowances by trade, remote-working e-worker relief), those need their own TDM lookups before go-live — s.112's default of "taxable unless a specific exemption applies" is the correct fallback rule in the meantime, and is already safe (it never under-taxes).
16. **C-24 (NECI) is the one engine in this document sourced entirely from secondary material** — no primary NECI or Revenue document was located this session. The 3.7%/2.5%/€4.11 figures are internally consistent across two independent secondary citations but neither is authoritative. Do not seed `Paye2026Rules`'s NECI table from this document without a follow-up primary-source fetch.
17. **C-21's end-of-year Week 53 credit/band-increase preview does not yet cover four-weekly pay frequency.** TDM Part 42-04-07 defines Week 53 itself as applying to weekly, fortnightly, *and* four-weekly payrolls, but its own worked examples and the "1/52 or 1/26" increase language only address weekly and fortnightly. Confirm the four-weekly divisor (presumably 1/13, but not stated in the fetched TDM) before extending C-21's end-of-year preview to four-weekly employees.
18. **C-21's fetched TDM (Part 42-04-07) displayed a "more recent manual is available" banner.** The mechanism (s.480B TCA 1997) is stable statute and unlikely to have changed, but Track B should pull the current edition rather than treat July 2022 as final.
19. **C-22's Illness Benefit engine assumes Occupational Injury Benefit and Partial Capacity Benefit share identical tax/USC/PRSI treatment to Illness Benefit itself** — the source page bundles all three together, but this document did not independently verify the Partial Capacity Benefit case has no special conditions of its own.

This completes the full set of engines identified from the roadmap's Screen-Level Backlog (§3) and the user's original non-exhaustive list, **plus C-21 (Week 53), C-22 (Illness Benefit), C-23 (CWPS) and C-24 (NECI) — the four gaps surfaced by the screen-specs expansion and closed in this pass.** Every engine in this document is either **fully sourced and complete** or **explicitly marked with the precise sub-piece that still needs a follow-up primary-source fetch** before Track B codes it — none are presented as complete when they are not. C-24 (NECI) is the sole exception worth naming directly: it is the only engine here built entirely on secondary sourcing, and is flagged as such rather than silently blended in with the rest. C-20 (Net-to-Gross) and C-06 (Tax-Year Rules Registry) are intentionally documented as algorithms/resolvers rather than statutory calculations, because that is what they actually are.

---

## Sources

Primary sources consulted directly during this research pass (fetched and read, not inferred from search-result summaries):

- Revenue Commissioners, **"Budget 2026 Summary"** — [revenue.ie/en/corporate/press-office/budget-information/current-year/budget-summary.pdf](https://www.revenue.ie/en/corporate/press-office/budget-information/current-year/budget-summary.pdf), published 7 October 2025. Source for: tax rates/bands, SRCOP, tax credits, USC standard and reduced rates.
- Revenue Commissioners, **"The Emergency Basis of Tax & USC Deduction"** (form RPC020012, 2026 edition with 2022–2025 comparatives) — [revenue.ie/en/jobs-and-pensions/documents/emergency-rates.pdf](https://www.revenue.ie/en/jobs-and-pensions/documents/emergency-rates.pdf). Source for: the corrected emergency PAYE/USC mechanism in C-01.
- Department of Social Protection, **"PRSI Class A Rates"** — [gov.ie/en/department-of-social-protection/publications/prsi-class-a-rates](https://www.gov.ie/en/department-of-social-protection/publications/prsi-class-a-rates/), published 4 February 2020, last updated 10 June 2026. Source for: the full C-02 PRSI subclass/rate/threshold table, including the 1 October 2026 step change.
- Department of Public Expenditure, NDP Delivery and Reform, **"Additional Superannuation Contribution (ASC)"** — [publicservicepensions.gov.ie/en/topic/additional-superannuation-contribution-asc](https://www.publicservicepensions.gov.ie/en/topic/additional-superannuation-contribution-asc/). Source for: the corrected C-05 band structure.
- Thesaurus Payroll Manager (Ireland) 2026 documentation, `thesaurus.ie/docs/2026/` — used throughout as the baseline exemplar for screen flow and mechanism description (per the main roadmap document), and specifically its `2026 Budget - Employer Summary`, `PRSI`, `USC — General Information`, and `Additional Superannuation Contribution (ASC)` pages for cross-checking the above against a payroll-software vendor's own operational interpretation.
- Revenue Commissioners, **Tax and Duty Manual Part 05-01-01b, "Chapter 2 - Employer provided vehicles"** — [revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-01-01b.pdf](https://www.revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-01-01b.pdf), last updated December 2025. Source for C-07 (BIK Cars) and C-08 (BIK Vans) in full, including Table A, Table B, the 2026 category-A1/EV changes, and all cited worked examples.
- Revenue Commissioners, **Tax and Duty Manual Part 05-01-01d, "Chapter 4 - The provision of preferential loans"** — [revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-01-01d.pdf](https://www.revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-01-01d.pdf), last reviewed October 2024. Source for C-09 (BIK Preferential Loans) in full.
- Revenue Commissioners, **Tax and Duty Manual Part 05-05-19, "Payments on Termination of an Office or Employment or Removal from an Office or Employment"** — [revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-05-19.pdf](https://www.revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-05-19.pdf), updated March 2026. Source for C-10 (Termination Lump Sum / SCSB) in full.
- Revenue Commissioners, **Tax and Duty Manual Part 05-01-01k, "Chapter 11 - Salary sacrifice arrangements"** — [revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-01-01k.pdf](https://www.revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-01-01k.pdf), last reviewed November 2025. Source for C-11 (Cycle to Work / Salary Sacrifice) mechanism in full.
- Revenue Commissioners, **"Cycle to Work scheme"** — [revenue.ie/en/jobs-and-pensions/taxation-of-employer-benefits/cycle-to-work-scheme.aspx](https://www.revenue.ie/en/jobs-and-pensions/taxation-of-employer-benefits/cycle-to-work-scheme.aspx). Source for C-11's corrected three-category exemption limits (€1,250 / €1,500 / €3,000) and the confirmed four-year re-use rule, superseding an earlier secondary-sourced draft that was missing the €3,000 cargo/e-cargo category entirely.
- Revenue's Tax and Duty Manual index — [revenue.ie/en/tax-professionals/tdm/index.aspx](https://www.revenue.ie/en/tax-professionals/tdm/index.aspx) — used to navigate to the above; this is the canonical index the user pointed to and is the right starting point for any further engine expansion.
- Workplace Relations Commission, **"Sick Leave"** — [workplacerelations.ie/en/what_you_should_know/leave/sick-leave/](https://www.workplacerelations.ie/en/what_you_should_know/leave/sick-leave/), updated 9 April 2025. Source for C-12's 70%/€110/day rate figures.
- Department of Social Protection, **"Illness Benefit, Injury Benefit and Statutory Sick Leave in 2026"** — [gov.ie/en/department-of-social-protection/publications/illness-benefit-injury-benefit-and-statutory-sick-leave-in-2025/](https://www.gov.ie/en/department-of-social-protection/publications/illness-benefit-injury-benefit-and-statutory-sick-leave-in-2025/), last updated 14 January 2026. Source for C-12's confirmed 5-day 2026 entitlement and the day-6 DSP handoff.
- Department of Social Protection, **"PRSI Class S Rates"** — [gov.ie/en/department-of-social-protection/publications/prsi-class-s-rates/](https://www.gov.ie/en/department-of-social-protection/publications/prsi-class-s-rates/), published 4 February 2020, last updated 20 January 2026. Source for C-13 in full, including the 1 October 2026 rate step-change and the €650 annual minimum.
- Department of Social Protection, **"Auto-enrolment retirement savings system for employers"** — [gov.ie/en/department-of-social-protection/publications/auto-enrolment-retirement-savings-system-for-employers/](https://www.gov.ie/en/department-of-social-protection/publications/auto-enrolment-retirement-savings-system-for-employers/), published 30 August 2024, last updated 14 January 2026. Source for C-14 in full — contribution-rate table, eligibility criteria, exemption rules, the €80,000 cap, and the no-PAYE-relief-on-employee-side / no-BIK-on-employer-side treatment.
- Department of Social Protection, **"My Future Fund"** press release — [gov.ie/en/department-of-social-protection/press-releases/my-future-fund/](https://www.gov.ie/en/department-of-social-protection/press-releases/my-future-fund/), published 1 January 2026. Source for the confirmed 1 January 2026 go-live date, correcting stale secondary-source claims of a later 2026 launch.
- Revenue Commissioners, **"Tax relief limits on pension contributions"** — [revenue.ie/en/jobs-and-pensions/pension/relief/tax-relief-limits.aspx](https://www.revenue.ie/en/jobs-and-pensions/pension/relief/tax-relief-limits.aspx). Source for C-15's age-related percentage table and the €115,000 earnings cap.
- Revenue Commissioners, **Share Schemes Manual, Chapter 2, "Restricted Stock Units (RSU)"** — [revenue.ie/en/tax-professionals/tdm/share-schemes/chapter-02.pdf](https://www.revenue.ie/en/tax-professionals/tdm/share-schemes/chapter-02.pdf), last reviewed March 2025. Source for C-16 in full.
- Revenue Commissioners, **"How to calculate and pay Relevant Tax on Share Options"** — [revenue.ie/en/additional-incomes/employment-related-shares/unapproved-share-option-schemes/calculate-pay-rtso.aspx](https://www.revenue.ie/en/additional-incomes/employment-related-shares/unapproved-share-option-schemes/calculate-pay-rtso.aspx). Source for C-16's note on the pre-/post-2024 RTSO mechanism change.
- Revenue Commissioners, **"Holiday pay and advance payments"** — [revenue.ie/en/employing-people/becoming-an-employer-and-ongoing-obligations/payments-to-employees/holiday-pay-and-advance-payments.aspx](https://www.revenue.ie/en/employing-people/becoming-an-employer-and-ongoing-obligations/payments-to-employees/holiday-pay-and-advance-payments.aspx). Source for C-17, flagged as a thinner/lower-confidence source than the others (see Open Question 13).
- Revenue Commissioners, **Tax and Duty Manual Part 05-05-31, "Taxation of Maternity Benefit, Paternity Benefit, Parent's Benefit, Adoptive Benefit and Health and Safety Benefit"** — [revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-05-31.pdf](https://www.revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-05/05-05-31.pdf), last reviewed June 2025. Source for C-18 in full.
- Revenue Commissioners, **"Civil service rates"** (travel and subsistence) — [revenue.ie/en/employing-people/employee-expenses/travel-and-subsistence/civil-service-rates.aspx](https://www.revenue.ie/en/employing-people/employee-expenses/travel-and-subsistence/civil-service-rates.aspx). Source for C-19's mileage and subsistence rate tables, read alongside Tax and Duty Manual Part 05-01-06 (the general travel-and-subsistence manual this page summarises).
- **S.I. No. 607/2022 — Sick Leave Act 2022 (Prescribed Daily Rate of Payment) Regulations 2022**, Irish Statute Book — [irishstatutebook.ie/eli/2022/si/607/made/en/print](https://www.irishstatutebook.ie/eli/2022/si/607/made/en/print), fetched in full (the actual statutory instrument, not a summary of it). Source for C-12's corrected three-category daily-rate calculation and the 13-week reference period, correcting an earlier draft that used a 4-week period sourced only from a secondary snippet.
- Revenue Commissioners, **Tax and Duty Manual Part 42-04-07, "PAYE Reviews where Week 53 applies"** — [revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-42/42-04-07-20220718091214.pdf](https://www.revenue.ie/en/tax-professionals/tdm/income-tax-capital-gains-tax-corporation-tax/part-42/42-04-07-20220718091214.pdf), last updated July 2022 (a newer edition may exist — see Open Question 18). Source for C-21 in full, including all six worked examples.
- Revenue Commissioners, **"Taxation of illness, occupational injury and partial capacity benefits"** — [revenue.ie/en/jobs-and-pensions/taxation-of-social-welfare-payments/illness-occupational-injury-partial-capacity-benefits.aspx](https://www.revenue.ie/en/jobs-and-pensions/taxation-of-social-welfare-payments/illness-occupational-injury-partial-capacity-benefits.aspx). Source for C-22's core mechanism (credit-reduction via RPN, USC/PRSI exemption).
- Department of Social Protection / MyWelfare, Illness Benefit guidance (searched and cross-corroborated, not a single fetched document) — source for C-22's employer-mandate reimbursement scenario.
- Construction Workers' Pension Scheme, **"Annual Contribution Rates"** — [cwps.ie/\_files/20250617_CWPS_AnnualContributionRates_RevisedSEO_1AugV2.pdf](https://www.cwps.ie/_files/20250617_CWPS_AnnualContributionRates_RevisedSEO_1AugV2.pdf), rates effective 1 August 2025. Source for C-23 in full — the scheme administrator's own published rate card.
- Revenue Commissioners, **"Types of pension contributions"** — [revenue.ie/en/employing-people/what-constitutes-pay/employees-pension-payments/types-of-pension-contributions.aspx](https://www.revenue.ie/en/employing-people/what-constitutes-pay/employees-pension-payments/types-of-pension-contributions.aspx). Source for the now-confirmed C-15/C-23 pension-contribution USC/PRSI treatment ("you should not deduct these contributions from your employee's gross pay when calculating USC and PRSI").

Not independently verified in this pass (flagged inline where relevant): the primary legislative texts (Taxes Consolidation Act 1997, Social Welfare Consolidation Act 2005, Public Service Pay and Pensions Act 2017), Revenue's own ASC-specific Tax and Duty Manual, the Revenue Pensions Manual Appendix V (present-value method for pension lump sums, referenced by C-10), the specific gov.ie/Revenue page setting out the sub-50%-shareholding proprietary-director Code of Practice test (referenced by C-13, corroborated across multiple pages but not fetched as a single primary document), TDM Part 18D-00-01 §4 (USC's own Week 53 mechanics, referenced but not independently fetched for C-21), and **any primary source at all for C-24 (NECI)** — that engine remains secondary-sourced only, per Open Question 16.

# Payroll smoke tests

Seven `demo`-mode scripts, one per group of screens, carried over from the
ad-hoc verification passes run while building `com.blueseer.pay` (the Irish
Payroll 2026 module). Each one was actually executed against the live app
during development and confirmed to produce correct, TDM/Revenue-sourced
figures (see the module's own commit history/PR description for the exact
worked-example cross-checks - e.g. S-50's €30,000 lump sum, S-53's Category C
BIK car matching TDM's own Tony example).

Menu paths here use the submenu structure introduced when the flat 54-item
Payroll menu was split into 12 logical submenus (it no longer fit an
1920x1080 screen as a single dropdown - items past ~40 rendered off-screen
and stray clicks landed on the desktop underneath). If a menu label you're
adding contains a comma, rename it: `MainFrame.getMenus()` serializes each
menu row as a comma-joined string and re-splits on `,`, so an embedded comma
silently shifts every field after it and drops the item from the JMenuBar
with no error (confirmed the hard way - see `PayrollBikPensionsMenu`'s label,
originally "BIK, Sick Pay & Pensions", now "BIK / Sick Pay & Pensions").

These are **smoke tests, not the full E2E suite** - each one navigates to a
handful of screens, exercises the primary computation path once, and takes
screenshots. They exist to catch "does this screen even load and compute
without throwing" regressions cheaply. The proper E2E suite (happy path +
error/edge cases per flow, organised by roadmap section) lives alongside
these under `tools/ui-regression/demos/payroll/e2e/`.

## Coverage

| Script | Screens |
|---|---|
| `s02-s03-s04-registrations-cert-employee.json` | S-02 Additional PAYE Registrations, S-03 Digital Certificate Manager, S-04 Employee Maintenance (load) |
| `s50-termination-lump-sum.json` | S-50 Termination Lump Sum (C-10) |
| `s53-bik-vehicle_s59-pension-deduction.json` | S-53 BIK Cars & Vans (C-07), S-59 Pension Deduction Setup (C-15/C-23/C-24, all three scheme cards) |
| `s62-journal-mapping_s63-journal-export.json` | S-62 Payroll Journal Mapping, S-63 Journal Export |
| `s64-pay-frequency-change.json` | S-64 Pay Frequency Change wizard (opens to selection card) |
| `s65-start-new-tax-year.json` | S-65 Start New Tax Year wizard (checklist card) |
| `s66-s70-remittance-and-revenue-record.json` | S-66 Remittance Overview, S-67 Payment Due Dates, S-68 Revenue Payments Record, S-69 Returns Look-Up, S-70 Query Revenue Record |

Not covered here (see the main roadmap doc for the full screen list):
S-01, S-05 through S-49, S-51/S-52, S-54 through S-58, S-60/S-61 - these were
verified manually during development but no script was saved for them before
this pass. The E2E suite should close that gap, not just for these but with
proper error/edge-case coverage for everything above too.

## Running

From the repo root, with `bs.cfg` present (build once per session; kill any
manually-launched app instance first - see `tools/ui-regression/README.md`
for the full explanation):

```sh
mvn -q -Dmaven.compiler.release=21 dependency:build-classpath -Dmdep.outputFile=tools/ui-regression/.test-classpath.txt -DincludeScope=test
javac -encoding UTF-8 --release 21 -d tools/ui-regression/out -cp "target/classes;$(cat tools/ui-regression/.test-classpath.txt)" tools/ui-regression/src/com/blueseer/uitest/*.java

java -cp "tools/ui-regression/out;target/classes;$(cat tools/ui-regression/.test-classpath.txt)" -Djava.awt.headless=false \
  com.blueseer.uitest.UiRegressionRunner demo . tools/ui-regression/demos/payroll/smoke/s50-termination-lump-sum.json /tmp/out
```

Repeat the last line per script (or loop over `demos/payroll/smoke/*.json`),
swapping the output directory each time. Read the resulting PNGs to confirm
computed values - the runner does not assert anything itself.

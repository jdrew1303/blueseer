# Payroll E2E suite - conventions

This is the real, pass/fail test suite for `com.blueseer.pay` (Irish Payroll
2026). It's a sibling to `../smoke/` (see that folder's own README), not a
replacement: smoke tests are a handful of scripts giving quick "did this
obviously break" coverage across many screens; this suite goes deep on each
flow - happy path plus the error and edge cases that actually catch bugs.

Both use the same `DemoRunner` JSON format and the same runner command. The
difference that matters is the `assert` step (see below): a script with no
asserts is really just a recorder for a human to eyeball screenshots. A
script with asserts is a genuine test with a real pass/fail exit code.

## Why this exists

Two things prompted building this out:

1. Smoke tests were being treated as "verified" when several of their steps
   were silently failing (`ERROR:*` entries in the manifest, but the process
   always exited 0 - nobody was actually reading every manifest line). Two
   real bugs shipped unnoticed as a result: `DemoRunner` couldn't drive
   dialog content at all for a while, and a comma in a menu label
   (`PayrollBikPensionsMenu`) silently dropped that whole submenu from the
   JMenuBar. Both are now fixed, but the *process* gap - "the runner exits 0
   no matter what happened" - was the deeper problem, and it's fixed too
   (see "Pass/fail" below).
2. A second, unrelated bug (the Pension Deduction scheme-type combo leaving
   its own dropdown popup open after a programmatic selection, silently
   breaking every subsequent `combo` step) was caught *while building this
   suite*, from a script that ran the same UI action twice in a row - the
   sort of thing that only turns up under actual exercise, not a single
   happy-path click-through. That's the standard to hold every script here
   to: don't just prove the screen loads, prove it behaves under real,
   repeated, adversarial use.

## Directory layout

One folder per flow, matching the roadmap's own `docs/architecture/
irish-payroll-2026-roadmap.md` grouping (and the task breakdown this suite
was built against):

| Folder | Roadmap screens |
|---|---|
| `company-setup-and-employee-master/` | S-01 to S-13 |
| `rpn/` | S-14 to S-16 |
| `pay-processing/` | S-17 to S-24, S-30 |
| `leavers/` | S-48 to S-52 |
| `psr/` | S-31 to S-35 |
| `distribution/` | S-36 to S-39 |
| `reports/` | S-40 to S-47 |
| `bik-sick-pay-pensions/` | S-53 to S-61 |
| `journals-and-year-transition/` | S-62 to S-65 |
| `remittance/` | S-66 to S-70 |

Within each folder, one script per scenario, named for what it proves:

- `happy-path.json` - the primary flow, start to finish, with assertions on
  the final computed/saved state. There should be exactly one of these per
  folder (split further only if a folder genuinely covers two unrelated
  flows - e.g. if `distribution/` ever needs a second one for bank file vs.
  payslip printing specifically, name them `happy-path-<flow>.json`).
- `error-<condition>.json` - an input or precondition that should be
  rejected or handled gracefully (validation message, disabled button,
  computed value that correctly comes out zero/blocked) rather than silently
  producing a wrong number or throwing past the UI. Example:
  `error-negative-lump-sum.json`, `error-missing-rpn-before-finalise.json`.
- `edge-<condition>.json` - a legitimate but boundary-adjacent input that
  should still compute correctly. Example: `edge-exactly-at-cutoff.json`,
  `edge-week-53.json`, `edge-zero-years-service.json`.

`assert` can't currently read JTable cell contents (Register of Employees,
RPN Bulk Retrieval's results grid, etc.) - `findValueByLabel`/`anyShowingLabelContains`
only look at JLabel/JTextField/JComboBox. For a table-backed screen, verify
via screenshot instead (as the `reports/` and `rpn/` scripts do) rather than
asserting against table content - don't add a fragile workaround that
happens to string-match some other label near the table.

Don't be precious about the boundary between "error" and "edge" - the point
is the filename tells the next person what scenario they're looking at
without opening it, not that every case fits a strict taxonomy.

## Script format

Same JSON shape and action vocabulary as `demos/payroll/smoke/*.json` and
documented in full in the doc comment at the top of
`tools/ui-regression/src/com/blueseer/uitest/DemoRunner.java` - that comment
is the source of truth; don't let this doc drift from it. Quick summary of
what's available: `menu`, `click`, `type`, `tab`, `check`/`radio`, `combo`,
`key`, `zoom`, `wait`, `caption`, `screenshot`, `assert`.

The one that makes this suite different from the smoke tests:

```json
{ "assert": { "label": "Taxable Amount:", "equals": "8310" } }
{ "assert": { "label": "Net Pay:", "contains": "1,234" } }
{ "assert": { "label": "Some Computed Field:", "notBlank": true } }
```

`label` is looked up the same way `type`'s `label` variant is (nearest
matching-text `JLabel`, then whatever's on the same visual row to its right)
except it also matches a plain read-only `JLabel` as the value, not just a
`JTextField`/`JComboBox` - most computed payroll figures are displayed that
way (e.g. `TerminationLumpSumPanel`'s `lblTaxableAmount`), never as editable
fields. Use `equals` for exact-value checks (the normal case - text is
trimmed before comparing), `contains` when the field embeds the value in a
longer string or you only care about part of it, `notBlank` when you just
need to confirm *something* computed (no `"-"` placeholder left over) rather
than pin an exact figure - useful for values that are legitimately
non-deterministic or config-dependent.

A JLabel showing HTML (`<html>...</html>`, e.g. `lblCwpsBreakdown`'s
multi-line breakdown) returns its raw markup from `assert` - use `contains`
against a distinctive substring rather than `equals` against the whole
string.

### Pass/fail

A step that throws (target not found, assertion mismatch, whatever) doesn't
stop the run - it's logged as an `ERROR:<action>` entry in `manifest.json`
with an error screenshot, and the script keeps going, so one broken early
step doesn't hide everything after it. But the process **exit code reflects
whether every step succeeded**: 0 if clean, 1 if anything failed. Don't rely
on eyeballing the console output or the manifest by hand - check the exit
code, or grep the manifest for `"ERROR:` if you need the specific failure.

## Menu paths

The Payroll menu was restructured into 12 submenus (see
`tools/ui-regression/demos/payroll/smoke/README.md` for why - the flat
54-item version overflowed the screen). Every `menu` step here needs the
full submenu-qualified path, e.g. `"Payroll>Leavers>Termination Lump Sum"`,
not the old flat `"Payroll>Termination Lump Sum"`. Two items stayed flat:
`Payroll>Employee Maintenance` and `Payroll>Payroll Calendar`.

If you're adding a *new* menu item and its label needs a comma, don't -
`MainFrame.getMenus()` serializes menu rows as comma-joined strings and
splits on `,`, so an embedded comma silently corrupts parsing and drops the
item from the menu with no error (confirmed the hard way once already).

## Test data

The seeded dev database (`sf/data/bsdbdev.db`) has one demo company
(`CompanyId(1)`) and at least one seeded employee, `Murphy, Siobhan (W001)`,
which shows up as the default selection in every employee-selector combo
across the screens exercised so far. If a script needs a *specific*
employee (rather than whatever's already selected), select explicitly:

```json
{ "combo": { "index": 0, "select": "Murphy, Siobhan (W001)" } }
```

If a flow needs more than one employee (e.g. to test a case that only
applies to a director, or a leaver vs. a current employee) and the seeded
roster doesn't have one, that's worth raising rather than working around -
either the seed data needs another row, or the scenario needs a setup step
of its own first (e.g. via S-01/S-04 to create one) as part of the script.

## Running

Same build-once-per-session setup as the smoke tests (see
`tools/ui-regression/demos/payroll/smoke/README.md` for the full command
block and the "kill any manually-launched app instance first" caveat).

Single script:

```sh
java -cp "tools/ui-regression/out;target/classes;$(cat tools/ui-regression/.test-classpath.txt)" -Djava.awt.headless=false \
  com.blueseer.uitest.UiRegressionRunner demo . tools/ui-regression/demos/payroll/e2e/leavers/happy-path.json /tmp/out
echo "exit: $?"
```

Whole suite, with a pass/fail summary (bash):

```sh
fail=0
for f in tools/ui-regression/demos/payroll/e2e/*/*.json; do
  name=$(echo "$f" | sed 's#tools/ui-regression/demos/payroll/e2e/##; s#\.json$##; s#/#_#')
  java -cp "tools/ui-regression/out;target/classes;$(cat tools/ui-regression/.test-classpath.txt)" -Djava.awt.headless=false \
    com.blueseer.uitest.UiRegressionRunner demo . "$f" "/tmp/e2e-out/$name" > "/tmp/e2e-out/$name.log" 2>&1
  if [ $? -ne 0 ]; then echo "FAIL: $f"; fail=1; else echo "pass: $f"; fi
done
exit $fail
```

Each run launches a fresh app instance (`UiRegressionRunner`'s `demo` mode
calls `SwingAppDriver.launchAndLogin()`, which starts `com.blueseer.utl.mf`
itself) - don't run scripts concurrently against the same `bsdbdev.db`, and
don't leave a manually-launched instance running while these execute (see
the smoke README).

Running the whole suite as one tight back-to-back loop (14+ JVM launches in
a few minutes) can occasionally produce a spurious `menu path not found` or
similar timing failure on an otherwise-passing script, purely from system
load - `launchAndLogin()` polls for the JMenuBar to be populated before
returning, but that's not a hard guarantee against a genuinely starved
machine. If a script fails standalone, that's real; if it only fails inside
a large batch and passes cleanly on its own immediately after, it's this,
not a regression - don't chase it further.

## Status

Every folder has at least one script. Covered with real (non-screenshot-
only) assertions: `company-setup-and-employee-master/`, `rpn/`,
`pay-processing/`, `leavers/`, `bik-sick-pay-pensions/`, `psr/`,
`journals-and-year-transition/`, `remittance/`. Covered with
load-verification only (screenshot-only, no `assert`): `distribution/`,
`reports/` - both are read-heavy screens with little to compute wrong, so
"loads without throwing" is most of the value; add real assertions if a bug
ever turns up in one.

Known remaining gaps:
- `company-setup-and-employee-master/` doesn't cover the
  Additions/Deductions/Mid-Year Cumulatives/CSO Details tabs, or the S-01
  New Company Wizard (the latter creates a new company record - needs a
  decision on whether/how to make that safely repeatable against the shared
  dev database before scripting it).
- Several screens intentionally stop short of their genuinely one-way action
  against the shared dev database rather than run it for real: Finalise Pay
  Period, Leaver Finalise Final Payslip, PSR Submit to Revenue, Start New
  Tax Year, Journal Export's actual file write. Each of those scripts
  verifies the screen and its confirmation dialog, then declines/backs out.
  If a dedicated disposable-database mode is ever added for this suite,
  these are the scripts to extend to run the real action and assert on the
  result.

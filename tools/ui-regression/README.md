# UI regression walker

Drives the live BlueSeer Swing UI through its real component tree (menus,
tabs, checkboxes, combo boxes) via AssertJ-Swing instead of blind screen
coordinates, so the same recorded action sequence can be replayed against a
second build and the resulting screenshots diffed pairwise. Useful for:

- Checking a rebuilt/replaced `bsmf.jar` (e.g. a decompiled reconstruction)
  behaves identically to the original.
- Regression-testing future UI refactors: record once against `main`, replay
  against your branch, and anything that diffs is an unintended change.

It only ever clicks tabs, checkboxes, radio buttons, and combo boxes (plus
menu navigation) - never a plain button or menu item whose text matches a
blocklist (delete/exit/print/remove/...). It's read-mostly by design since it
walks a real, if disposable, database copy.

## Usage

Both commands need a working directory containing `bs.cfg`, `data/`, etc.
(e.g. a copy of `target/`) whose `dist/` has the `bsmf.jar` build under test.

```sh
# Record: walks the "Inventory" menu (by substring match against the full
# ">"-joined path), capping at 40 leaf screens, logging every action.
tools/ui-regression/run.sh record /path/to/workdir-A /tmp/record-out --filter Inventory --max-leaves 40

# Replay: re-runs the exact same recorded actions against a second build.
tools/ui-regression/run.sh replay /path/to/workdir-B /tmp/record-out/actions.log /tmp/replay-out

# Diff: pairwise ImageMagick compare, sorted worst-first.
tools/ui-regression/diff-report.sh /tmp/record-out /tmp/replay-out /tmp/report
```

`--filter` matches anywhere in the menu path, so `--filter "Item Maintenance"`
targets one specific screen instead of a whole top-level menu. Omit it to
walk every menu (slow - there's no cap on how much of the app that is).
`--seed N` (default 42) seeds the combo-box item picks so record/replay make
the same "random" choices.

## Notes / known limitations

- Requires a real (or Xvfb) display - AssertJ-Swing's Robot needs one.
- Component resolution during replay is by (class, occurrence-index-within-
  the-same-scan), not object identity, so it tolerates the two builds having
  different object instances but not a different component tree shape - a
  shape change shows up either as a MISMATCH log line or a screenshot diff.
- This walks the app fast and deterministically, which turned out to matter:
  a UI bug that reproduces reliably under manual/xdotool clicking may *not*
  reproduce here, and vice versa, if it's actually a timing-sensitive race
  rather than a deterministic logic difference. Treat a clean replay as
  "no diff at this click cadence," not an absolute guarantee - if you suspect
  a race, rerun a few times and/or compare against a manual reproduction.
- `run.sh` rebuilds automatically the first time (or with `--rebuild`); it
  shells out to `mvn dependency:build-classpath` to resolve `assertj-swing`
  and friends, so it needs network access the first time that's not cached.

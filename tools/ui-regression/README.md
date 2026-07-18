# UI regression walker / demo-video capture

Drives the live BlueSeer Swing UI through its real component tree (menus,
tabs, checkboxes, combo boxes, buttons, text fields) via AssertJ-Swing
instead of blind screen coordinates. Two independent uses share the same
driver (`SwingAppDriver.java`):

- **`record` / `replay`** (`UiRegressionRunner.java`): auto-explores the menu
  tree and records a pixel-diffable action log, so the same sequence can be
  replayed against a second build (e.g. a different `bsmf.jar`) and the
  resulting screenshots diffed pairwise. Useful for checking a
  rebuilt/replaced jar behaves identically to the original, or
  regression-testing future UI refactors: record once against `main`, replay
  against your branch, anything that diffs is an unintended change. It only
  ever clicks tabs, checkboxes, radio buttons, and combo boxes (plus menu
  navigation) - never a plain button or menu item whose text matches a
  blocklist (delete/exit/print/remove/...) - since it's read-mostly by design
  (walks a real, if disposable, database copy).
- **`demo`** (`DemoRunner.java`): plays back an explicit, curated JSON script
  (testreel-style) instead of auto-exploring, and `demo.sh` wraps the whole
  thing in Xvfb + fluxbox + ffmpeg to produce an actual video - this is the
  demo-video capture path. No blocklist here: it runs exactly what the script
  says, so use a disposable/demo database. See "Demo-video capture" below.

## Usage (record / replay)

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

## Demo-video capture

```sh
sudo apt-get install -y xvfb fluxbox ffmpeg   # one-time, if not already present
tools/ui-regression/demo.sh /path/to/workdir tools/ui-regression/example-demo.json out.mp4
```

`workdir` is the same kind of directory `run.sh` needs (`bs.cfg`, `data/`,
`dist/`, etc. - e.g. a copy of `target/`). Use a database that's already past
BlueSeer's first-run "Choose Country of Origin" setup dialog: a fresh,
never-configured `bsdb.db` throws that up on first login and needs an app
restart to clear, which isn't something worth scripting around since it's
one-time setup, not normal app behavior - just point `demo.sh` at a workdir
whose DB has already been through that once.

`demo.sh` starts Xvfb, starts fluxbox inside it (a bare Xvfb has no window
manager, so `MainFrame`'s `setExtendedState(MAXIMIZED_BOTH)` call on launch
never actually resizes anything - the window stays pinned at its tiny
pre-login `pack()` size without one), starts `ffmpeg -f x11grab` recording
that display, runs the script via `run.sh demo`, then stops the raw capture
(SIGINT, so the mp4 container finalizes instead of coming out corrupt),
optionally runs a second ffmpeg pass to apply any zoom effects (see below),
and prints where the final video, the raw pre-zoom recording, and a
`manifest.json` ended up.

`demos/` has a few complete, verified scripts covering common flows
(`tour.json`, `new-product-code.json`, `new-customer.json`) - a working
starting point to copy from, and evidence of what actually works end to end
against a real build rather than just documentation.

Every click-like step moves the real pointer to the target with a brief
eased glide first, rather than teleporting there - the entire point of this
mode is a human watching along, unlike `record`/`replay`'s instant clicks.
Typing likewise visibly clicks into the field before entering text.

### Script format

```json
{
  "steps": [
    { "caption": "Welcome to BlueSeer ERP", "wait": 1500 },
    { "menu": "Inventory>Product Code Maintenance", "wait": 1500 },
    { "click": "New", "wait": 800 },
    { "type": { "label": "Key", "text": "D001" } },
    { "zoom": { "label": "Key", "level": 2.2 }, "wait": 1500 },
    { "tab": { "title": "Detail" } },
    { "combo": { "index": 0, "select": "Each" } },
    { "key": "ENTER" },
    { "screenshot": "final-state.png" }
  ]
}
```

Every step has exactly one action key, plus optional `"wait"` (ms to pause
*after* that action - defaults to 600ms; a bare `{"wait": N}` step is just
that pause on its own) and `"caption"` (logged to the manifest and console
for narration/subtitle authoring later, not rendered on screen) and
`"screenshot"` (captures a discrete PNG alongside the continuous video - a
bare `{"screenshot": "name.png"}` step just captures on its own).

| Action | Shape | Notes |
|---|---|---|
| `menu` | `"Top>Sub>Leaf"` | Matches `JMenu`/`JMenuItem` text exactly, `>`-joined. |
| `click` | `"Button Text"` | Finds a `JButton` by exact text in the current content pane. |
| `type` | `{"label": "Key", "text": "..."}` or `{"index": 0, "text": "..."}` | `label` finds the `JLabel` matching (exact match preferred, else case-insensitive substring), then the `JTextField`/`JComboBox` on the same visual row to its right - geometric, not tree-structural, since BlueSeer's generated panels aren't consistent about *how* a label and its field get parented (see `SwingAppDriver.findFieldByLabel`'s doc comment for a concrete example of two different patterns on two different screens). Fall back to `index` (nth visible `JTextField`) if a screen's layout still confuses it. |
| `tab` | `{"pane": 0, "title": "..."}` or `{"pane": 0, "index": 1}` | `pane` selects which `JTabbedPane` on screen (0 = first found), default 0. |
| `check` / `radio` | `0` | Clicks the nth checkbox/radio button found on screen. |
| `combo` | `{"index": 0, "item": 1}` or `{"index": 0, "select": "text"}` | `index` picks which combo box; `item`/`select` pick its value. |
| `key` | `"ENTER"` | Any `java.awt.event.KeyEvent.VK_*` name. |
| `zoom` | `{"label": "Key", "level": 2.0}` or `{"button": "New", "level": 2.0}` or `{"rect": [x,y,w,h]}` | Punches in on the target, holds for `"wait"` ms (default 1500), eases back out. See "Zoom" below. |
| `wait` | `1500` | Pause only. |
| `caption` | `"..."` | Log only, no UI action. |
| `screenshot` | `"name.png"` | Captures a PNG, no other action. |

A failed step (menu path/button/field not found) is logged to the manifest
as an `ERROR:` entry (with its own screenshot of what was actually on
screen, `error-N.png`) and the script continues - it doesn't abort the whole
recording over one bad selector.

### Zoom

Implemented as a second ffmpeg pass over the finished raw recording, not a
live effect - `crop`'s width/height turned out not to be reconfigurable
per-frame in practice (only its x/y position is; confirmed empirically, not
just from docs), so the actual technique is: `scale` the whole frame up by a
time-varying factor (`scale` *does* support this, via `eval=frame`), then
`crop` a constant-size window that pans to stay centered on the target as
the frame grows underneath it. DemoRunner records each zoom event's target
center and video-relative timing (using the `DEMO_RECORDING_EPOCH_MS` /
`DEMO_WIDTH` / `DEMO_HEIGHT` env vars `demo.sh` sets, to correlate its own
wall clock with ffmpeg's capture timeline - accurate to within ffmpeg's own
x11grab startup latency, maybe a few hundred ms, not frame-exact) and writes
the resulting filter graph to `<outDir>/zoom-filter.txt`; `demo.sh` applies
it automatically if any zoom steps ran. The recording holds still for the
entire ease-in + hold + ease-out window while a zoom step executes, since
the crop is computed against those exact timestamps.

Don't overuse it - one or two zooms on the moment that actually matters
reads as emphasis; zooming on every field reads as seasick.

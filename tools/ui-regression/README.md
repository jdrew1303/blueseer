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
that display, runs the script via `run.sh demo`, then stops everything
(SIGINT to ffmpeg specifically, so the mp4 container finalizes instead of
coming out corrupt) and prints where the video and a `manifest.json` ended
up.

### Script format

```json
{
  "steps": [
    { "caption": "Welcome to BlueSeer ERP", "wait": 1500 },
    { "menu": "Inventory>Product Code Maintenance", "wait": 1500 },
    { "click": "New", "wait": 800 },
    { "type": { "label": "Key", "text": "DEMO-001" } },
    { "tab": { "title": "Detail" } },
    { "combo": { "index": 0, "select": "Each" } },
    { "key": "ENTER" }
  ]
}
```

Every step has exactly one action key, plus optional `"wait"` (ms to pause
*after* that action - defaults to 400ms; a bare `{"wait": N}` step is just
that pause on its own) and `"caption"` (logged to the manifest and console
for narration/subtitle authoring later, not rendered on screen) and
`"screenshot"` (also captures a discrete PNG alongside the continuous video).

| Action | Shape | Notes |
|---|---|---|
| `menu` | `"Top>Sub>Leaf"` | Matches `JMenu`/`JMenuItem` text exactly, `>`-joined. |
| `click` | `"Button Text"` | Finds a `JButton` by exact text in the current content pane. |
| `type` | `{"label": "Key", "text": "..."}` or `{"index": 0, "text": "..."}` | `label` finds the field whose nearest preceding `JLabel` matches (case-insensitive substring) - a heuristic that works for BlueSeer's generated forms but isn't a real association, so fall back to `index` (nth `JTextField`) if it picks the wrong one. |
| `tab` | `{"pane": 0, "title": "..."}` or `{"pane": 0, "index": 1}` | `pane` selects which `JTabbedPane` on screen (0 = first found), default 0. |
| `check` / `radio` | `0` | Clicks the nth checkbox/radio button found on screen. |
| `combo` | `{"index": 0, "item": 1}` or `{"index": 0, "select": "text"}` | `index` picks which combo box; `item`/`select` pick its value. |
| `key` | `"ENTER"` | Any `java.awt.event.KeyEvent.VK_*` name. |
| `wait` | `1500` | Pause only. |
| `caption` | `"..."` | Log only, no UI action. |

A failed step (menu path/button/field not found) is logged to the manifest
as an `ERROR:` entry and the script continues - it doesn't abort the whole
recording over one bad selector.

package com.blueseer.uitest;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import java.awt.Component;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Plays back a JSON-scripted sequence of UI actions against the live
 * BlueSeer Swing app, testreel-style, so a curated demo flow (not the
 * auto-exploring walk UiRegressionRunner does) can be scripted once and
 * re-run identically for video capture. See demo.sh for wrapping this in
 * Xvfb + fluxbox + ffmpeg to actually produce a video, and
 * example-demo.json for the schema by example.
 *
 * Unlike UiRegressionRunner's auto-explorer, this has no blocklist - it runs
 * exactly what the script says, including destructive-looking actions if you
 * put them there. Always point it at a disposable/demo database, never
 * production data.
 *
 * Every click-like action moves the real pointer to the target with a brief
 * eased glide (see SwingAppDriver.smoothMoveAndClick) instead of teleporting
 * - the whole point of this mode is a human being able to watch and follow
 * along, unlike the fast/instant clicks UiRegressionRunner uses.
 *
 * Script shape:
 *   { "steps": [ { <one action key>, "wait"?: ms, "caption"?: "...", "screenshot"?: "name.png" }, ... ] }
 *
 * Action keys (exactly one per step, except a step can be just "wait",
 * "caption", or "screenshot" on their own):
 *   "menu": "Top>Sub>Leaf"                        - menu bar navigation
 *   "click": "Button Text"                        - click a JButton by its text
 *   "type": {"label": "Key", "text": "..."}       - type into the field on the same row as, and
 *            or {"index": 0, "text": "..."}         right of, the JLabel matching (or nth JTextField)
 *   "tab": {"pane": 0, "title": "..."}            - select a JTabbedPane tab, by title or index
 *           or {"pane": 0, "index": 1}
 *   "check": 0 / "radio": 0                       - click the nth checkbox/radio button found
 *   "combo": {"index": 0, "item": 1}              - set a combo box's selection, by item index
 *             or {"index": 0, "select": "text"}     or by matching its string value
 *   "key": "ENTER"                                - press a key (java.awt.event.KeyEvent.VK_* name)
 *   "zoom": {"label": "Key", "level": 2.0}         - punch in on a target for a beat, then ease back
 *            or {"button": "New", "level": 2.0}      out ("level" is the zoom factor, default 2.0;
 *            or {"rect": [x,y,w,h]}                  "rect" is used as-is, ignoring "level"). See
 *                                                     "Zoom" below - implemented as a post-processing
 *                                                     video crop, not a live effect.
 *   "wait": 1000                                  - pause only
 *   "caption": "Creating a new item"               - no-op, just logged to manifest.json (and
 *                                                    console) for narration/subtitle authoring later
 *   "screenshot": "name.png"                       - no-op besides capturing a PNG (on its own; see
 *                                                    below for capturing alongside another action)
 *   "assert": {"label": "Taxable Amount:",         - read the field/value on the same row as, and
 *              "equals": "1234.56"}                  right of, the JLabel matching "label" (JLabel,
 *             or {"label": "...", "contains": "x"}    JTextField, or JComboBox - unlike "type", this
 *             or {"label": "...", "notBlank": true}   also matches read-only JLabel results, e.g. a
 *                                                      computed payslip figure), and compare it.
 *             or {"text": "not been retrieved"}      - for a self-describing JLabel with no separate
 *                or {"text": "...", "visible": false}  caption+value pair (a standalone banner/
 *                                                       warning): true/false (default true) for
 *                                                       whether any currently-showing JLabel contains
 *                                                       this text.
 *                                                      A failed assertion fails the step exactly like
 *                                                      any other error (see "Pass/fail" below) - this
 *                                                      is what makes a script an actual test instead
 *                                                      of just a screenshot-taking walkthrough.
 *
 * "wait" and "caption" can also ride along on any other step: "wait" becomes
 * that step's post-action pause (default 600ms if omitted), "caption" is
 * logged alongside it. "screenshot" likewise captures a PNG right after
 * whatever action the step performed, in addition to the continuous video.
 *
 * Pass/fail: a step that throws (target not found, assertion mismatch, ...)
 * doesn't stop the run - it's logged as "ERROR:<action>" in manifest.json
 * with an error screenshot, and the run continues, so one broken step near
 * the start doesn't hide everything after it. But unlike smoke-test usage
 * (where a human reads the screenshots), run() now returns false if *any*
 * step failed, and UiRegressionRunner's "demo" mode exits 1 in that case -
 * a script with assertions is a real pass/fail test, not just a recording.
 *
 * Zoom: DemoRunner records each zoom event's on-screen target rect and its
 * *video-relative* timing (using the DEMO_RECORDING_EPOCH_MS/DEMO_WIDTH/
 * DEMO_HEIGHT env vars demo.sh sets to correlate wall-clock time with the
 * ffmpeg capture's own timeline), then - after all steps finish - writes a
 * ready-to-use ffmpeg crop/scale filter graph to outDir/zoom-filter.txt.
 * demo.sh runs that as a second ffmpeg pass over the raw recording. Doing it
 * this way (post-process the finished recording) instead of trying to drive
 * ffmpeg's capture live keeps the timing exact - it's ordinary crop/scale
 * math against known timestamps, not something that has to happen in
 * real time while the app is still running.
 */
final class DemoRunner {

    private DemoRunner() {
    }

    /** Ease-in/ease-out duration on either side of a zoom's hold, in ms. */
    private static final int ZOOM_EASE_MS = 500;
    private static final int DEFAULT_ZOOM_HOLD_MS = 1500;
    private static final int DEFAULT_POST_ACTION_WAIT_MS = 600;

    private static final class ZoomEvent {
        double t0, t1, t2, t3; // video-relative seconds: ease-in start/end, hold end, ease-out end
        double cx, cy;         // target center, in original capture pixel coordinates
        double level;          // zoom factor (2.0 = crop to half width/height, scaled back up)
    }

    /** @return true if every step succeeded (no ERROR entries in the manifest). */
    static boolean run(File scriptFile, File outDir) throws Exception {
        outDir.mkdirs();
        JSONObject root;
        try (FileReader reader = new FileReader(scriptFile)) {
            root = new JSONObject(new JSONTokener(reader));
        }
        JSONArray steps = root.getJSONArray("steps");

        SwingAppDriver driver = new SwingAppDriver();
        ScreenshotUtil screenshots = new ScreenshotUtil();
        long startMs = System.currentTimeMillis();
        long recordingEpochMs = Long.parseLong(
                System.getenv().getOrDefault("DEMO_RECORDING_EPOCH_MS", String.valueOf(startMs)));
        int captureWidth = Integer.parseInt(System.getenv().getOrDefault("DEMO_WIDTH", "1280"));
        int captureHeight = Integer.parseInt(System.getenv().getOrDefault("DEMO_HEIGHT", "900"));
        JSONArray manifest = new JSONArray();
        List<ZoomEvent> zoomEvents = new ArrayList<>();
        int[] seq = {0};
        int[] failures = {0};

        Frame frame = driver.launchAndLogin();
        JFrame jframe = (JFrame) frame;
        manifest.put(manifestEntry(seq[0]++, "login", "admin", null, startMs));

        for (int i = 0; i < steps.length(); i++) {
            JSONObject step = steps.getJSONObject(i);
            String action = null;
            String detail = null;
            try {
                if (step.has("menu")) {
                    action = "menu";
                    detail = step.getString("menu");
                    List<String> path = new ArrayList<>(List.of(detail.split(">")));
                    if (!driver.navigateMenuPathSmooth(jframe.getJMenuBar(), path)) {
                        throw new IllegalStateException("menu path not found: " + detail);
                    }
                } else if (step.has("click")) {
                    action = "click";
                    detail = step.getString("click");
                    javax.swing.JButton b = driver.findButtonByText(driver.currentInteractionRoot(jframe), detail);
                    if (b == null) {
                        throw new IllegalStateException("button not found: " + detail);
                    }
                    driver.smoothMoveAndClick(b);
                } else if (step.has("type")) {
                    action = "type";
                    JSONObject t = step.getJSONObject("type");
                    String text = t.getString("text");
                    Component field;
                    if (t.has("label")) {
                        field = driver.findFieldByLabel(driver.currentInteractionRoot(jframe), t.getString("label"));
                        detail = t.getString("label") + "=" + text;
                    } else if (t.has("index")) {
                        // Showing-only, unlike a plain tree walk - the login screen's
                        // username/password fields never get removed from the tree,
                        // just hidden, so an unfiltered scan would silently shift every
                        // index by 2.
                        List<Component> scan = new ArrayList<>();
                        driver.collectShowing(driver.currentInteractionRoot(jframe), JTextField.class, scan);
                        field = driver.nthOfType(scan, JTextField.class, t.getInt("index"));
                        detail = "#" + t.getInt("index") + "=" + text;
                    } else {
                        throw new IllegalArgumentException("type step needs \"label\" or \"index\"");
                    }
                    if (field == null) {
                        throw new IllegalStateException("field not found for type step: " + detail);
                    }
                    driver.typeIntoField(field, text);
                } else if (step.has("tab")) {
                    action = "tab";
                    JSONObject t = step.getJSONObject("tab");
                    int paneIdx = t.optInt("pane", 0);
                    List<Component> scan = driver.findTargets(driver.currentInteractionRoot(jframe));
                    JTabbedPane pane = driver.nthOfType(scan, JTabbedPane.class, paneIdx);
                    if (pane == null) {
                        throw new IllegalStateException("tab pane #" + paneIdx + " not found");
                    }
                    int tabIdx;
                    if (t.has("title")) {
                        tabIdx = -1;
                        for (int k = 0; k < pane.getTabCount(); k++) {
                            if (t.getString("title").equals(pane.getTitleAt(k))) {
                                tabIdx = k;
                                break;
                            }
                        }
                        if (tabIdx < 0) {
                            throw new IllegalStateException("tab titled " + t.getString("title") + " not found");
                        }
                    } else {
                        tabIdx = t.getInt("index");
                    }
                    detail = "pane " + paneIdx + " -> " + pane.getTitleAt(tabIdx);
                    driver.clickTabSmooth(pane, tabIdx);
                } else if (step.has("check") || step.has("radio")) {
                    boolean isCheck = step.has("check");
                    action = isCheck ? "check" : "radio";
                    int idx = step.getInt(action);
                    List<Component> scan = driver.findTargets(driver.currentInteractionRoot(jframe));
                    Component c = isCheck ? driver.nthOfType(scan, JCheckBox.class, idx)
                                           : driver.nthOfType(scan, JRadioButton.class, idx);
                    if (c == null) {
                        throw new IllegalStateException(action + " #" + idx + " not found");
                    }
                    driver.smoothMoveAndClick(c);
                    detail = "#" + idx;
                } else if (step.has("combo")) {
                    action = "combo";
                    JSONObject t = step.getJSONObject("combo");
                    int idx = t.getInt("index");
                    List<Component> scan = driver.findTargets(driver.currentInteractionRoot(jframe));
                    JComboBox<?> combo = driver.nthOfType(scan, JComboBox.class, idx);
                    if (combo == null) {
                        throw new IllegalStateException("combo #" + idx + " not found");
                    }
                    int itemIdx;
                    if (t.has("item")) {
                        itemIdx = t.getInt("item");
                    } else if (t.has("select")) {
                        itemIdx = -1;
                        for (int k = 0; k < combo.getItemCount(); k++) {
                            if (t.getString("select").equals(String.valueOf(combo.getItemAt(k)))) {
                                itemIdx = k;
                                break;
                            }
                        }
                        if (itemIdx < 0) {
                            throw new IllegalStateException("combo item " + t.getString("select") + " not found");
                        }
                    } else {
                        throw new IllegalArgumentException("combo step needs \"item\" or \"select\"");
                    }
                    driver.selectComboSmooth(combo, itemIdx);
                    detail = "#" + idx + " -> " + itemIdx;
                } else if (step.has("key")) {
                    action = "key";
                    detail = step.getString("key");
                    driver.pressKey(detail);
                } else if (step.has("zoom")) {
                    action = "zoom";
                    JSONObject z = step.getJSONObject("zoom");
                    double level;
                    double cx;
                    double cy;
                    if (z.has("rect")) {
                        JSONArray a = z.getJSONArray("rect");
                        Rectangle r = new Rectangle(a.getInt(0), a.getInt(1), a.getInt(2), a.getInt(3));
                        level = captureWidth / (double) r.width;
                        cx = r.getCenterX();
                        cy = r.getCenterY();
                        detail = "rect " + r;
                    } else {
                        Component target;
                        if (z.has("button")) {
                            detail = "button " + z.getString("button");
                            target = driver.findButtonByText(driver.currentInteractionRoot(jframe), z.getString("button"));
                        } else if (z.has("label")) {
                            detail = "label " + z.getString("label");
                            target = driver.findFieldByLabel(driver.currentInteractionRoot(jframe), z.getString("label"));
                        } else {
                            throw new IllegalArgumentException("zoom step needs \"button\", \"label\", or \"rect\"");
                        }
                        if (target == null) {
                            throw new IllegalStateException("zoom target not found: " + detail);
                        }
                        level = z.optDouble("level", 2.0);
                        Point rawCenter = driver.centerOf(target);
                        // Clamp so the target stays reachable as a crop center at this zoom
                        // level - i.e. cx*level +/- captureWidth/2 never runs off either edge
                        // of the scaled frame (see buildAxisExpr: cropX's hold value is
                        // cx*level - captureWidth/2).
                        double marginX = captureWidth / (2.0 * level);
                        double marginY = captureHeight / (2.0 * level);
                        cx = clampD(rawCenter.x, marginX, captureWidth - marginX);
                        cy = clampD(rawCenter.y, marginY, captureHeight - marginY);
                    }
                    int hold = step.has("wait") ? step.getInt("wait") : DEFAULT_ZOOM_HOLD_MS;
                    double t0 = (System.currentTimeMillis() - recordingEpochMs) / 1000.0;
                    ZoomEvent ev = new ZoomEvent();
                    ev.t0 = t0;
                    ev.t1 = t0 + ZOOM_EASE_MS / 1000.0;
                    ev.t2 = ev.t1 + hold / 1000.0;
                    ev.t3 = ev.t2 + ZOOM_EASE_MS / 1000.0;
                    ev.cx = cx;
                    ev.cy = cy;
                    ev.level = level;
                    zoomEvents.add(ev);
                    // Hold the recording still for the entire zoom window (ease-in + hold +
                    // ease-out) since the crop is applied afterward against these exact
                    // timestamps - nothing on screen should change while "zoomed in".
                    driver.pause(2 * ZOOM_EASE_MS + hold);
                } else if (step.has("assert")) {
                    action = "assert";
                    JSONObject a = step.getJSONObject("assert");
                    if (a.has("label")) {
                        String label = a.getString("label");
                        Component field = driver.findValueByLabel(driver.currentInteractionRoot(jframe), label);
                        if (field == null) {
                            throw new IllegalStateException("assert target not found: " + label);
                        }
                        String actual = driver.textOf(field);
                        if (actual == null) {
                            throw new IllegalStateException("assert target has no readable text: " + label);
                        }
                        String actualTrimmed = actual.trim();
                        if (a.has("equals")) {
                            String expected = a.getString("equals");
                            detail = label + " equals \"" + expected + "\" (actual: \"" + actualTrimmed + "\")";
                            if (!actualTrimmed.equals(expected)) {
                                throw new IllegalStateException("assertion failed: " + detail);
                            }
                        } else if (a.has("contains")) {
                            String expected = a.getString("contains");
                            detail = label + " contains \"" + expected + "\" (actual: \"" + actualTrimmed + "\")";
                            if (!actualTrimmed.contains(expected)) {
                                throw new IllegalStateException("assertion failed: " + detail);
                            }
                        } else if (a.has("notBlank")) {
                            detail = label + " notBlank (actual: \"" + actualTrimmed + "\")";
                            if (actualTrimmed.isEmpty() || actualTrimmed.equals("-")) {
                                throw new IllegalStateException("assertion failed: " + detail);
                            }
                        } else {
                            throw new IllegalArgumentException("assert step (\"label\" form) needs \"equals\", \"contains\", or \"notBlank\"");
                        }
                    } else if (a.has("text")) {
                        // For self-describing JLabels with no separate caption+value pair
                        // (e.g. a standalone warning banner) - "label" mode can't target
                        // these, since it looks for something *next to* a matching label,
                        // not the label itself. This just asks "does any currently-showing
                        // JLabel contain this text" instead.
                        String needle = a.getString("text");
                        boolean expectVisible = a.optBoolean("visible", true);
                        boolean found = driver.anyShowingLabelContains(driver.currentInteractionRoot(jframe), needle);
                        detail = "label containing \"" + needle + "\" " + (expectVisible ? "visible" : "not visible")
                                + " (actual: " + (found ? "visible" : "not visible") + ")";
                        if (found != expectVisible) {
                            throw new IllegalStateException("assertion failed: " + detail);
                        }
                    } else {
                        throw new IllegalArgumentException("assert step needs \"label\" or \"text\"");
                    }
                } else if (step.has("wait")) {
                    action = "wait";
                    detail = String.valueOf(step.getInt("wait"));
                } else if (step.has("caption") && !step.has("screenshot")) {
                    action = "caption";
                    detail = step.getString("caption");
                } else if (step.has("screenshot")) {
                    action = "screenshot";
                    detail = step.getString("screenshot");
                } else {
                    throw new IllegalArgumentException("step has no recognized action: " + step);
                }
            } catch (Exception ex) {
                failures[0]++;
                String errShot = "error-" + i + ".png";
                try {
                    screenshots.capture(new File(outDir, errShot));
                } catch (Exception ignored) {
                }
                manifest.put(manifestEntry(seq[0]++, "ERROR:" + action, detail + " (" + ex.getMessage() + ")", errShot, startMs));
                System.err.println("demo step " + i + " failed: " + ex.getMessage());
                continue;
            }

            driver.robot.waitForIdle();
            // "zoom" already paused for its whole ease/hold/ease window above; every
            // other action gets a settle pause so a human can follow what just
            // happened ("wait" overrides the default when present on the same step).
            if (!action.equals("zoom")) {
                driver.pause(step.has("wait") ? step.getInt("wait") : DEFAULT_POST_ACTION_WAIT_MS);
            }

            String shot = null;
            if (step.has("screenshot") && !action.equals("screenshot")) {
                shot = step.getString("screenshot");
                screenshots.capture(new File(outDir, shot));
            } else if (action.equals("screenshot")) {
                shot = detail;
                screenshots.capture(new File(outDir, shot));
            }
            if (step.has("caption") && !action.equals("caption")) {
                System.out.println("  (" + step.getString("caption") + ")");
            }

            manifest.put(manifestEntry(seq[0]++, action, detail, shot, startMs));
        }

        try (PrintWriter w = new PrintWriter(new FileWriter(new File(outDir, "manifest.json")))) {
            w.write(manifest.toString(2));
        }

        if (!zoomEvents.isEmpty()) {
            String filter = buildZoomFilter(zoomEvents, captureWidth, captureHeight);
            try (PrintWriter w = new PrintWriter(new FileWriter(new File(outDir, "zoom-filter.txt")))) {
                w.write(filter);
            }
        }

        if (failures[0] == 0) {
            System.out.println("Demo complete: " + seq[0] + " steps logged to " + new File(outDir, "manifest.json") + " - PASS");
        } else {
            System.out.println("Demo complete: " + seq[0] + " steps logged to " + new File(outDir, "manifest.json")
                    + " - FAIL (" + failures[0] + " step(s) failed)");
        }
        return failures[0] == 0;
    }

    private static double clampD(double v, double min, double max) {
        return Math.max(min, Math.min(v, Math.max(min, max)));
    }

    private static JSONObject manifestEntry(int seq, String action, String detail, String screenshot, long startMs) {
        JSONObject o = new JSONObject();
        o.put("seq", seq);
        o.put("action", action);
        o.put("detail", detail == null ? JSONObject.NULL : detail);
        o.put("screenshot", screenshot == null ? JSONObject.NULL : screenshot);
        o.put("tMs", System.currentTimeMillis() - startMs);
        return o;
    }

    // ------------------------------------------------------------------
    // Zoom: builds an ffmpeg filter graph string covering every recorded
    // zoom event, using per-frame time expressions rather than ffmpeg
    // "commands" - see the class doc for why this runs as a separate
    // post-processing pass instead of live during capture.
    //
    // crop can't vary its own OUTPUT width/height per frame in practice
    // (confirmed empirically - a t-varying w/h silently has no effect,
    // presumably because downstream needs a constant frame size), only its
    // x/y position within a fixed-size output. So the "growing" part of the
    // zoom has to happen in `scale` instead, which does support this
    // (eval=frame): scale the whole frame up by a time-varying factor, then
    // crop a constant-size (iw x ih) window that pans to stay centered on
    // the target as the frame grows underneath it - equivalent to zooming
    // in on that point without ever changing the stream's frame dimensions.
    // ------------------------------------------------------------------

    private static String buildZoomFilter(List<ZoomEvent> events, int iw, int ih) {
        String scaleW = buildAxisExpr(events, iw, ih, "scaleW");
        String scaleH = buildAxisExpr(events, iw, ih, "scaleH");
        String cropX = buildAxisExpr(events, iw, ih, "cropX");
        String cropY = buildAxisExpr(events, iw, ih, "cropY");
        return "scale=w='" + scaleW + "':h='" + scaleH + "':eval=frame,"
                + "crop=" + iw + ":" + ih + ":x='" + cropX + "':y='" + cropY + "'";
    }

    private static String buildAxisExpr(List<ZoomEvent> events, int iw, int ih, String axis) {
        String expr = switch (axis) {
            case "scaleW" -> String.valueOf(iw);
            case "scaleH" -> String.valueOf(ih);
            default -> "0"; // cropX, cropY: no zoom active means no pan offset
        };
        for (ZoomEvent ev : events) {
            double from;
            double target;
            switch (axis) {
                case "scaleW":
                    from = iw;
                    target = iw * ev.level;
                    break;
                case "scaleH":
                    from = ih;
                    target = ih * ev.level;
                    break;
                case "cropX":
                    from = 0;
                    target = ev.cx * ev.level - iw / 2.0;
                    break;
                default: // cropY
                    from = 0;
                    target = ev.cy * ev.level - ih / 2.0;
                    break;
            }
            String easeIn = lerp(from, target, ev.t0, ev.t1);
            String hold = fmt(target);
            String easeOut = lerp(target, from, ev.t2, ev.t3);
            expr = "if(between(t," + fmt(ev.t0) + "," + fmt(ev.t1) + ")," + easeIn + ","
                    + "if(between(t," + fmt(ev.t1) + "," + fmt(ev.t2) + ")," + hold + ","
                    + "if(between(t," + fmt(ev.t2) + "," + fmt(ev.t3) + ")," + easeOut + "," + expr + ")))";
        }
        return expr;
    }

    private static String lerp(double from, double to, double t0, double t1) {
        return "(" + fmt(from) + "+(" + fmt(to) + "-" + fmt(from) + ")*(t-" + fmt(t0) + ")/" + fmt(t1 - t0) + ")";
    }

    private static String fmt(double d) {
        return String.format(Locale.ROOT, "%.3f", d);
    }
}

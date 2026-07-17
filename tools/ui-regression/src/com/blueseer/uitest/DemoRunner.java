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
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

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
 * Script shape:
 *   { "steps": [ { <one action key>, "wait"?: ms, "caption"?: "...", "screenshot"?: "name.png" }, ... ] }
 *
 * Action keys (exactly one per step, except a step can be just "wait" or
 * just "caption" on their own):
 *   "menu": "Top>Sub>Leaf"                        - menu bar navigation
 *   "click": "Button Text"                        - click a JButton by its text
 *   "type": {"label": "Key", "text": "..."}       - type into the field whose nearest
 *            or {"index": 0, "text": "..."}         preceding JLabel matches (or nth JTextField)
 *   "tab": {"pane": 0, "title": "..."}            - select a JTabbedPane tab, by title or index
 *           or {"pane": 0, "index": 1}
 *   "check": 0 / "radio": 0                       - click the nth checkbox/radio button found
 *   "combo": {"index": 0, "item": 1}              - set a combo box's selection, by item index
 *             or {"index": 0, "select": "text"}     or by matching its string value
 *   "key": "ENTER"                                - press a key (java.awt.event.KeyEvent.VK_* name)
 *   "wait": 1000                                  - pause only
 *   "caption": "Creating a new item"               - no-op, just logged to manifest.json (and
 *                                                    console) for narration/subtitle authoring later
 *
 * "wait" and "caption" can also ride along on any other step: "wait" becomes
 * that step's post-action pause (default 400ms if omitted), "caption" is
 * logged alongside it.
 */
final class DemoRunner {

    private DemoRunner() {
    }

    static void run(File scriptFile, File outDir) throws Exception {
        outDir.mkdirs();
        JSONObject root;
        try (FileReader reader = new FileReader(scriptFile)) {
            root = new JSONObject(new JSONTokener(reader));
        }
        JSONArray steps = root.getJSONArray("steps");

        SwingAppDriver driver = new SwingAppDriver();
        ScreenshotUtil screenshots = new ScreenshotUtil();
        long startMs = System.currentTimeMillis();
        JSONArray manifest = new JSONArray();
        int[] seq = {0};

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
                    if (!driver.navigateMenuPath(jframe.getJMenuBar(), path)) {
                        throw new IllegalStateException("menu path not found: " + detail);
                    }
                } else if (step.has("click")) {
                    action = "click";
                    detail = step.getString("click");
                    javax.swing.JButton b = driver.findButtonByText(jframe.getContentPane(), detail);
                    if (b == null) {
                        throw new IllegalStateException("button not found: " + detail);
                    }
                    driver.robot.click(b);
                } else if (step.has("type")) {
                    action = "type";
                    JSONObject t = step.getJSONObject("type");
                    String text = t.getString("text");
                    Component field;
                    if (t.has("label")) {
                        field = driver.findFieldByLabel(jframe.getContentPane(), t.getString("label"));
                        detail = t.getString("label") + "=" + text;
                    } else if (t.has("index")) {
                        List<Component> scan = new ArrayList<>();
                        driver.collectAll(jframe.getContentPane(), JTextField.class, scan);
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
                    List<Component> scan = driver.findTargets(jframe.getContentPane());
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
                    driver.clickTab(pane, tabIdx);
                } else if (step.has("check") || step.has("radio")) {
                    boolean isCheck = step.has("check");
                    action = isCheck ? "check" : "radio";
                    int idx = step.getInt(action);
                    List<Component> scan = driver.findTargets(jframe.getContentPane());
                    Component c = isCheck ? driver.nthOfType(scan, JCheckBox.class, idx)
                                           : driver.nthOfType(scan, JRadioButton.class, idx);
                    if (c == null) {
                        throw new IllegalStateException(action + " #" + idx + " not found");
                    }
                    driver.robot.click(c);
                    detail = "#" + idx;
                } else if (step.has("combo")) {
                    action = "combo";
                    JSONObject t = step.getJSONObject("combo");
                    int idx = t.getInt("index");
                    List<Component> scan = driver.findTargets(jframe.getContentPane());
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
                    driver.setComboIndex(combo, itemIdx);
                    detail = "#" + idx + " -> " + itemIdx;
                } else if (step.has("key")) {
                    action = "key";
                    detail = step.getString("key");
                    driver.pressKey(detail);
                } else if (step.has("wait")) {
                    action = "wait";
                    detail = String.valueOf(step.getInt("wait"));
                } else if (step.has("caption")) {
                    action = "caption";
                    detail = step.getString("caption");
                } else {
                    throw new IllegalArgumentException("step has no recognized action: " + step);
                }
            } catch (Exception ex) {
                manifest.put(manifestEntry(seq[0]++, "ERROR:" + action, detail + " (" + ex.getMessage() + ")", null, startMs));
                System.err.println("demo step " + i + " failed: " + ex.getMessage());
                continue;
            }

            driver.robot.waitForIdle();
            // "wait" doubles as this step's post-action pause when combined with
            // another action key; a bare {"wait": N} step just is that pause.
            driver.pause(step.has("wait") ? step.getInt("wait") : 400);

            String shot = null;
            if (step.has("screenshot")) {
                shot = step.getString("screenshot");
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
        System.out.println("Demo complete: " + seq[0] + " steps logged to " + new File(outDir, "manifest.json"));
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
}

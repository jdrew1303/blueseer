package com.blueseer.uitest;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Drives the live BlueSeer Swing UI through its real component tree (menus,
 * tabs, checkboxes, combo boxes) instead of blind screen coordinates, so the
 * same recorded action sequence can be replayed against a second build (e.g.
 * a different bsmf.jar) and the resulting screenshots diffed pairwise.
 *
 * Usage:
 *   record <bsCfgWorkingDir> <outDir> [--filter text] [--max-leaves N] [--seed N]
 *   replay <bsCfgWorkingDir> <actionLog> <outDir>
 *   demo   <bsCfgWorkingDir> <script.json> <outDir>   (see DemoRunner)
 *
 * <bsCfgWorkingDir> must contain bs.cfg and be run with a classpath that has
 * the bsmf.jar variant under test on it (see test/ui-regression/run.sh).
 */
public class UiRegressionRunner {

    // Never auto-click anything whose text/tooltip matches one of these -
    // this walks a real (if disposable) test database, so anything
    // destructive, external, or exit-like is off limits.
    private static final String[] BLOCKLIST = {
        "delete", "exit", "logout", "log out", "print", "remove", "purge",
        "drop", "format", "export", "email", "e-mail", "send", "post",
        "ftp", "upload", "backup", "restore", "shutdown", "kill", "close"
    };

    private final SwingAppDriver driver = new SwingAppDriver();
    private final ScreenshotUtil screenshots = new ScreenshotUtil();
    private final Random random;
    private final File outDir;
    private int seq = 0;
    private PrintWriter log;

    private UiRegressionRunner(File outDir, long seed) {
        this.outDir = outDir;
        this.random = new Random(seed);
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: record <workDir> <outDir> [--filter text] [--max-leaves N] [--seed N]");
            System.err.println("       replay <workDir> <actionLog> <outDir>");
            System.err.println("       demo <workDir> <script.json> <outDir>");
            System.exit(2);
        }

        String mode = args[0];
        File workDir = new File(args[1]);
        // System.setProperty("user.dir", ...) does NOT change how java.io.File
        // resolves relative paths (it's fixed once at JVM startup), so bs.cfg
        // and friends are only found if this JVM's actual working directory
        // is already workDir - the launching shell must `cd` there first.
        File cwdMarker = new File("bs.cfg");
        if (!cwdMarker.isFile()) {
            System.err.println("bs.cfg not found in the current directory - launch this JVM with its "
                    + "working directory set to " + workDir.getAbsolutePath() + " (cd there first, don't "
                    + "rely on the workDir argument to do it)");
            System.exit(2);
        }

        if (mode.equals("record")) {
            File outDir = new File(args[2]);
            String filter = null;
            int maxLeaves = 40;
            long seed = 42L;
            for (int i = 3; i < args.length; i++) {
                if (args[i].equals("--filter")) {
                    filter = args[++i];
                } else if (args[i].equals("--max-leaves")) {
                    maxLeaves = Integer.parseInt(args[++i]);
                } else if (args[i].equals("--seed")) {
                    seed = Long.parseLong(args[++i]);
                }
            }
            UiRegressionRunner runner = new UiRegressionRunner(outDir, seed);
            runner.record(filter, maxLeaves);
        } else if (mode.equals("replay")) {
            File actionLog = new File(args[2]);
            File outDir = new File(args[3]);
            UiRegressionRunner runner = new UiRegressionRunner(outDir, 42L);
            runner.replay(actionLog);
        } else if (mode.equals("demo")) {
            File script = new File(args[2]);
            File outDir = new File(args[3]);
            DemoRunner.run(script, outDir);
        } else {
            System.err.println("unknown mode: " + mode);
            System.exit(2);
        }
        System.exit(0);
    }

    // ------------------------------------------------------------------
    // Record
    // ------------------------------------------------------------------

    private void record(String filter, int maxLeaves) throws Exception {
        outDir.mkdirs();
        log = new PrintWriter(new FileWriter(new File(outDir, "actions.log")));
        try {
            Frame frame = driver.launchAndLogin();
            screenshots.capture(new File(outDir, "post-login.png"));
            JMenuBar menuBar = ((javax.swing.JFrame) frame).getJMenuBar();

            List<List<String>> allLeafPaths = new ArrayList<>();
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                JMenu top = menuBar.getMenu(i);
                if (top == null) {
                    continue;
                }
                List<String> path = new ArrayList<>();
                path.add(top.getText());
                driver.walkMenu(top, path, allLeafPaths);
            }
            // Filter matches anywhere in the ">"-joined path (not just the
            // top-level menu), so e.g. "Item Maintenance" can target one
            // specific screen instead of a whole top-level menu's leaves.
            List<List<String>> leafPaths = new ArrayList<>();
            for (List<String> path : allLeafPaths) {
                if (filter == null || String.join(">", path).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))) {
                    leafPaths.add(path);
                }
            }

            System.out.println("Discovered " + leafPaths.size() + " menu leaves"
                    + (leafPaths.size() > maxLeaves ? "; capping at " + maxLeaves : ""));

            int visited = 0;
            for (List<String> path : leafPaths) {
                if (visited >= maxLeaves) {
                    break;
                }
                String joined = String.join(">", path);
                if (isBlocked(path.get(path.size() - 1))) {
                    writeLine("SKIP", joined, null, "blocklisted");
                    continue;
                }
                boolean ok = driver.navigateMenuPath(menuBar, path);
                if (!ok) {
                    writeLine("MISMATCH", joined, null, "menu path not found");
                    continue;
                }
                driver.robot.waitForIdle();
                driver.pause(300);
                writeLine("MENU", joined, screenshot(), joined);
                exploreVisibleArea(((javax.swing.JFrame) frame).getContentPane(), joined);
                visited++;
            }
        } finally {
            log.close();
        }
        System.out.println("Recorded " + seq + " actions to " + new File(outDir, "actions.log"));
    }

    // ------------------------------------------------------------------
    // Replay
    // ------------------------------------------------------------------

    private void replay(File actionLog) throws Exception {
        outDir.mkdirs();
        log = new PrintWriter(new FileWriter(new File(outDir, "replay.log")));
        try {
            Frame frame = driver.launchAndLogin();
            screenshots.capture(new File(outDir, "post-login.png"));
            JMenuBar menuBar = ((javax.swing.JFrame) frame).getJMenuBar();
            Container contentPane = ((javax.swing.JFrame) frame).getContentPane();

            try (BufferedReader reader = new BufferedReader(new FileReader(actionLog))) {
                String line;
                List<Component> currentScan = null;
                while ((line = reader.readLine()) != null) {
                    String[] parts = line.split("\t", -1);
                    if (parts.length < 3) {
                        continue;
                    }
                    String type = parts[1];
                    String key = parts[2];

                    switch (type) {
                        case "MENU": {
                            List<String> path = new ArrayList<>(List.of(key.split(">")));
                            boolean ok = driver.navigateMenuPath(menuBar, path);
                            driver.robot.waitForIdle();
                            driver.pause(300);
                            if (!ok) {
                                writeLine("MISMATCH", key, screenshot(), "menu path not found on replay");
                            } else {
                                writeLine("MENU", key, screenshot(), key);
                            }
                            currentScan = driver.findTargets(contentPane);
                            break;
                        }
                        case "TAB": {
                            String[] kv = key.split(":");
                            int paneIdx = Integer.parseInt(kv[0]);
                            int tabIdx = Integer.parseInt(kv[1]);
                            JTabbedPane pane = driver.nthOfType(currentScan, JTabbedPane.class, paneIdx);
                            if (pane == null || tabIdx >= pane.getTabCount()) {
                                writeLine("MISMATCH", key, screenshot(), "tab not found on replay");
                            } else {
                                driver.clickTab(pane, tabIdx);
                                driver.robot.waitForIdle();
                                driver.pause(200);
                                writeLine("TAB", key, screenshot(), "replayed");
                            }
                            currentScan = driver.findTargets(contentPane);
                            break;
                        }
                        case "CHECK":
                        case "RADIO": {
                            int idx = Integer.parseInt(key);
                            Class<? extends Component> cls = type.equals("CHECK") ? JCheckBox.class : JRadioButton.class;
                            Component c = driver.nthOfType(currentScan, cls, idx);
                            if (c == null) {
                                writeLine("MISMATCH", key, screenshot(), type + " not found on replay");
                            } else {
                                driver.robot.click(c);
                                driver.robot.waitForIdle();
                                driver.pause(150);
                                writeLine(type, key, screenshot(), "replayed");
                            }
                            break;
                        }
                        case "COMBO": {
                            String[] kv = key.split(":");
                            int idx = Integer.parseInt(kv[0]);
                            int itemIdx = Integer.parseInt(kv[1]);
                            JComboBox<?> combo = driver.nthOfType(currentScan, JComboBox.class, idx);
                            if (combo == null || itemIdx >= combo.getItemCount()) {
                                writeLine("MISMATCH", key, screenshot(), "combo not found on replay");
                            } else {
                                driver.setComboIndex(combo, itemIdx);
                                driver.pause(150);
                                writeLine("COMBO", key, screenshot(), "replayed");
                            }
                            break;
                        }
                        default:
                            // SKIP / MISMATCH lines from the recording are not replayed
                            break;
                    }
                }
            }
        } finally {
            log.close();
        }
        System.out.println("Replayed to " + new File(outDir, "replay.log"));
    }

    // ------------------------------------------------------------------
    // In-screen exploration: tabs, checkboxes, radios, combos
    // ------------------------------------------------------------------

    private void exploreVisibleArea(Container root, String menuPathKey) {
        scanAndInteract(root);

        List<Component> targets = driver.findTargets(root);
        List<JTabbedPane> panes = new ArrayList<>();
        for (Component c : targets) {
            if (c instanceof JTabbedPane) {
                panes.add((JTabbedPane) c);
            }
        }
        for (int paneIdx = 0; paneIdx < panes.size(); paneIdx++) {
            JTabbedPane pane = panes.get(paneIdx);
            for (int tabIdx = 0; tabIdx < pane.getTabCount(); tabIdx++) {
                if (isBlocked(String.valueOf(pane.getTitleAt(tabIdx)))) {
                    continue;
                }
                driver.clickTab(pane, tabIdx);
                driver.robot.waitForIdle();
                driver.pause(200);
                writeLine("TAB", paneIdx + ":" + tabIdx, screenshot(), String.valueOf(pane.getTitleAt(tabIdx)));
                scanAndInteract(root);
            }
        }
    }

    /** Toggles checkboxes/radios and picks a combo index, screenshotting + logging each. */
    private void scanAndInteract(Container root) {
        List<Component> targets = driver.findTargets(root);
        int checkIdx = -1, radioIdx = -1, comboIdx = -1;
        for (Component c : targets) {
            if (c instanceof JCheckBox) {
                checkIdx++;
                JCheckBox cb = (JCheckBox) c;
                if (isBlocked(driver.safeText(cb))) {
                    continue;
                }
                driver.robot.click(cb);
                driver.robot.waitForIdle();
                driver.pause(100);
                writeLine("CHECK", String.valueOf(checkIdx), screenshot(), driver.safeText(cb));
            } else if (c instanceof JRadioButton) {
                radioIdx++;
                JRadioButton rb = (JRadioButton) c;
                if (isBlocked(driver.safeText(rb))) {
                    continue;
                }
                driver.robot.click(rb);
                driver.robot.waitForIdle();
                driver.pause(100);
                writeLine("RADIO", String.valueOf(radioIdx), screenshot(), driver.safeText(rb));
            } else if (c instanceof JComboBox) {
                comboIdx++;
                JComboBox<?> combo = (JComboBox<?>) c;
                int count = combo.getItemCount();
                if (count <= 0) {
                    continue;
                }
                int itemIdx = random.nextInt(count);
                driver.setComboIndex(combo, itemIdx);
                driver.pause(100);
                writeLine("COMBO", comboIdx + ":" + itemIdx, screenshot(), String.valueOf(combo.getSelectedItem()));
            }
        }
    }

    private boolean isBlocked(String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String kw : BLOCKLIST) {
            if (lower.contains(kw)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Logging / screenshots
    // ------------------------------------------------------------------

    private String screenshot() {
        String name = String.format("%04d.png", seq);
        screenshots.capture(new File(outDir, name));
        return name;
    }

    private void writeLine(String type, String key, String screenshotName, String label) {
        seq++;
        log.printf("%d\t%s\t%s\t%s\t%s%n", seq, type, key, screenshotName == null ? "" : screenshotName,
                label == null ? "" : label.replace('\t', ' ').replace('\n', ' '));
        log.flush();
    }
}

package com.blueseer.uitest;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
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

    private final Robot robot = BasicRobot.robotWithCurrentAwtHierarchy();
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
            Frame frame = launchAndLogin();
            JMenuBar menuBar = ((javax.swing.JFrame) frame).getJMenuBar();

            List<List<String>> allLeafPaths = new ArrayList<>();
            for (int i = 0; i < menuBar.getMenuCount(); i++) {
                JMenu top = menuBar.getMenu(i);
                if (top == null) {
                    continue;
                }
                List<String> path = new ArrayList<>();
                path.add(top.getText());
                walkMenu(top, path, allLeafPaths);
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
                boolean ok = navigateMenuPath(menuBar, path);
                if (!ok) {
                    writeLine("MISMATCH", joined, null, "menu path not found");
                    continue;
                }
                robot.waitForIdle();
                pause(300);
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
            Frame frame = launchAndLogin();
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
                            boolean ok = navigateMenuPath(menuBar, path);
                            robot.waitForIdle();
                            pause(300);
                            if (!ok) {
                                writeLine("MISMATCH", key, screenshot(), "menu path not found on replay");
                            } else {
                                writeLine("MENU", key, screenshot(), key);
                            }
                            currentScan = findTargets(contentPane);
                            break;
                        }
                        case "TAB": {
                            String[] kv = key.split(":");
                            int paneIdx = Integer.parseInt(kv[0]);
                            int tabIdx = Integer.parseInt(kv[1]);
                            JTabbedPane pane = nthOfType(currentScan, JTabbedPane.class, paneIdx);
                            if (pane == null || tabIdx >= pane.getTabCount()) {
                                writeLine("MISMATCH", key, screenshot(), "tab not found on replay");
                            } else {
                                clickTab(pane, tabIdx);
                                robot.waitForIdle();
                                pause(200);
                                writeLine("TAB", key, screenshot(), "replayed");
                            }
                            currentScan = findTargets(contentPane);
                            break;
                        }
                        case "CHECK":
                        case "RADIO": {
                            int idx = Integer.parseInt(key);
                            Class<? extends Component> cls = type.equals("CHECK") ? JCheckBox.class : JRadioButton.class;
                            Component c = nthOfType(currentScan, cls, idx);
                            if (c == null) {
                                writeLine("MISMATCH", key, screenshot(), type + " not found on replay");
                            } else {
                                robot.click(c);
                                robot.waitForIdle();
                                pause(150);
                                writeLine(type, key, screenshot(), "replayed");
                            }
                            break;
                        }
                        case "COMBO": {
                            String[] kv = key.split(":");
                            int idx = Integer.parseInt(kv[0]);
                            int itemIdx = Integer.parseInt(kv[1]);
                            JComboBox<?> combo = nthOfType(currentScan, JComboBox.class, idx);
                            if (combo == null || itemIdx >= combo.getItemCount()) {
                                writeLine("MISMATCH", key, screenshot(), "combo not found on replay");
                            } else {
                                setComboIndex(combo, itemIdx);
                                pause(150);
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
    // Shared: launch + login
    // ------------------------------------------------------------------

    private Frame launchAndLogin() throws Exception {
        com.blueseer.utl.mf.main(new String[0]);

        Frame frame = waitForFrame();
        robot.waitForIdle();

        // Login screen: first JTextField = username, first JPasswordField = password.
        // JPasswordField extends JTextField, so search for a plain (non-password)
        // text field explicitly rather than reusing firstOfType(JTextField.class).
        JTextField username = null;
        List<Component> textFields = new ArrayList<>();
        collectAll((Container) frame, JTextField.class, textFields);
        for (Component c : textFields) {
            if (!(c instanceof JPasswordField)) {
                username = (JTextField) c;
                break;
            }
        }
        JPasswordField password = firstOfType((Container) frame, JPasswordField.class);
        if (username != null && password != null) {
            robot.focusAndWaitForFocusGain(username);
            robot.enterText("admin");
            robot.focusAndWaitForFocusGain(password);
            robot.enterText("admin");
            javax.swing.JButton loginButton = findButtonByText((Container) frame, "Login");
            if (loginButton != null) {
                robot.click(loginButton);
            }
            robot.waitForIdle();
            pause(3000);
        }
        screenshots.capture(new File(outDir, "post-login.png"));
        return frame;
    }

    private Frame waitForFrame() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            for (Window w : Window.getWindows()) {
                if (w instanceof javax.swing.JFrame && w.isShowing()) {
                    return (Frame) w;
                }
            }
            Thread.sleep(200);
        }
        throw new IllegalStateException("app window never appeared");
    }

    // ------------------------------------------------------------------
    // Menu discovery / navigation
    // ------------------------------------------------------------------

    private void walkMenu(JMenu menu, List<String> pathSoFar, List<List<String>> leaves) {
        for (int i = 0; i < menu.getItemCount(); i++) {
            JMenuItem item = menu.getItem(i);
            if (item == null) {
                continue; // separator
            }
            List<String> path = new ArrayList<>(pathSoFar);
            path.add(item.getText());
            if (item instanceof JMenu) {
                walkMenu((JMenu) item, path, leaves);
            } else {
                leaves.add(path);
            }
        }
    }

    private boolean navigateMenuPath(JMenuBar menuBar, List<String> path) {
        JMenu top = null;
        for (int i = 0; i < menuBar.getMenuCount(); i++) {
            JMenu m = menuBar.getMenu(i);
            if (m != null && m.getText().equals(path.get(0))) {
                top = m;
                break;
            }
        }
        if (top == null) {
            return false;
        }
        robot.click(top);
        robot.waitForIdle();
        pause(150);

        JMenu currentMenu = top;
        for (int i = 1; i < path.size(); i++) {
            String text = path.get(i);
            JMenuItem found = null;
            for (int j = 0; j < currentMenu.getItemCount(); j++) {
                JMenuItem item = currentMenu.getItem(j);
                if (item != null && text.equals(item.getText())) {
                    found = item;
                    break;
                }
            }
            if (found == null) {
                return false;
            }
            robot.click(found);
            robot.waitForIdle();
            pause(150);
            if (found instanceof JMenu) {
                currentMenu = (JMenu) found;
            }
        }
        return true;
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
    // In-screen exploration: tabs, checkboxes, radios, combos
    // ------------------------------------------------------------------

    private void exploreVisibleArea(Container root, String menuPathKey) {
        scanAndInteract(root);

        List<Component> targets = findTargets(root);
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
                clickTab(pane, tabIdx);
                robot.waitForIdle();
                pause(200);
                writeLine("TAB", paneIdx + ":" + tabIdx, screenshot(), String.valueOf(pane.getTitleAt(tabIdx)));
                scanAndInteract(root);
            }
        }
    }

    /** Toggles checkboxes/radios and picks a combo index, screenshotting + logging each. */
    private void scanAndInteract(Container root) {
        List<Component> targets = findTargets(root);
        int checkIdx = -1, radioIdx = -1, comboIdx = -1;
        for (Component c : targets) {
            if (c instanceof JCheckBox) {
                checkIdx++;
                JCheckBox cb = (JCheckBox) c;
                if (isBlocked(safeText(cb))) {
                    continue;
                }
                robot.click(cb);
                robot.waitForIdle();
                pause(100);
                writeLine("CHECK", String.valueOf(checkIdx), screenshot(), safeText(cb));
            } else if (c instanceof JRadioButton) {
                radioIdx++;
                JRadioButton rb = (JRadioButton) c;
                if (isBlocked(safeText(rb))) {
                    continue;
                }
                robot.click(rb);
                robot.waitForIdle();
                pause(100);
                writeLine("RADIO", String.valueOf(radioIdx), screenshot(), safeText(rb));
            } else if (c instanceof JComboBox) {
                comboIdx++;
                JComboBox<?> combo = (JComboBox<?>) c;
                int count = combo.getItemCount();
                if (count <= 0) {
                    continue;
                }
                int itemIdx = random.nextInt(count);
                setComboIndex(combo, itemIdx);
                pause(100);
                writeLine("COMBO", comboIdx + ":" + itemIdx, screenshot(), String.valueOf(combo.getSelectedItem()));
            }
        }
    }

    private void clickTab(JTabbedPane pane, int tabIdx) {
        SwingUtilities.invokeLater(() -> pane.setSelectedIndex(tabIdx));
        robot.waitForIdle();
    }

    private void setComboIndex(JComboBox<?> combo, int itemIdx) {
        SwingUtilities.invokeLater(() -> combo.setSelectedIndex(itemIdx));
        robot.waitForIdle();
    }

    private List<Component> findTargets(Container root) {
        List<Component> out = new ArrayList<>();
        collectTargets(root, out);
        return out;
    }

    private void collectTargets(Component c, List<Component> out) {
        if (!c.isShowing()) {
            return;
        }
        if (c instanceof JTabbedPane || c instanceof JCheckBox || c instanceof JRadioButton || c instanceof JComboBox) {
            out.add(c);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collectTargets(child, out);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <T> T nthOfType(List<Component> components, Class<T> type, int n) {
        if (components == null) {
            return null;
        }
        int count = -1;
        for (Component c : components) {
            if (type.isInstance(c)) {
                count++;
                if (count == n) {
                    return (T) c;
                }
            }
        }
        return null;
    }

    private <T extends Component> T firstOfType(Container root, Class<T> type) {
        List<Component> out = new ArrayList<>();
        collectAll(root, type, out);
        return out.isEmpty() ? null : type.cast(out.get(0));
    }

    private void collectAll(Component c, Class<?> type, List<Component> out) {
        if (type.isInstance(c)) {
            out.add(c);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collectAll(child, type, out);
            }
        }
    }

    private javax.swing.JButton findButtonByText(Container root, String text) {
        List<Component> out = new ArrayList<>();
        collectAll(root, javax.swing.JButton.class, out);
        for (Component c : out) {
            javax.swing.JButton b = (javax.swing.JButton) c;
            if (text.equals(b.getText())) {
                return b;
            }
        }
        return null;
    }

    private String safeText(javax.swing.AbstractButton b) {
        String t = b.getText();
        return t == null ? "" : t;
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

    private void pause(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }
}

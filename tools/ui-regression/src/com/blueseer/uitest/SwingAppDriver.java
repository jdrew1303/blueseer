package com.blueseer.uitest;

import org.assertj.swing.core.BasicRobot;
import org.assertj.swing.core.Robot;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPasswordField;
import javax.swing.MenuSelectionManager;
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.AWTException;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Window;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Drives the live BlueSeer Swing UI through its real component tree (menus,
 * tabs, buttons, checkboxes, combo boxes, text fields) via AssertJ-Swing
 * instead of blind screen coordinates. Shared by {@link UiRegressionRunner}
 * (record/replay for pixel-diff regression testing) and {@link DemoRunner}
 * (JSON-scripted demo-video capture) - both need the same "launch, log in,
 * find and click things" primitives, just driven differently.
 */
final class SwingAppDriver {

    final Robot robot = BasicRobot.robotWithCurrentAwtHierarchy();
    // A second, separate java.awt.Robot purely for cursor animation. AssertJ-Swing's
    // Robot jumps the pointer straight to a component and clicks - fine for the fast
    // regression walker, but a demo video needs the viewer to actually see the cursor
    // travel to whatever's about to be clicked (see smoothMoveAndClick).
    private final java.awt.Robot awtRobot = createAwtRobot();

    private static java.awt.Robot createAwtRobot() {
        try {
            return new java.awt.Robot();
        } catch (AWTException ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * Launches BlueSeer and logs in as admin/admin. Blocks until the main
     * frame is showing and the login has been submitted.
     */
    Frame launchAndLogin() throws Exception {
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
            smoothMoveAndClick(username);
            robot.focusAndWaitForFocusGain(username);
            robot.enterText("admin");
            smoothMoveAndClick(password);
            robot.focusAndWaitForFocusGain(password);
            robot.enterText("admin");
            javax.swing.JButton loginButton = findButtonByText((Container) frame, "Login");
            if (loginButton != null) {
                pause(300);
                smoothMoveAndClick(loginButton);
            }
            robot.waitForIdle();
            // A fixed pause here is a guess at how long post-login menu
            // construction (a DB round-trip building ~70 menu_tree rows into
            // the JMenuBar) takes, and it's occasionally not enough under
            // system load - confirmed by a real failure where every menu
            // step failed instantly because the JMenuBar was still empty 3s
            // after login. Poll for the actual condition instead of guessing
            // its duration.
            waitForMenuBarPopulated((JFrame) frame, 10000);
        }
        return frame;
    }

    private void waitForMenuBarPopulated(JFrame jframe, long timeoutMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            JMenuBar bar = jframe.getJMenuBar();
            if (bar != null && bar.getMenuCount() > 0) {
                return;
            }
            pause(100);
        }
    }

    /**
     * The container demo-script steps ("click", "type", "combo", "tab",
     * "check"/"radio") should actually search: the topmost currently-showing
     * {@link Window} other than the main frame if one exists (a modal
     * {@code JDialog} - including a plain {@code JOptionPane} confirm, which
     * is backed by one - or a wizard dialog like {@code RpnRequestDialog}),
     * else the main frame's own content pane. {@link Window#getWindows()}
     * returns windows in creation order, so the last showing non-main entry
     * is the most recently opened - the right choice even for a dialog
     * spawned from within another dialog (a confirm prompt raised from
     * inside a wizard), since that inner dialog is necessarily created after
     * its parent.
     *
     * <p>Without this, every step targeting dialog content silently resolves
     * against the main frame's tree instead and fails with a "not found"
     * error - a real, previously-undiagnosed limitation (dialogs are common
     * throughout this app: confirmations, multi-step wizards, add/edit
     * modals), not a hypothetical edge case.
     */
    Container currentInteractionRoot(JFrame mainFrame) {
        Window topmost = null;
        for (Window w : Window.getWindows()) {
            if (w == mainFrame || !w.isShowing()) {
                continue;
            }
            topmost = w;
        }
        return topmost != null ? topmost : mainFrame.getContentPane();
    }

    Frame waitForFrame() throws InterruptedException {
        long deadline = System.currentTimeMillis() + 15000;
        while (System.currentTimeMillis() < deadline) {
            for (Window w : Window.getWindows()) {
                if (w instanceof JFrame && w.isShowing()) {
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

    void walkMenu(JMenu menu, List<String> pathSoFar, List<List<String>> leaves) {
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

    boolean navigateMenuPath(JMenuBar menuBar, List<String> path) {
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

    // ------------------------------------------------------------------
    // Visible cursor movement (demo mode only - see DemoRunner). The fast
    // record/replay walker keeps using AssertJ-Swing's instant robot.click().
    // ------------------------------------------------------------------

    Point centerOf(Component c) {
        Point p = c.getLocationOnScreen();
        return new Point(p.x + c.getWidth() / 2, p.y + c.getHeight() / 2);
    }

    private Point currentPointer() {
        PointerInfo info = MouseInfo.getPointerInfo();
        return info != null ? info.getLocation() : new Point(0, 0);
    }

    private void pressAndRelease() {
        pause(120);
        awtRobot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        pause(60);
        awtRobot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }

    /** Glides the real pointer to the component's center, then clicks it. */
    void smoothMoveAndClick(Component target) {
        smoothMoveTo(centerOf(target));
        pressAndRelease();
    }

    void smoothMoveTo(Point dest) {
        PointerInfo info = MouseInfo.getPointerInfo();
        Point cur = info != null ? info.getLocation() : dest;
        int steps = 30;
        for (int i = 1; i <= steps; i++) {
            double f = easeInOut((double) i / steps);
            awtRobot.mouseMove((int) Math.round(cur.x + (dest.x - cur.x) * f),
                    (int) Math.round(cur.y + (dest.y - cur.y) * f));
            pause(12);
        }
    }

    private static double easeInOut(double t) {
        return t < 0.5 ? 2 * t * t : 1 - Math.pow(-2 * t + 2, 2) / 2;
    }

    /** Demo-mode menu navigation: same as navigateMenuPath but with a visible cursor. */
    boolean navigateMenuPathSmooth(JMenuBar menuBar, List<String> path) {
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
        smoothMoveAndClick(top);
        pause(300);

        // Every JMenu we've opened along the way (top included), so it can
        // be closed by hand once we're done - MenuSelectionManager.
        // clearSelectedPath() turned out not to close any of these under
        // this app's Nimbus look-and-feel, top-level included, so cleanup
        // is symmetric with how each was opened rather than relying on it.
        List<JMenu> openedMenus = new ArrayList<>();
        openedMenus.add(top);
        JMenu currentMenu = top;
        for (int i = 1; i < path.size(); i++) {
            String text = path.get(i);
            JMenuItem found = null;
            StringBuilder seen = new StringBuilder();
            for (int j = 0; j < currentMenu.getItemCount(); j++) {
                JMenuItem item = currentMenu.getItem(j);
                seen.append('[').append(item == null ? "null" : item.getText()).append(']');
                if (item != null && text.equals(item.getText())) {
                    found = item;
                    break;
                }
            }
            if (found == null) {
                System.err.println("menu path lookup failed: no item '" + text + "' under '" +
                        currentMenu.getText() + "' - actual items: " + seen);
                return false;
            }
            // A direct diagonal glide from the top-level menu bar down to this
            // item can graze a sibling top-level menu on the way past - Swing's
            // menu bar switches to whatever top-level menu the pointer is over
            // on mere hover while one is already open, so clipping "Engineering"
            // en route to something under "Inventory" silently opens Engineering's
            // dropdown instead and the click lands there. Descend straight down
            // to the item's row first (staying under the already-open menu,
            // never crossing the bar horizontally), then move across at that
            // row, safely below the bar.
            Point dest = centerOf(found);
            Point cur = currentPointer();
            smoothMoveTo(new Point(cur.x, dest.y));
            smoothMoveTo(dest);

            boolean isTerminal = i == path.size() - 1;
            if (isTerminal) {
                // The submenu(s) above were opened directly via
                // JPopupMenu.show(...) rather than through
                // MenuSelectionManager's normal selection-path machinery
                // (see below), so Swing doesn't consider them part of an
                // active menu session - a raw coordinate click here lands as
                // a stray click outside any tracked menu, which dismisses
                // everything without firing the item's action (confirmed:
                // the target screen never loaded, just closed the menu).
                // JMenuItem.doClick() invokes the action listener directly,
                // sidestepping that entirely.
                JMenuItem terminal = found;
                try {
                    SwingUtilities.invokeAndWait(terminal::doClick);
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                robot.waitForIdle();
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        MenuSelectionManager.defaultManager().clearSelectedPath();
                        for (int k = openedMenus.size() - 1; k >= 0; k--) {
                            JMenu m = openedMenus.get(k);
                            m.getPopupMenu().setVisible(false);
                            m.setSelected(false);
                        }
                    });
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                robot.waitForIdle();
                pause(300);
            } else {
                // Neither a raw java.awt.Robot press/release nor AssertJ's
                // Robot.click() reliably pops a *nested* JMenu's flyout open
                // here (confirmed by screenshotting mid-failure: the click
                // registers but the submenu never appears), and manually
                // extending MenuSelectionManager's selected path updates the
                // selection model but - confirmed via a debug probe - still
                // leaves getPopupMenu().isShowing() false, since that's not
                // what actually triggers the popup to render (that normally
                // happens via a ChangeListener BasicMenuUI itself registers,
                // and apparently isn't wired the same way under this app's
                // Nimbus look-and-feel). Calling JPopupMenu.show(...)
                // directly is the one approach that reliably renders it. The
                // cursor still glides here first purely so a demo recording
                // shows it travelling down the cascade.
                JMenu sub = (JMenu) found;
                try {
                    SwingUtilities.invokeAndWait(() -> {
                        sub.setSelected(true);
                        sub.getPopupMenu().show(sub, sub.getWidth(), 0);
                    });
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
                openedMenus.add(sub);
                robot.waitForIdle();
                pause(300);
            }
            pause(300);
            if (found instanceof JMenu) {
                currentMenu = (JMenu) found;
            }
        }
        return true;
    }

    /** Demo-mode tab selection: clicks the tab's actual on-screen area instead of setSelectedIndex. */
    void clickTabSmooth(JTabbedPane pane, int tabIdx) {
        Rectangle tabBounds = pane.getBoundsAt(tabIdx);
        Point paneOrigin = pane.getLocationOnScreen();
        smoothMoveTo(new Point(paneOrigin.x + tabBounds.x + tabBounds.width / 2,
                paneOrigin.y + tabBounds.y + tabBounds.height / 2));
        pause(120);
        awtRobot.mousePress(InputEvent.BUTTON1_DOWN_MASK);
        pause(60);
        awtRobot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);
        robot.waitForIdle();
    }

    /**
     * Demo-mode combo selection: visibly clicks the combo (opening its
     * dropdown) before picking the item programmatically. Clicking the
     * specific popup list entry is skipped - JComboBox popups aren't
     * reliably part of the normal component tree to target - but the
     * open-then-select combination still reads as a real interaction on
     * screen and Swing closes the popup itself once the selection changes.
     */
    void selectComboSmooth(JComboBox<?> combo, int itemIdx) {
        smoothMoveAndClick(combo);
        pause(300);
        setComboIndex(combo, itemIdx);
    }

    // ------------------------------------------------------------------
    // In-screen component discovery / interaction
    // ------------------------------------------------------------------

    List<Component> findTargets(Container root) {
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
    <T> T nthOfType(List<Component> components, Class<T> type, int n) {
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

    <T extends Component> T firstOfType(Container root, Class<T> type) {
        List<Component> out = new ArrayList<>();
        collectAll(root, type, out);
        return out.isEmpty() ? null : type.cast(out.get(0));
    }

    void collectAll(Component c, Class<?> type, List<Component> out) {
        if (type.isInstance(c)) {
            out.add(c);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collectAll(child, type, out);
            }
        }
    }

    javax.swing.JButton findButtonByText(Container root, String text) {
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

    /**
     * Finds the JTextField or JComboBox that visually belongs to the JLabel
     * whose text matches {@code labelText} (case-insensitive: exact match
     * preferred, else first substring match in tree order) - the field on
     * the same visual row, positioned to the label's right.
     *
     * This is geometric (on-screen position), not tree-structural, on
     * purpose: BlueSeer's generated panels aren't consistent about *how*
     * label+field pairs are parented. Product Code Maintenance interleaves
     * them (label, its field, next label, its field, ...) but Customer
     * Maintenance's GroupLayout adds *all* the labels first as one block and
     * *all* the fields after as a separate block - a "nearest preceding
     * label in traversal order" heuristic silently pairs every field in that
     * second block with whatever the last label in the first block was.
     * Matching by actual rendered position sidesteps that regardless of
     * which pattern a given screen happens to use.
     */
    Component findFieldByLabel(Container root, String labelText) {
        return findNearestToLabel(root, labelText, false);
    }

    /**
     * Same label-then-nearest-to-the-right lookup as {@link #findFieldByLabel},
     * but also considers plain {@link JLabel}s as candidates - used only for
     * "assert" steps, which need to read back computed, read-only results
     * (e.g. {@code lblTaxableAmount} in TerminationLumpSumPanel: a JLabel
     * sitting to the right of a static "Taxable Amount:" caption JLabel,
     * exactly the same row-based layout pattern as a JTextField would use).
     * Kept separate from findFieldByLabel rather than folded in, since a
     * "type" step matching a JLabel by mistake would silently type into
     * nothing instead of failing loudly.
     */
    Component findValueByLabel(Container root, String labelText) {
        return findNearestToLabel(root, labelText, true);
    }

    private Component findNearestToLabel(Container root, String labelText, boolean includeLabels) {
        String wanted = labelText.toLowerCase(java.util.Locale.ROOT);
        List<Component> labels = new ArrayList<>();
        collectShowing(root, JLabel.class, labels);

        JLabel target = null;
        for (Component c : labels) {
            String t = ((JLabel) c).getText();
            if (t != null && t.toLowerCase(java.util.Locale.ROOT).equals(wanted)) {
                target = (JLabel) c;
                break;
            }
        }
        if (target == null) {
            for (Component c : labels) {
                String t = ((JLabel) c).getText();
                if (t != null && t.toLowerCase(java.util.Locale.ROOT).contains(wanted)) {
                    target = (JLabel) c;
                    break;
                }
            }
        }
        if (target == null) {
            return null;
        }

        Point labelPos = target.getLocationOnScreen();
        int labelCenterY = labelPos.y + target.getHeight() / 2;
        int labelRightX = labelPos.x + target.getWidth();

        List<Component> candidates = new ArrayList<>();
        collectShowing(root, JTextField.class, candidates);
        collectShowing(root, JComboBox.class, candidates);
        if (includeLabels) {
            collectShowing(root, JLabel.class, candidates);
        }

        Component best = null;
        long bestScore = Long.MAX_VALUE;
        for (Component c : candidates) {
            if (c == target) {
                continue; // the caption label itself is never its own value
            }
            Point p = c.getLocationOnScreen();
            int centerY = p.y + c.getHeight() / 2;
            int dy = Math.abs(centerY - labelCenterY);
            if (dy > c.getHeight()) {
                continue; // not on the same visual row
            }
            if (p.x < labelPos.x) {
                continue; // must be to the right of the label, not before it
            }
            long dx = Math.max(0, p.x - labelRightX);
            long score = ((long) dy << 32) | dx; // same-row match first, then closest horizontally
            if (score < bestScore) {
                bestScore = score;
                best = c;
            }
        }
        return best;
    }

    /** For a self-describing JLabel with no separate caption+value pair (a standalone banner/warning). */
    boolean anyShowingLabelContains(Container root, String needle) {
        List<Component> labels = new ArrayList<>();
        collectShowing(root, JLabel.class, labels);
        String wanted = needle.toLowerCase(java.util.Locale.ROOT);
        for (Component c : labels) {
            String t = ((JLabel) c).getText();
            if (t != null && t.toLowerCase(java.util.Locale.ROOT).contains(wanted)) {
                return true;
            }
        }
        return false;
    }

    /** Reads back the displayed value of a field found via findValueByLabel. */
    String textOf(Component c) {
        if (c instanceof JTextField) {
            return ((JTextField) c).getText();
        }
        if (c instanceof JLabel) {
            return ((JLabel) c).getText();
        }
        if (c instanceof JComboBox) {
            Object sel = ((JComboBox<?>) c).getSelectedItem();
            return sel == null ? "" : sel.toString();
        }
        return null;
    }

    <T extends Component> void collectShowing(Component c, Class<T> type, List<Component> out) {
        if (c.isShowing() && type.isInstance(c)) {
            out.add(c);
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                collectShowing(child, type, out);
            }
        }
    }

    void typeIntoField(Component field, String text) {
        smoothMoveAndClick(field);
        robot.focusAndWaitForFocusGain(field);
        if (field instanceof JTextField) {
            ((JTextField) field).setText("");
        }
        robot.enterText(text);
    }

    void clickTab(JTabbedPane pane, int tabIdx) {
        SwingUtilities.invokeLater(() -> pane.setSelectedIndex(tabIdx));
        robot.waitForIdle();
    }

    void setComboIndex(JComboBox<?> combo, int itemIdx) {
        // selectComboSmooth clicks the combo first (so a demo recording shows
        // the dropdown genuinely opening), which leaves its popup showing;
        // setSelectedIndex() only updates the model and doesn't go through
        // the popup's own selection listener, so the dropdown never closes
        // on its own - confirmed by screenshotting mid-run: the popup was
        // still open, covering every field below it, well after the
        // selection had already taken effect. setPopupVisible(false) is the
        // direct, documented way to close it.
        SwingUtilities.invokeLater(() -> {
            combo.setSelectedIndex(itemIdx);
            combo.setPopupVisible(false);
        });
        robot.waitForIdle();
    }

    /** Sends a single key press/release by java.awt.event.KeyEvent.VK_* name, e.g. "ENTER", "TAB". */
    void pressKey(String vkName) throws Exception {
        int code = KeyEvent.class.getField("VK_" + vkName.toUpperCase(java.util.Locale.ROOT)).getInt(null);
        robot.pressAndReleaseKey(code);
    }

    String safeText(javax.swing.AbstractButton b) {
        String t = b.getText();
        return t == null ? "" : t;
    }

    void pause(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
        }
    }
}

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
import javax.swing.JRadioButton;
import javax.swing.JTabbedPane;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Frame;
import java.awt.Window;
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
        return frame;
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
     * Finds the first (non-password) JTextField, JPasswordField, or JComboBox
     * whose nearest preceding JLabel (in tree traversal order) has text
     * matching {@code labelText} (case-insensitive, substring match). This is
     * a heuristic, not a real label/component association - it works for the
     * common form pattern of "label added immediately before its field" that
     * BlueSeer's generated panels use, but a differently laid-out screen may
     * need the index-based fallback instead (see DemoRunner's README section).
     */
    Component findFieldByLabel(Container root, String labelText) {
        String[] lastLabel = {""};
        return findFieldByLabelRecursive(root, labelText.toLowerCase(java.util.Locale.ROOT), lastLabel);
    }

    private Component findFieldByLabelRecursive(Component c, String wanted, String[] lastLabel) {
        if (!c.isShowing()) {
            return null;
        }
        if (c instanceof JLabel) {
            String t = ((JLabel) c).getText();
            if (t != null && !t.isBlank()) {
                lastLabel[0] = t.toLowerCase(java.util.Locale.ROOT);
            }
        } else if (c instanceof JTextField || c instanceof JComboBox) {
            if (lastLabel[0].contains(wanted)) {
                return c;
            }
        }
        if (c instanceof Container) {
            for (Component child : ((Container) c).getComponents()) {
                Component found = findFieldByLabelRecursive(child, wanted, lastLabel);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    void typeIntoField(Component field, String text) {
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
        SwingUtilities.invokeLater(() -> combo.setSelectedIndex(itemIdx));
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

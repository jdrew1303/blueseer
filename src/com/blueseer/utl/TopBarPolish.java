/*
The MIT License (MIT)

Copyright (c) Terry Evans Vaughn

All rights reserved.

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.blueseer.utl;

import bsmf.MainFrame;
import com.formdev.flatlaf.FlatClientProperties;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.event.HierarchyEvent;
import java.awt.event.MouseEvent;
import javax.swing.JButton;
import javax.swing.JMenuBar;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.swing.FontIcon;

/**
 * Small, in-place polish for bsmf.MainFrame's single embedded-title-bar row
 * -- no restructuring of the layout (that was tried and reverted; it didn't
 * work for users), just: (1) back/home rendered as real, same-size, flat
 * toolbar-style buttons instead of two differently-sized bundled PNGs on
 * JMenus, and (2) the NavCode quick-jump field given a leading icon,
 * placeholder text, and a tooltip so its purpose is obvious at a glance.
 * Done post-construction through MainFrame's public getJMenuBar() and its
 * public static navcode field -- bsmf.jar itself is never touched.
 */
public class TopBarPolish {

    private static final int ICON_SIZE = 18;
    private static final Color DISABLED_COLOR = new Color(170, 170, 170);

    public static void apply(MainFrame frame) {
        polishNavcode();

        JMenuBar bar = frame.getJMenuBar();
        if (bar == null) {
            return;
        }

        int backIndex = indexOf(bar, "menuback");
        int homeIndex = indexOf(bar, "Home");

        Component backMenu = backIndex >= 0 ? bar.getComponent(backIndex) : null;
        Component homeMenu = homeIndex >= 0 ? bar.getComponent(homeIndex) : null;

        int insertAt = backIndex >= 0 ? backIndex + 1 : homeIndex + 1;
        if (insertAt <= 0) {
            return;
        }

        // menuback/menuhome stay exactly where they are, still fully live --
        // MainFrame's own enable/disable sweeps (disableAllMenus/
        // enableAllMenus, and the "backmenuint"-indexed re-enable in
        // checkperms) all key off the ORIGINAL component's index/name, so
        // removing it from the bar would silently break back-button
        // re-enabling after the first navigation. Shrinking it to zero size
        // makes it take no visible space without touching its visible/
        // enabled state at all, so those internal mechanisms keep working
        // and our new button (below) can just mirror the state changes.
        if (backMenu != null) {
            collapse(backMenu);
            bar.add(toolbarButton(backMenu, MaterialDesignA.ARROW_LEFT, "PrevMenu"), insertAt++);
        }
        if (homeMenu != null) {
            collapse(homeMenu);
            bar.add(toolbarButton(homeMenu, MaterialDesignH.HOME, "Home"), insertAt);
        }

        bar.revalidate();
        bar.repaint();
    }

    private static void collapse(Component c) {
        Dimension zero = new Dimension(0, 0);
        c.setPreferredSize(zero);
        c.setMaximumSize(zero);
        c.setMinimumSize(zero);
    }

    private static void polishNavcode() {
        MainFrame.navcode.putClientProperty(FlatClientProperties.TEXT_FIELD_LEADING_ICON,
                FontIcon.of(MaterialDesignA.ARROW_RIGHT_BOLD_CIRCLE_OUTLINE, 14));
        MainFrame.navcode.putClientProperty(FlatClientProperties.PLACEHOLDER_TEXT, "NavCode");
        MainFrame.navcode.putClientProperty(FlatClientProperties.TEXT_FIELD_SHOW_CLEAR_BUTTON, true);
        MainFrame.navcode.setToolTipText(
                "Enter a NavCode and press Enter to jump directly to its menu (assigned per menu item in Admin > Menu Maintenance)");
        Dimension size = new Dimension(130, 30);
        MainFrame.navcode.setPreferredSize(size);
        MainFrame.navcode.setMaximumSize(size);
    }

    private static int indexOf(JMenuBar bar, String name) {
        Component[] components = bar.getComponents();
        for (int i = 0; i < components.length; i++) {
            if (name.equals(components[i].getName())) {
                return i;
            }
        }
        return -1;
    }

    // menuback/menuhome keep their original MouseListeners (still live, just
    // collapsed to zero size above), which is what actually performs the
    // navigation -- forwarding a synthetic click into them lets the new
    // button reuse that logic exactly instead of duplicating it. Both
    // visibility (hidden until login finishes populating the menu bar) and
    // enabled state (menuback stays disabled until you've navigated away
    // from the home screen) are mirrored from the original rather than
    // tracked separately, since MainFrame's own code is what drives both.
    private static JButton toolbarButton(Component target, Ikon ikon, String tooltip) {
        JButton button = new JButton(FontIcon.of(ikon, ICON_SIZE));
        button.setDisabledIcon(FontIcon.of(ikon, ICON_SIZE, DISABLED_COLOR));
        button.putClientProperty(FlatClientProperties.BUTTON_TYPE, FlatClientProperties.BUTTON_TYPE_TOOLBAR_BUTTON);
        button.setToolTipText(tooltip);
        button.setFocusable(false);
        button.setName(target.getName());
        button.setEnabled(target.isEnabled());
        button.setVisible(target.isVisible());
        target.addPropertyChangeListener("enabled", evt -> button.setEnabled((Boolean) evt.getNewValue()));
        // Component.setVisible() does NOT fire a "visible" bound property
        // change (verified empirically) -- HierarchyListener/SHOWING_CHANGED
        // is the mechanism that actually reports visibility toggles.
        target.addHierarchyListener(evt -> {
            if ((evt.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
                button.setVisible(target.isVisible());
            }
        });
        button.addActionListener(e -> target.dispatchEvent(new MouseEvent(
                target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 5, 5, 1, false)));
        return button;
    }
}

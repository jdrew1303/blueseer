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
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Container;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.swing.Box;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JToolBar;

/**
 * Splits bsmf.MainFrame's single, crowded embedded-title-bar JMenuBar into
 * two rows (FlatLaf-demo style): the business menus stay merged into the
 * title bar, everything else (back/home nav, quick-nav search field, status
 * label, progress bar) moves to a plain JToolBar underneath. Built entirely
 * on MainFrame's existing public surface (getJMenuBar/getContentPane/
 * setContentPane, and its public static navcode/messagelabel/MainProgressBar
 * fields) -- bsmf.jar itself is never modified or recompiled.
 */
public class TopBarLayout {

    public static void apply(MainFrame frame) {
        JMenuBar bar = frame.getJMenuBar();
        if (bar == null) {
            return;
        }

        Component backMenu = findByName(bar, "menuback");
        Component homeMenu = findByName(bar, "Home");

        JToolBar toolBar = new JToolBar();
        toolBar.setFloatable(false);

        if (backMenu != null) {
            keepPermanentlyHidden(backMenu);
            toolBar.add(forwardingButton(backMenu, "/images/back.png", "PrevMenu"));
        }
        if (homeMenu != null) {
            keepPermanentlyHidden(homeMenu);
            toolBar.add(forwardingButton(homeMenu, "/images/home2.png", "Home"));
        }

        toolBar.addSeparator();
        keepPermanentlyVisible(MainFrame.navcode);
        toolBar.add(MainFrame.navcode);

        toolBar.add(Box.createHorizontalGlue());

        JLabel diagnosticsLabel = new JLabel();
        diagnosticsLabel.setEnabled(false);
        toolBar.add(diagnosticsLabel);
        toolBar.addSeparator();
        interceptDiagnosticsTitle(frame, diagnosticsLabel);

        toolBar.add(MainFrame.messagelabel);
        toolBar.addSeparator();
        toolBar.add(MainFrame.MainProgressBar);

        Container oldContent = frame.getContentPane();
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(toolBar, BorderLayout.NORTH);
        wrapper.add(oldContent, BorderLayout.CENTER);
        frame.setContentPane(wrapper);
        frame.revalidate();
    }

    private static Component findByName(JMenuBar bar, String name) {
        for (Component c : bar.getComponents()) {
            if (name.equals(c.getName())) {
                return c;
            }
        }
        return null;
    }

    // menuback/menuhome stay off-bar but alive (still holding their original
    // listeners, which the forwarding buttons dispatch into) -- MainFrame's
    // own internal logic occasionally calls setVisible(true) on them again
    // (e.g. its quick-nav-code flow), which would otherwise pop up a
    // duplicate icon alongside our toolbar button.
    private static void keepPermanentlyHidden(Component c) {
        c.setVisible(false);
        c.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentShown(ComponentEvent e) {
                c.setVisible(false);
            }
        });
    }

    // navcode defaults to hidden until MainFrame's quick-nav flow reveals it;
    // we want it permanently visible in the new toolbar instead.
    private static void keepPermanentlyVisible(Component c) {
        c.setVisible(true);
        c.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentHidden(ComponentEvent e) {
                c.setVisible(true);
            }
        });
    }

    // MainFrame.setFrameTitle() builds "USER=...        IP=...        VER=...
    // DBTYPE=...        DBNAME=..." and sets it as the actual window title,
    // which FlatLaf's embedded title bar renders centered -- that overly
    // long string is what was overflowing/truncating there. We can't stop
    // MainFrame from setting it (private logic, closed-source), but Frame
    // fires a "title" property change on every setTitle() call, so we can
    // catch it, swap in a short app title, and surface the diagnostic
    // details as a compact label (full text in the tooltip) in the toolbar
    // instead.
    private static void interceptDiagnosticsTitle(MainFrame frame, JLabel diagnosticsLabel) {
        frame.addPropertyChangeListener("title", evt -> {
            String newTitle = (String) evt.getNewValue();
            if (newTitle == null || !newTitle.startsWith("USER=")) {
                return;
            }
            String condensed = Arrays.stream(newTitle.trim().split("\\s{2,}"))
                    .collect(Collectors.joining("  |  "));
            diagnosticsLabel.setText(condensed);
            diagnosticsLabel.setToolTipText(condensed);
            frame.setTitle("BlueSeer ERP");
        });
    }

    private static JButton forwardingButton(Component target, String iconResource, String tooltip) {
        JButton button = new JButton(new ImageIcon(TopBarLayout.class.getResource(iconResource)));
        button.setToolTipText(tooltip);
        button.addActionListener(e -> target.dispatchEvent(new MouseEvent(
                target, MouseEvent.MOUSE_PRESSED, System.currentTimeMillis(), 0, 5, 5, 1, false)));
        return button;
    }
}

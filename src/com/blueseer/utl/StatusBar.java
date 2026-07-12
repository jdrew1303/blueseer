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
import java.awt.FlowLayout;
import java.awt.Font;
import java.util.Arrays;
import java.util.stream.Collectors;
import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.UIManager;
import javax.swing.border.CompoundBorder;

/**
 * Adds a VS Code-style status bar along the bottom of the window and moves
 * three things there that don't belong crammed into the menu bar: the
 * "System Ready"/NavCode status message, the connection diagnostics
 * (currently the whole window title -- USER=/IP=/VER=/DBTYPE=/DBNAME=), and
 * the progress bar. Done post-construction through MainFrame's public
 * getContentPane()/setContentPane() and its public static messagelabel/
 * MainProgressBar fields -- bsmf.jar itself is never touched. Re-parenting
 * messagelabel/MainProgressBar here removes them from the menu bar
 * automatically (a component can only have one parent).
 */
public class StatusBar {

    public static void apply(MainFrame frame) {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setBorder(new CompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, UIManager.getColor("Separator.foreground")),
                BorderFactory.createEmptyBorder(2, 8, 2, 8)));

        // BlueSeerUtils.message() recolors this label per severity (red for
        // errors, green for success, purple for warnings, blue for info,
        // black otherwise) every time it sets text -- left alone here, that
        // coding carries over correctly into the status bar.
        shrinkFont(MainFrame.messagelabel);
        MainFrame.messagelabel.setPreferredSize(null);
        MainFrame.messagelabel.setMaximumSize(null);
        MainFrame.messagelabel.setHorizontalAlignment(SwingConstants.LEFT);
        bar.add(MainFrame.messagelabel, BorderLayout.WEST);

        JPanel rightSide = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        rightSide.setOpaque(false);
        JLabel diagnosticsLabel = new JLabel();
        shrinkFont(diagnosticsLabel);
        diagnosticsLabel.setForeground(UIManager.getColor("Label.disabledForeground"));
        rightSide.add(diagnosticsLabel);
        rightSide.add(MainFrame.MainProgressBar);
        bar.add(rightSide, BorderLayout.EAST);

        interceptDiagnosticsTitle(frame, diagnosticsLabel);

        Container oldContent = frame.getContentPane();
        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(oldContent, BorderLayout.CENTER);
        wrapper.add(bar, BorderLayout.SOUTH);
        frame.setContentPane(wrapper);
        frame.revalidate();
    }

    private static void shrinkFont(Component c) {
        Font f = c.getFont();
        c.setFont(f.deriveFont(f.getSize2D() - 1f));
    }

    // MainFrame.setFrameTitle() builds "USER=...        IP=...        VER=...
    // DBTYPE=...        DBNAME=..." and sets it as the actual window title.
    // Frame fires a "title" property change on every setTitle() call, so we
    // intercept it, swap in a short app title, and surface the diagnostic
    // details in the status bar instead.
    private static void interceptDiagnosticsTitle(MainFrame frame, JLabel diagnosticsLabel) {
        frame.addPropertyChangeListener("title", evt -> {
            String newTitle = (String) evt.getNewValue();
            if (newTitle == null || !newTitle.startsWith("USER=")) {
                return;
            }
            String condensed = Arrays.stream(newTitle.trim().split("\\s{2,}"))
                    .collect(Collectors.joining("   |   "));
            diagnosticsLabel.setText(condensed);
            diagnosticsLabel.setToolTipText(condensed);
            frame.setTitle("BlueSeer ERP");
        });
    }
}

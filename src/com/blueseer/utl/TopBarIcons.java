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
import java.awt.Color;
import java.awt.Component;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import org.kordamp.ikonli.Ikon;
import org.kordamp.ikonli.materialdesign2.MaterialDesignA;
import org.kordamp.ikonli.materialdesign2.MaterialDesignH;
import org.kordamp.ikonli.swing.FontIcon;

/**
 * bsmf.MainFrame's back/home menu-bar icons come from two bundled PNGs of
 * different sizes (back.png is 22x22, home2.png is 16x16), so they render at
 * visibly different sizes side by side. Swapped here, post-construction, for
 * matching-size Ikonli vector icons -- the same icon mechanism already used
 * everywhere else in this codebase (e.g. ItemMaint's FontIcon.of(...) toolbar
 * buttons) -- via MainFrame's public getJMenuBar()/JMenu.setIcon(), without
 * touching bsmf.jar.
 */
public class TopBarIcons {

    private static final int SIZE = 18;
    private static final Color DISABLED_COLOR = new Color(170, 170, 170);

    public static void apply(MainFrame frame) {
        JMenuBar bar = frame.getJMenuBar();
        if (bar == null) {
            return;
        }
        for (Component c : bar.getComponents()) {
            if (!(c instanceof JMenu)) {
                continue;
            }
            String name = c.getName();
            if ("menuback".equals(name)) {
                setIcons((JMenu) c, MaterialDesignA.ARROW_LEFT);
            } else if ("Home".equals(name)) {
                setIcons((JMenu) c, MaterialDesignH.HOME);
            }
        }
    }

    // JMenu (an AbstractButton) only auto-derives a grayed-out disabled icon
    // for plain ImageIcons; a custom Icon like FontIcon renders blank instead
    // once the menu is disabled (e.g. menuback right after login), so both
    // variants need to be set explicitly.
    private static void setIcons(JMenu menu, Ikon ikon) {
        menu.setIcon(FontIcon.of(ikon, SIZE));
        menu.setDisabledIcon(FontIcon.of(ikon, SIZE, DISABLED_COLOR));
    }
}

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
import com.formdev.flatlaf.FlatLightLaf;
import java.io.File;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.InputMap;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.KeyStroke;
import javax.swing.UIManager;

/**
 *
 * @author terryva
 */
public class mf {

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        if (relaunchInAppDirectoryIfNeeded(args)) {
            return;
        }

        /* Set the FlatLaf look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* FlatLaf (https://www.formdev.com/flatlaf/) gives Swing a modern, flat
         * appearance and respects the OS font/scaling settings. Falls back to the
         * platform default look and feel if FlatLaf can't be installed.
         */
        //</editor-fold>

        // Let FlatLaf draw the window title bar/border and merge the menu bar
        // into it, like a modern browser or VS Code, instead of the plain OS
        // title bar with a separate menu bar underneath.
        JFrame.setDefaultLookAndFeelDecorated(true);
        JDialog.setDefaultLookAndFeelDecorated(true);
        UIManager.put("TitlePane.menuBarEmbedded", true);

        FlatLightLaf.setup();

        try {
            // TEV 20160318 added the below for windows 'doclick' fix
            InputMap im = (InputMap) UIManager.get("Button.focusInputMap");
            im.put(KeyStroke.getKeyStroke("ENTER"), "pressed");
            im.put(KeyStroke.getKeyStroke("released ENTER"), "released");
        } catch (Exception ex) {
            MainFrame.bslog(ex);
        }


        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new MainFrame().setVisible(true);
            }
        });


    }

    /**
     * bs.cfg, data/, jasper/, etc. are all read relative to the process's
     * working directory. That's fine when launched from a shell in the right
     * place, but a jpackage-built native launcher (and desktop/menu shortcuts)
     * doesn't set one, so it inherits whatever directory the user happened to
     * be in. If bs.cfg isn't next to us, look one level above the jar we were
     * loaded from -- that's where the packaging (both the Maven "target" layout
     * and the jpackage app image layout) places it alongside the app's dist/
     * or lib/app/ jars -- and relaunch ourselves there.
     *
     * @return true if a relaunch was performed (the caller should return
     * immediately; this process is just supervising the real one)
     */
    private static boolean relaunchInAppDirectoryIfNeeded(String[] args) {
        if (new File("bs.cfg").isFile()) {
            return false;
        }
        try {
            File jarFile = new File(mf.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            File appDir = jarFile.getParentFile().getParentFile();
            if (appDir == null || !new File(appDir, "bs.cfg").isFile()) {
                return false;
            }

            String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            List<String> command = new ArrayList<>();
            command.add(javaBin);
            for (String prop : new String[]{"java.util.logging.config.file", "user.language", "user.country"}) {
                String value = System.getProperty(prop);
                if (value != null) {
                    command.add("-D" + prop + "=" + value);
                }
            }
            command.add("-cp");
            command.add(System.getProperty("java.class.path"));
            command.add(mf.class.getName());
            command.addAll(Arrays.asList(args));

            Process process = new ProcessBuilder(command).directory(appDir).inheritIO().start();
            System.exit(process.waitFor());
            return true;
        } catch (URISyntaxException | java.io.IOException | InterruptedException ex) {
            return false;
        }
    }

}

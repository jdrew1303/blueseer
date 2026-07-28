package com.blueseer.pay;

import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * Menu entry point for S-01: the app's menu loader only instantiates
 * {@code JPanel}s via reflection (see {@code bsmf.MainFrame.loadPanel}), so
 * the wizard itself - a modal {@code JDialog} - is launched from here rather
 * than being the menu target directly.
 */
public class CompanySetupLauncherPanel extends JPanel {

    public CompanySetupLauncherPanel() {
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(new JLabel("Set up a new Irish payroll company:"));
        JButton btNewCompany = new JButton("New Company Setup");
        btNewCompany.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            new NewCompanyWizardDialog(owner).setVisible(true);
        });
        center.add(btNewCompany);
        add(center);
    }
}

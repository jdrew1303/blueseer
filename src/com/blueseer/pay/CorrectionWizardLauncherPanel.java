package com.blueseer.pay;

import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** S-33 menu entry point - same reasoning as {@link CompanySetupLauncherPanel}: the wizard itself is a modal {@code JDialog}. */
public class CorrectionWizardLauncherPanel extends JPanel {

    public CorrectionWizardLauncherPanel() {
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(new JLabel("Correct a previously-filed PSR:"));
        JButton btOpen = new JButton("Correction PSR Wizard");
        btOpen.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            PayrollStubStore store = PayrollStubStore.shared();
            IPsrGateway gateway = new InMemoryPsrGateway(store, new InMemoryEmployeeRepository(store),
                    new InMemoryRevenueDetailsService(store), new InMemoryAdditionDeductionService(store));
            new CorrectionWizardDialog(owner, gateway, new InMemoryEmployeeRepository(store), store).setVisible(true);
        });
        center.add(btOpen);
        add(center);
    }
}

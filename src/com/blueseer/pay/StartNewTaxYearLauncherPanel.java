package com.blueseer.pay;

import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** S-65 menu entry point: {@link StartNewTaxYearWizard} is a modal {@code JDialog}, launched from here. */
public class StartNewTaxYearLauncherPanel extends JPanel {

    public StartNewTaxYearLauncherPanel() {
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        PayrollStubStore store = PayrollStubStore.shared();
        center.add(new JLabel("Currently active tax year: " + store.activeTaxYear));
        JButton btOpen = new JButton("Start New Tax Year...");
        btOpen.addActionListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(this);
            new StartNewTaxYearWizard(owner, new InMemoryTaxYearRolloverController(store), store.activeTaxYear, store.activeTaxYear + 1).setVisible(true);
        });
        center.add(btOpen);
        add(center);
    }
}

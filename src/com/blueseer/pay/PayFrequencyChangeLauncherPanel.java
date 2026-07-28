package com.blueseer.pay;

import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** S-64 menu entry point: {@link PayFrequencyChangeWizard} is a modal {@code JDialog}, launched from here. */
public class PayFrequencyChangeLauncherPanel extends JPanel {

    public PayFrequencyChangeLauncherPanel() {
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(new JLabel("Change an employee's pay frequency (e.g. Weekly to Monthly):"));
        JButton btOpen = new JButton("Change Pay Frequency...");
        btOpen.addActionListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(this);
            PayrollStubStore store = PayrollStubStore.shared();
            new PayFrequencyChangeWizard(owner, new InMemoryPayFrequencyController(store), new InMemoryEmployeeRepository(store)).setVisible(true);
        });
        center.add(btOpen);
        add(center);
    }
}

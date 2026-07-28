package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** S-31 menu entry point - same reasoning as {@link CompanySetupLauncherPanel}: the wizard itself is a modal {@code JDialog}. */
public class PsrPrepareLauncherPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    public PsrPrepareLauncherPanel() {
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(new JLabel("Prepare and submit the pending Payroll Submission Request:"));
        JButton btOpen = new JButton("PSR Prepare & Submit");
        btOpen.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            PayrollStubStore store = PayrollStubStore.shared();
            IPsrGateway gateway = new InMemoryPsrGateway(store, new InMemoryEmployeeRepository(store),
                    new InMemoryRevenueDetailsService(store), new InMemoryAdditionDeductionService(store));
            new PsrPrepareDialog(owner, gateway, DEMO_COMPANY).setVisible(true);
        });
        center.add(btOpen);
        add(center);
    }
}

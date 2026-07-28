package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * S-38 menu entry point: {@link BankFileWizard} is a modal {@code JDialog},
 * so (same reason as {@link ImportHoursLauncherPanel}) it is launched from
 * here rather than being the menu target directly.
 */
public class BankFileWizardLauncherPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    public BankFileWizardLauncherPanel() {
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(new JLabel("Generate a bank payment file for employees paid by credit transfer:"));
        JButton btOpen = new JButton("Pay Employees...");
        btOpen.addActionListener(e -> {
            Frame owner = (Frame) SwingUtilities.getWindowAncestor(this);
            PayrollStubStore store = PayrollStubStore.shared();
            IPaymentController controller = new InMemoryPaymentController(store);
            IPayslipDistributionController distributionController =
                    new InMemoryPayslipDistributionController(store, new PayslipReportDataProvider(store));
            new BankFileWizard(owner, controller, distributionController, DEMO_COMPANY).setVisible(true);
        });
        center.add(btOpen);
        add(center);
    }
}

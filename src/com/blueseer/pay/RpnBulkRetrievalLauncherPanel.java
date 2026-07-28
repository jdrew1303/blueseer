package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** S-15 menu entry point: {@link RpnBulkRetrievalDialog} is a modal {@code JDialog}, launched from here. */
public class RpnBulkRetrievalLauncherPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    public RpnBulkRetrievalLauncherPanel() {
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(new JLabel("Retrieve updated RPN credits/cut-off points for all employees, ahead of finalising this pay period:"));
        JButton btOpen = new JButton("RPN Retrieval...");
        btOpen.addActionListener(e -> {
            Window owner = SwingUtilities.getWindowAncestor(this);
            new RpnBulkRetrievalDialog(owner, new InMemoryRpnGateway(PayrollStubStore.shared()), DEMO_COMPANY).setVisible(true);
        });
        center.add(btOpen);
        add(center);
    }
}

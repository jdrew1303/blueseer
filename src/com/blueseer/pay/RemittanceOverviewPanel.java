package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.RemittanceDtos.RemittanceSummaryDTO;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/** S-66 Remittance to Revenue - Overview. */
public class RemittanceOverviewPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IRemittanceController controller;

    private JLabel lblAmountDue;
    private JLabel lblDueDate;
    private JLabel lblPaymentStatus;

    public RemittanceOverviewPanel() {
        this(new InMemoryRemittanceController(PayrollStubStore.shared()));
    }

    public RemittanceOverviewPanel(IRemittanceController controller) {
        this.controller = controller;
        initComponents();
        refresh();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refresh();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Remittance to Revenue - Overview"));
        content.setPreferredSize(new Dimension(480, 260));

        JPanel form = new JPanel();
        GroupLayout layout = new GroupLayout(form);
        form.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblAmountDueLabel = new JLabel("Amount Due:");
        lblAmountDue = new JLabel("-");
        lblAmountDue.setFont(lblAmountDue.getFont().deriveFont(Font.BOLD, 22f));

        JLabel lblDueDateLabel = new JLabel("Due Date:");
        lblDueDate = new JLabel("-");

        JLabel lblStatusLabel = new JLabel("Payment Status:");
        lblPaymentStatus = new JLabel("-");

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblAmountDueLabel).addComponent(lblDueDateLabel).addComponent(lblStatusLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblAmountDue).addComponent(lblDueDate).addComponent(lblPaymentStatus))));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblAmountDueLabel).addComponent(lblAmountDue))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDueDateLabel).addComponent(lblDueDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblStatusLabel).addComponent(lblPaymentStatus)));

        content.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        JButton btViewTaxDetailsReport = new JButton("View Tax Details Report");
        btViewTaxDetailsReport.addActionListener(e -> openInDialog("Tax Details Report", new TaxDetailsReportPanel()));
        JButton btGoToPaymentDueDates = new JButton("Payment Due Dates");
        btGoToPaymentDueDates.addActionListener(e -> openInDialog("Payment Due Dates", new PaymentDueDatesPanel()));
        south.add(btViewTaxDetailsReport);
        south.add(btGoToPaymentDueDates);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void openInDialog(String title, JPanel panel) {
        Window owner = SwingUtilities.getWindowAncestor(this);
        JDialog dialog = new JDialog(owner, title, java.awt.Dialog.ModalityType.APPLICATION_MODAL);
        dialog.getContentPane().add(panel);
        dialog.pack();
        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void refresh() {
        RemittanceSummaryDTO summary = controller.getRemittanceSummary(DEMO_COMPANY);
        lblAmountDue.setText("€" + summary.amountDue().toPlainString());
        lblDueDate.setText(summary.dueDate() == null ? "-" : summary.dueDate().toString());
        lblPaymentStatus.setText(summary.paymentStatus());
        lblPaymentStatus.setForeground("Overdue".equals(summary.paymentStatus()) ? new Color(180, 0, 0) : new Color(0, 120, 0));
    }
}

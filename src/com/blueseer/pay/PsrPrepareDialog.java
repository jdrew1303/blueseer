package com.blueseer.pay;

import com.blueseer.pay.PsrDtos.PsrLineItemDTO;
import com.blueseer.pay.PsrDtos.PsrSubmissionResult;
import com.blueseer.pay.PsrDtos.PsrSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/**
 * S-31 PSR Prepare & Submit, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * The pending PSR is whichever batch {@link PayPeriodFinaliser} most
 * recently created in "Due" status - this dialog only ever shows/submits
 * one at a time (S-32 handles the multi-batch control panel view).
 */
public class PsrPrepareDialog extends JDialog {

    private static final String CARD_SUMMARY = "summary";
    private static final String CARD_DETAIL = "detail";
    private static final String CARD_CONFIRMED = "confirmed";

    private final IPsrGateway gateway;
    private final CompanyId companyId;

    private CardLayout cardLayout;
    private JPanel cards;
    private JButton btSubmitPsrToRevenue;
    private JLabel lblConfirmedReference;
    private JLabel lblConfirmedTotal;

    public PsrPrepareDialog(Frame owner, IPsrGateway gateway, CompanyId companyId) {
        super(owner, "PSR Prepare & Submit", true);
        this.gateway = gateway;
        this.companyId = companyId;
        initComponents();
        setSize(560, 400);
        setLocation(60, 60);
    }

    private void initComponents() {
        setLayout(new BorderLayout());
        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);
        cards.add(buildSummaryCard(), CARD_SUMMARY);
        cards.add(buildDetailCard(), CARD_DETAIL);
        cards.add(buildConfirmedCard(), CARD_CONFIRMED);
        add(cards, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btClose = new JButton("Close");
        btClose.addActionListener(e -> onClose());
        south.add(btClose);
        add(south, BorderLayout.SOUTH);
    }

    private JPanel buildSummaryCard() {
        JPanel panel = new JPanel();
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        PsrSummaryDTO summary = gateway.getPendingPsrSummary(companyId);

        JLabel lblPayeLabel = new JLabel("PAYE Total:");
        JLabel lblPayeTotal = new JLabel(summary.payeTotal().toPlainString());
        JLabel lblUscLabel = new JLabel("USC Total:");
        JLabel lblUscTotal = new JLabel(summary.uscTotal().toPlainString());
        JLabel lblPrsiLabel = new JLabel("PRSI Total:");
        JLabel lblPrsiTotal = new JLabel(summary.prsiTotal().toPlainString());
        JLabel lblLptLabel = new JLabel("LPT Total:");
        JLabel lblLptTotal = new JLabel(summary.lptTotal().toPlainString());

        JButton btViewDetail = new JButton("View Detail");
        btViewDetail.addActionListener(e -> cardLayout.show(cards, CARD_DETAIL));
        btSubmitPsrToRevenue = new JButton("Submit PSR to Revenue");
        btSubmitPsrToRevenue.addActionListener(e -> onSubmit());

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblPayeLabel).addComponent(lblUscLabel).addComponent(lblPrsiLabel).addComponent(lblLptLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblPayeTotal).addComponent(lblUscTotal).addComponent(lblPrsiTotal).addComponent(lblLptTotal)))
                .addComponent(btViewDetail)
                .addComponent(btSubmitPsrToRevenue));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPayeLabel).addComponent(lblPayeTotal))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblUscLabel).addComponent(lblUscTotal))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPrsiLabel).addComponent(lblPrsiTotal))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblLptLabel).addComponent(lblLptTotal))
                .addComponent(btViewDetail)
                .addComponent(btSubmitPsrToRevenue));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        return wrapper;
    }

    private JPanel buildDetailCard() {
        JPanel panel = new JPanel(new BorderLayout());
        DefaultTableModel model = new DefaultTableModel(new Object[] { "Employee", "PAYE", "PRSI", "USC", "LPT" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (PsrLineItemDTO line : gateway.getPsrDetail(companyId)) {
            model.addRow(new Object[] { line.employeeId().value(), line.paye().toPlainString(),
                    line.prsi().toPlainString(), line.usc().toPlainString(), line.lpt().toPlainString() });
        }
        panel.add(new JScrollPane(new JTable(model)), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btBackToSummary = new JButton("Back to Summary");
        btBackToSummary.addActionListener(e -> cardLayout.show(cards, CARD_SUMMARY));
        south.add(btBackToSummary);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildConfirmedCard() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Submission Confirmed"));
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblRefLabel = new JLabel("Revenue Reference:");
        lblConfirmedReference = new JLabel("-");
        JLabel lblTotalLabel = new JLabel("Total Submitted:");
        lblConfirmedTotal = new JLabel("-");

        layout.setHorizontalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.LEADING).addComponent(lblRefLabel).addComponent(lblTotalLabel))
                .addGroup(layout.createParallelGroup(Alignment.LEADING).addComponent(lblConfirmedReference).addComponent(lblConfirmedTotal)));
        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblRefLabel).addComponent(lblConfirmedReference))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblTotalLabel).addComponent(lblConfirmedTotal)));

        JPanel wrapper = new JPanel(new BorderLayout());
        wrapper.add(panel, BorderLayout.NORTH);
        return wrapper;
    }

    private void onSubmit() {
        btSubmitPsrToRevenue.setEnabled(false);
        new SwingWorker<PsrSubmissionResult, Void>() {
            @Override
            protected PsrSubmissionResult doInBackground() {
                return gateway.submitPsr(companyId);
            }

            @Override
            protected void done() {
                btSubmitPsrToRevenue.setEnabled(true);
                try {
                    PsrSubmissionResult result = get();
                    if (!result.success()) {
                        JOptionPane.showMessageDialog(PsrPrepareDialog.this, "Nothing pending to submit.",
                                "Submit PSR", JOptionPane.WARNING_MESSAGE);
                        return;
                    }
                    lblConfirmedReference.setText(result.revenueReferenceNumber());
                    lblConfirmedTotal.setText(result.totalSubmitted().toPlainString());
                    cardLayout.show(cards, CARD_CONFIRMED);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(PsrPrepareDialog.this, "Submission failed: " + ex.getMessage(),
                            "Submit PSR", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void onClose() {
        if (lblConfirmedReference != null && !"-".equals(lblConfirmedReference.getText())) {
            // Per the exemplar: "On closing out of the PSR utility, you will
            // be prompted to take a backup" - a call-out to the existing
            // ERP-level backup utility, not a new screen for this module.
            int confirm = JOptionPane.showConfirmDialog(this,
                    "Take a backup now?", "Backup Reminder", JOptionPane.YES_NO_OPTION);
            if (confirm == JOptionPane.YES_OPTION) {
                JOptionPane.showMessageDialog(this, "Open Admin > Backup to run a backup.",
                        "Backup Reminder", JOptionPane.INFORMATION_MESSAGE);
            }
        }
        dispose();
    }
}

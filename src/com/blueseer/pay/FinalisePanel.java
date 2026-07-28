package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.AnomalyDTO;
import com.blueseer.pay.PayProcessingDtos.FinalisationResult;
import com.blueseer.pay.PayProcessingDtos.FinalisationSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.FlowLayout;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.text.DateFormatter;

/**
 * S-22 Finalise Pay Period ("Update Payslips"), per
 * docs/architecture/irish-payroll-2026-screen-specs.md. Reproduces the
 * exemplar's own two-step Update -> OK confirmation sequence exactly, and
 * runs S-24's anomaly check before its own confirmation whenever anomalies
 * are found, per the spec.
 */
public class FinalisePanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");

    private final IFinalisationController finalisationController;
    private final IPayrollCalendarController calendarController;

    private Week53Banner week53Banner;
    private JLabel lblLastRpnImportDate;
    private JLabel lblLastPeriodUpdated;
    private JLabel lblPeriodBeingProcessed;
    private JFormattedTextField dcPayDate;
    private JLabel lblPayslipReferenceNote;
    private JTextField tbPayslipReferenceNote;
    private JButton btUpdate;

    public FinalisePanel() {
        this(new InMemoryFinalisationController(PayrollStubStore.shared(), PayrollEngineFactory.defaultCalculationService()),
                new InMemoryPayrollCalendarController(PayrollStubStore.shared()));
    }

    public FinalisePanel(IFinalisationController finalisationController, IPayrollCalendarController calendarController) {
        this.finalisationController = finalisationController;
        this.calendarController = calendarController;
        initComponents();
        reload();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            reload();
        }
    }

    private void initComponents() {
        week53Banner = new Week53Banner();

        JPanel panel = new JPanel();
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblLastRpnLabel = new JLabel("Last RPN Import Date:");
        lblLastRpnImportDate = new JLabel("-");
        JLabel lblLastPeriodLabel = new JLabel("Last Period Updated:");
        lblLastPeriodUpdated = new JLabel("-");
        JLabel lblPeriodLabel = new JLabel("Period Being Processed:");
        lblPeriodBeingProcessed = new JLabel("-");
        lblPeriodBeingProcessed.setFont(lblPeriodBeingProcessed.getFont().deriveFont(java.awt.Font.BOLD));
        JLabel lblPayDate = new JLabel("Pay Date:");
        DateFormatter formatter = new DateFormatter(DATE_FMT);
        formatter.setAllowsInvalid(true);
        dcPayDate = new JFormattedTextField(formatter);
        dcPayDate.setColumns(10);
        lblPayslipReferenceNote = new JLabel("Payslip Reference Note:");
        tbPayslipReferenceNote = new JTextField(20);

        btUpdate = new JButton("Update");
        btUpdate.addActionListener(e -> onUpdate());

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblLastRpnLabel).addComponent(lblLastPeriodLabel).addComponent(lblPeriodLabel)
                                .addComponent(lblPayDate).addComponent(lblPayslipReferenceNote))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblLastRpnImportDate).addComponent(lblLastPeriodUpdated).addComponent(lblPeriodBeingProcessed)
                                .addComponent(dcPayDate).addComponent(tbPayslipReferenceNote)))
                .addComponent(btUpdate));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblLastRpnLabel).addComponent(lblLastRpnImportDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblLastPeriodLabel).addComponent(lblLastPeriodUpdated))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPeriodLabel).addComponent(lblPeriodBeingProcessed))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPayDate).addComponent(dcPayDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPayslipReferenceNote).addComponent(tbPayslipReferenceNote))
                .addComponent(btUpdate));

        JPanel content = new JPanel(new java.awt.BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Finalise Pay Period"));
        content.add(week53Banner, java.awt.BorderLayout.NORTH);
        content.add(panel, java.awt.BorderLayout.CENTER);
        add(content);
    }

    private void reload() {
        FinalisationSummaryDTO summary = finalisationController.getFinalisationSummary(DEMO_COMPANY);
        week53Banner.setWeek53(calendarController.isWeek53Period(DEMO_COMPANY, summary.periodBeingProcessed()));
        lblLastRpnImportDate.setText(summary.lastRpnImportDateOrNull() == null ? "Not yet imported" : summary.lastRpnImportDateOrNull().toString());
        lblLastPeriodUpdated.setText(summary.lastPeriodUpdatedLabel());
        lblPeriodBeingProcessed.setText(String.valueOf(summary.periodBeingProcessed()));
        dcPayDate.setValue(Date.from(summary.suggestedPayDate().atStartOfDay(ZoneId.systemDefault()).toInstant()));
        lblPayslipReferenceNote.setVisible(summary.payslipReferenceNoteVisible());
        tbPayslipReferenceNote.setVisible(summary.payslipReferenceNoteVisible());
    }

    private void onUpdate() {
        List<AnomalyDTO> anomalies = finalisationController.detectAnomalies(DEMO_COMPANY, currentPeriod());
        if (!anomalies.isEmpty()) {
            AnomalyWarningDialog dialog = new AnomalyWarningDialog(
                    (java.awt.Frame) SwingUtilities.getWindowAncestor(this), anomalies);
            dialog.setVisible(true);
            if (dialog.getOutcome() == AnomalyWarningDialog.Outcome.REVIEW_IN_PAY_ENTRY) {
                JOptionPane.showMessageDialog(this, "Open Payroll > Pay Entry to review the flagged employee.",
                        "Review in Pay Entry", JOptionPane.INFORMATION_MESSAGE);
                return;
            }
            if (dialog.getOutcome() == AnomalyWarningDialog.Outcome.CANCELLED) {
                return;
            }
        }

        int confirm = JOptionPane.showConfirmDialog(this, "This will finalise payroll for ALL employees. Continue?",
                "Confirm Finalisation", JOptionPane.YES_NO_OPTION);
        if (confirm != JOptionPane.YES_OPTION) {
            return;
        }

        java.time.LocalDate payDate = ((Date) dcPayDate.getValue()).toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        String note = tbPayslipReferenceNote.isVisible() && !tbPayslipReferenceNote.getText().isBlank() ? tbPayslipReferenceNote.getText() : null;
        btUpdate.setEnabled(false);
        new SwingWorker<FinalisationResult, Void>() {
            @Override
            protected FinalisationResult doInBackground() {
                return finalisationController.finalisePayPeriod(DEMO_COMPANY, payDate, note);
            }

            @Override
            protected void done() {
                btUpdate.setEnabled(true);
                try {
                    FinalisationResult result = get();
                    JOptionPane.showMessageDialog(FinalisePanel.this, result.message(), "Finalise Pay Period", JOptionPane.INFORMATION_MESSAGE);
                    reload();
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(FinalisePanel.this, "Finalisation failed: " + ex.getMessage(),
                            "Finalise Pay Period", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private int currentPeriod() {
        return Integer.parseInt(lblPeriodBeingProcessed.getText());
    }
}

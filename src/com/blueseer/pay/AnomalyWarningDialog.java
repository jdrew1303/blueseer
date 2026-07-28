package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.AnomalyDTO;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * S-24 Computational Anomaly, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * Shown automatically as part of S-22's finalisation flow, before S-22's own
 * confirmation dialog - never opened independently.
 */
public class AnomalyWarningDialog extends JDialog {

    public enum Outcome { ACKNOWLEDGED, REVIEW_IN_PAY_ENTRY, CANCELLED }

    private Outcome outcome = Outcome.CANCELLED;
    private EmployeeId reviewEmployeeId;
    private final List<AnomalyDTO> anomalies;
    private JTable table;

    public AnomalyWarningDialog(Frame owner, List<AnomalyDTO> anomalies) {
        super(owner, "Computational Anomaly", true);
        this.anomalies = anomalies;
        initComponents();
        setSize(560, 300);
        setLocation(60, 80);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public EmployeeId getReviewEmployeeId() {
        return reviewEmployeeId;
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        DefaultTableModel model = new DefaultTableModel(
                new Object[] { "Employee", "This Period", "Prior Period", "% Change", "Flagged Reason" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        for (AnomalyDTO a : anomalies) {
            model.addRow(new Object[] { a.displayName(), a.thisPeriod().toPlainString(), a.priorPeriod().toPlainString(),
                    a.percentChange().toPlainString() + "%", a.flaggedReason() });
        }
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btReviewInPayEntry = new JButton("Review in Pay Entry");
        btReviewInPayEntry.setEnabled(false);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btReviewInPayEntry.setEnabled(table.getSelectedRow() >= 0);
            }
        });
        btReviewInPayEntry.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                outcome = Outcome.REVIEW_IN_PAY_ENTRY;
                reviewEmployeeId = anomalies.get(row).employeeId();
                dispose();
            }
        });
        JButton btAcknowledgeAndContinue = new JButton("Acknowledge and Continue");
        btAcknowledgeAndContinue.addActionListener(e -> {
            outcome = Outcome.ACKNOWLEDGED;
            dispose();
        });
        south.add(btReviewInPayEntry);
        south.add(btAcknowledgeAndContinue);
        add(south, BorderLayout.SOUTH);
    }
}

package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.RpnDtos.BulkRpnResultDTO;
import com.blueseer.pay.RpnDtos.RpnDiffDTO;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Window;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/** S-15 RPN Retrieval (bulk, each pay period). */
public class RpnBulkRetrievalDialog extends JDialog {

    private static final String EMPTY = "empty";
    private static final String RESULTS = "results";
    private static final String NO_CHANGES = "no-changes";

    private final IRpnGateway gateway;
    private final CompanyId companyId;

    private CardLayout cardLayout;
    private JPanel cardPanel;
    private DefaultTableModel resultModel;
    private JButton btRetrieve;
    private JButton btUpdate;
    private List<RpnDiffDTO> currentDiffs = List.of();

    public RpnBulkRetrievalDialog(Window owner, IRpnGateway gateway, CompanyId companyId) {
        super(owner, "RPN Retrieval (Bulk)", ModalityType.APPLICATION_MODAL);
        this.gateway = gateway;
        this.companyId = companyId;
        initComponents();
        setSize(560, 380);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel north = new JPanel(new BorderLayout());
        north.add(new JLabel("Retrieve the latest RPN credits/cut-off points for every active employee with a PPS number."),
                BorderLayout.CENTER);
        btRetrieve = new JButton("Retrieve");
        btRetrieve.addActionListener(e -> onRetrieve());
        JPanel retrieveRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        retrieveRow.add(btRetrieve);
        north.add(retrieveRow, BorderLayout.SOUTH);
        add(north, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(new JPanel(), EMPTY);

        resultModel = new DefaultTableModel(new Object[] {"", "Employee", "Old Credit", "New Credit", "Old Cut-Off", "New Cut-Off", "Changed?"}, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return column == 0;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                return columnIndex == 0 ? Boolean.class : String.class;
            }
        };
        JTable resultsTable = new JTable(resultModel);
        cardPanel.add(new JScrollPane(resultsTable), RESULTS);

        JPanel noChanges = new JPanel(new BorderLayout());
        noChanges.add(new JLabel("no new credits or cut off points were found for existing employees", JLabel.CENTER), BorderLayout.CENTER);
        cardPanel.add(noChanges, NO_CHANGES);

        add(cardPanel, BorderLayout.CENTER);

        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        JButton btPrintRpnDetails = new JButton("Print RPN Details");
        btPrintRpnDetails.setEnabled(false);
        btPrintRpnDetails.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "RPN Details report generated for " + currentDiffs.size() + " row(s) (no Jasper template wired for this stub report).",
                "Print RPN Details", JOptionPane.INFORMATION_MESSAGE));
        btUpdate = new JButton("Update");
        btUpdate.setEnabled(false);
        btUpdate.addActionListener(e -> onUpdate());
        JButton btClose = new JButton("Close");
        btClose.addActionListener(e -> onClose());
        south.add(btPrintRpnDetails);
        south.add(btUpdate);
        south.add(btClose);
        add(south, BorderLayout.SOUTH);

        cardLayout.show(cardPanel, EMPTY);
    }

    private void onRetrieve() {
        btRetrieve.setEnabled(false);
        SwingWorker<BulkRpnResultDTO, Void> worker = new SwingWorker<>() {
            @Override
            protected BulkRpnResultDTO doInBackground() {
                return gateway.retrieveBulkRpns(companyId);
            }

            @Override
            protected void done() {
                btRetrieve.setEnabled(true);
                try {
                    route(get());
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(RpnBulkRetrievalDialog.this, "Retrieval failed: " + ex.getMessage(),
                            "RPN Retrieval", JOptionPane.ERROR_MESSAGE);
                }
            }
        };
        worker.execute();
    }

    private void route(BulkRpnResultDTO result) {
        currentDiffs = result.diffs();
        if (result.noChangesFound()) {
            cardLayout.show(cardPanel, NO_CHANGES);
            return;
        }
        resultModel.setRowCount(0);
        for (RpnDiffDTO diff : currentDiffs) {
            resultModel.addRow(new Object[] {diff.changed(), diff.employeeId().value(), diff.oldCredit(), diff.newCredit(),
                    diff.oldCutOff(), diff.newCutOff(), diff.changed() ? "Yes" : "No"});
        }
        btUpdate.setEnabled(currentDiffs.stream().anyMatch(RpnDiffDTO::changed));
        cardLayout.show(cardPanel, RESULTS);
    }

    private void onUpdate() {
        List<RpnDiffDTO> selected = new ArrayList<>();
        for (int i = 0; i < resultModel.getRowCount(); i++) {
            if (Boolean.TRUE.equals(resultModel.getValueAt(i, 0))) {
                selected.add(currentDiffs.get(i));
            }
        }
        gateway.applyBulkRpnChanges(selected);
        JOptionPane.showMessageDialog(this, selected.size() + " employee(s) updated.", "RPN Retrieval", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }

    private void onClose() {
        boolean hasUnappliedChanges = btUpdate.isEnabled();
        if (hasUnappliedChanges) {
            int confirm = JOptionPane.showConfirmDialog(this, "Continue without applying these changes?",
                    "RPN Retrieval", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        dispose();
    }
}

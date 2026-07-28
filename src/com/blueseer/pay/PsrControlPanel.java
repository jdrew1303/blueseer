package com.blueseer.pay;

import com.blueseer.pay.PsrDtos.PsrControlRowDTO;
import com.blueseer.pay.PsrDtos.PsrSubmissionResult;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.PsrBatchId;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableModel;

/**
 * S-32 PSR Control Panel, per docs/architecture/irish-payroll-2026-screen-specs.md.
 */
public class PsrControlPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IPsrGateway gateway;

    private DefaultTableModel model;
    private JTable table;
    private JButton btSubmitSelected;
    private List<PsrControlRowDTO> rows = List.of();

    public PsrControlPanel() {
        this(new InMemoryPsrGateway(PayrollStubStore.shared(), new InMemoryEmployeeRepository(PayrollStubStore.shared()),
                new InMemoryRevenueDetailsService(PayrollStubStore.shared()), new InMemoryAdditionDeductionService(PayrollStubStore.shared())));
    }

    public PsrControlPanel(IPsrGateway gateway) {
        this.gateway = gateway;
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
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("PSR Control Panel"));
        content.setPreferredSize(new java.awt.Dimension(600, 400));

        model = new DefaultTableModel(new Object[] { "Pay Date", "Payslip/Correction Count", "Count Returned", "Status" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btSubmitSelected.setEnabled(table.getSelectedRowCount() >= 1);
            }
        });
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                maybeShowPopup(e);
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                maybeShowPopup(e);
            }
        });
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btSubmitSelected = new JButton("Submit Selected");
        btSubmitSelected.setEnabled(false);
        btSubmitSelected.addActionListener(e -> onSubmitSelected());
        south.add(btSubmitSelected);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void maybeShowPopup(MouseEvent e) {
        if (!e.isPopupTrigger()) {
            return;
        }
        int row = table.rowAtPoint(e.getPoint());
        if (row < 0) {
            return;
        }
        table.setRowSelectionInterval(row, row);
        PsrBatchId batchId = rows.get(row).batchId();

        JPopupMenu menu = new JPopupMenu();
        JMenuItem markSent = new JMenuItem("Mark all payslips as sent");
        markSent.addActionListener(a -> {
            gateway.markPsrAsSent(batchId);
            reload();
        });
        JMenuItem markNotSent = new JMenuItem("Mark all payslips as not sent");
        markNotSent.addActionListener(a -> {
            gateway.markPsrAsNotSent(batchId);
            reload();
        });
        menu.add(markSent);
        menu.add(markNotSent);
        menu.show(table, e.getX(), e.getY());
    }

    private void reload() {
        rows = gateway.getPsrControlPanelRows(DEMO_COMPANY);
        model.setRowCount(0);
        for (PsrControlRowDTO row : rows) {
            model.addRow(new Object[] { row.payDate(), row.payslipCount(), row.countReturned(), row.status() });
        }
        btSubmitSelected.setEnabled(false);
    }

    private void onSubmitSelected() {
        int[] selected = table.getSelectedRows();
        List<PsrBatchId> outstanding = new ArrayList<>();
        for (int row : selected) {
            if ("Due".equals(rows.get(row).status())) {
                outstanding.add(rows.get(row).batchId());
            }
        }
        if (outstanding.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No outstanding (Due) submissions selected.", "Submit Selected", JOptionPane.WARNING_MESSAGE);
            return;
        }
        btSubmitSelected.setEnabled(false);
        new SwingWorker<Integer, Void>() {
            @Override
            protected Integer doInBackground() {
                // Submitted in sequence, not in parallel, per the exemplar's
                // own stated procedure - one call after another, not a batch API.
                int submitted = 0;
                for (PsrBatchId id : outstanding) {
                    PsrSubmissionResult result = gateway.submitPsrBatch(id);
                    if (result.success()) {
                        submitted++;
                    }
                }
                return submitted;
            }

            @Override
            protected void done() {
                try {
                    int submitted = get();
                    JOptionPane.showMessageDialog(PsrControlPanel.this, submitted + " submission(s) filed.",
                            "Submit Selected", JOptionPane.INFORMATION_MESSAGE);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(PsrControlPanel.this, "Submission failed: " + ex.getMessage(),
                            "Submit Selected", JOptionPane.ERROR_MESSAGE);
                }
                reload();
            }
        }.execute();
    }
}

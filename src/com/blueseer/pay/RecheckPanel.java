package com.blueseer.pay;

import com.blueseer.pay.PsrDtos.PsrRecheckResultDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * S-34 Recheck Payroll Submissions, per docs/architecture/irish-payroll-2026-screen-specs.md.
 */
public class RecheckPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IPsrGateway gateway;

    private DefaultTableModel model;
    private List<PsrRecheckResultDTO> results = List.of();

    public RecheckPanel() {
        this(new InMemoryPsrGateway(PayrollStubStore.shared(), new InMemoryEmployeeRepository(PayrollStubStore.shared()),
                new InMemoryRevenueDetailsService(PayrollStubStore.shared()), new InMemoryAdditionDeductionService(PayrollStubStore.shared())));
    }

    public RecheckPanel(IPsrGateway gateway) {
        this.gateway = gateway;
        initComponents();
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Recheck Payroll Submissions"));
        content.setPreferredSize(new java.awt.Dimension(560, 350));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btRecheckAll = new JButton("Recheck All");
        btRecheckAll.addActionListener(e -> onRecheckAll(btRecheckAll));
        north.add(btRecheckAll);
        content.add(north, BorderLayout.NORTH);

        model = new DefaultTableModel(new Object[] { "Pay Date", "Previous Status", "Current Status" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setDefaultRenderer(Object.class, mismatchRenderer());
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        add(content);
    }

    private void onRecheckAll(JButton btRecheckAll) {
        btRecheckAll.setEnabled(false);
        new SwingWorker<List<PsrRecheckResultDTO>, Void>() {
            @Override
            protected List<PsrRecheckResultDTO> doInBackground() {
                return gateway.recheckSubmissions(DEMO_COMPANY);
            }

            @Override
            protected void done() {
                btRecheckAll.setEnabled(true);
                try {
                    results = get();
                    model.setRowCount(0);
                    for (PsrRecheckResultDTO r : results) {
                        model.addRow(new Object[] { r.payDate(), r.previousStatus(), r.currentStatus() });
                    }
                } catch (Exception ex) {
                    results = List.of();
                }
            }
        }.execute();
    }

    private DefaultTableCellRenderer mismatchRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                boolean mismatch = row < results.size() && !results.get(row).previousStatus().equals(results.get(row).currentStatus());
                c.setBackground(isSelected ? t.getSelectionBackground() : mismatch ? new Color(255, 230, 200) : Color.WHITE);
                return c;
            }
        };
    }
}

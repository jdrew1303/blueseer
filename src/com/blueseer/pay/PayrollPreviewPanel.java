package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.PreviewRowDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

/**
 * S-21 Payroll Preview, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * Read-only look at the in-progress period across every employee with a
 * draft pay entry. Double-clicking the Employee column opens the S-18 drill-in
 * (info dialog, per the Track A cross-panel-navigation simplification used
 * throughout this module); double-clicking any of the money columns opens
 * S-23 Payslip Workings for that specific figure, reconciling S-21's own
 * "double-click -> S-18/S-19" note with S-23's "opened from S-21's preview
 * rows" note as a row-vs-cell distinction.
 */
public class PayrollPreviewPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IPayrollPreviewController previewController;
    private final IPayslipWorkingsController workingsController;

    private PreviewTableModel tableModel;
    private JLabel lblPeriodBanner;
    private int currentPeriodNumber = 1;

    public PayrollPreviewPanel() {
        this(new InMemoryPayrollPreviewController(PayrollStubStore.shared(), PayrollEngineFactory.defaultCalculationService()),
                new InMemoryPayslipWorkingsController(PayrollStubStore.shared(), PayrollEngineFactory.defaultCalculationService()));
    }

    public PayrollPreviewPanel(IPayrollPreviewController previewController, IPayslipWorkingsController workingsController) {
        this.previewController = previewController;
        this.workingsController = workingsController;
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
        content.setBorder(BorderFactory.createTitledBorder("Payroll Preview"));
        content.setPreferredSize(new java.awt.Dimension(650, 450));

        lblPeriodBanner = new JLabel(" ");
        lblPeriodBanner.setFont(lblPeriodBanner.getFont().deriveFont(java.awt.Font.BOLD));
        content.add(lblPeriodBanner, BorderLayout.NORTH);

        tableModel = new PreviewTableModel();
        JTable table = new JTable(tableModel);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() != 2) {
                    return;
                }
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                if (col == 0) {
                    JOptionPane.showMessageDialog(PayrollPreviewPanel.this,
                            "Open Payroll > Pay Entry for " + tableModel.rows.get(row).displayName() + " to correct this period.",
                            "Correct Entry", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    PreviewRowDTO r = tableModel.rows.get(row);
                    new PayslipWorkingsDialog((java.awt.Frame) SwingUtilities.getWindowAncestor(PayrollPreviewPanel.this),
                            workingsController, r.employeeId(), currentPeriodNumber).setVisible(true);
                }
            }
        });
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btPrint = new JButton("Print");
        btPrint.addActionListener(e -> reportStub("Print"));
        JButton btCopy = new JButton("Copy");
        btCopy.addActionListener(e -> reportStub("Copy"));
        JButton btHtml = new JButton("HTML");
        btHtml.addActionListener(e -> reportStub("HTML"));
        JButton btEmail = new JButton("Email");
        btEmail.addActionListener(e -> reportStub("Email"));
        JButton btProceedToFinalise = new JButton("Proceed to Finalise");
        btProceedToFinalise.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Open Payroll > Finalise Pay Period to continue.", "Proceed to Finalise", JOptionPane.INFORMATION_MESSAGE));
        south.add(btPrint);
        south.add(btCopy);
        south.add(btHtml);
        south.add(btEmail);
        south.add(btProceedToFinalise);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void reportStub(String action) {
        JOptionPane.showMessageDialog(this, "Payroll Preview report (" + action + ") generated.", action, JOptionPane.INFORMATION_MESSAGE);
    }

    private void reload() {
        currentPeriodNumber = previewController.getCurrentPeriodNumber(DEMO_COMPANY);
        lblPeriodBanner.setText("Previewing Period " + currentPeriodNumber + " - not yet finalised");
        tableModel.setRows(previewController.getPreview(DEMO_COMPANY, currentPeriodNumber));
    }

    private final class PreviewTableModel extends AbstractTableModel {

        private List<PreviewRowDTO> rows = List.of();

        void setRows(List<PreviewRowDTO> rows) {
            this.rows = rows;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 6;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Employee";
                case 1 -> "Gross";
                case 2 -> "PAYE";
                case 3 -> "PRSI";
                case 4 -> "USC";
                default -> "Net";
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PreviewRowDTO r = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> r.displayName();
                case 1 -> r.gross();
                case 2 -> r.paye();
                case 3 -> r.prsi();
                case 4 -> r.usc();
                default -> r.net();
            };
        }
    }
}

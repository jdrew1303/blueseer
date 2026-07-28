package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.RevenueRecordDtos.RecordComparisonRowDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PayrollIds.EmployeeId;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingWorker;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * S-35 Check Revenue Record, per docs/architecture/irish-payroll-2026-screen-specs.md.
 */
public class CheckRevenueRecordPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IRevenueRecordController controller;
    private final IEmployeeRepository employeeRepository;

    private JRadioButton rbEmployer;
    private JRadioButton rbEmployee;
    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private DefaultTableModel model;
    private List<RecordComparisonRowDTO> rows = List.of();

    public CheckRevenueRecordPanel() {
        this(new InMemoryRevenueRecordController(PayrollStubStore.shared()), new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public CheckRevenueRecordPanel(IRevenueRecordController controller, IEmployeeRepository employeeRepository) {
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        initComponents();
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Check Revenue Record"));
        content.setPreferredSize(new java.awt.Dimension(600, 400));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rbEmployer = new JRadioButton("Employer", true);
        rbEmployee = new JRadioButton("Employee");
        ButtonGroup group = new ButtonGroup();
        group.add(rbEmployer);
        group.add(rbEmployee);
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new javax.swing.JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            if (!summary.isFormerEmployee()) {
                cbEmployeeSelector.addItem(summary);
            }
        }
        cbEmployeeSelector.setEnabled(false);
        rbEmployer.addActionListener(e -> cbEmployeeSelector.setEnabled(false));
        rbEmployee.addActionListener(e -> cbEmployeeSelector.setEnabled(true));
        north.add(rbEmployer);
        north.add(rbEmployee);
        north.add(cbEmployeeSelector);
        content.add(north, BorderLayout.NORTH);

        model = new DefaultTableModel(new Object[] { "Field", "Local Value", "Revenue Value", "Match?" }, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        JTable table = new JTable(model);
        table.setDefaultRenderer(Object.class, mismatchRenderer());
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btRunCheck = new JButton("Run Check");
        btRunCheck.addActionListener(e -> onRunCheck(btRunCheck));
        south.add(btRunCheck);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void onRunCheck(JButton btRunCheck) {
        EmployeeId idOrNull = null;
        if (rbEmployee.isSelected()) {
            EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
            if (selected == null) {
                return;
            }
            idOrNull = selected.id();
        }
        EmployeeId finalId = idOrNull;
        btRunCheck.setEnabled(false);
        new SwingWorker<List<RecordComparisonRowDTO>, Void>() {
            @Override
            protected List<RecordComparisonRowDTO> doInBackground() {
                return controller.compareRecord(finalId);
            }

            @Override
            protected void done() {
                btRunCheck.setEnabled(true);
                try {
                    rows = get();
                    model.setRowCount(0);
                    for (RecordComparisonRowDTO row : rows) {
                        model.addRow(new Object[] { row.field(), row.localValue(), row.revenueValue(), row.match() ? "Yes" : "No" });
                    }
                } catch (Exception ex) {
                    rows = List.of();
                }
            }
        }.execute();
    }

    private DefaultTableCellRenderer mismatchRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                boolean mismatch = row < rows.size() && !rows.get(row).match();
                c.setBackground(isSelected ? t.getSelectionBackground() : mismatch ? new Color(255, 220, 220) : Color.WHITE);
                return c;
            }
        };
    }
}

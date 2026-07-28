package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.DepartmentDTO;
import com.blueseer.pay.PayProcessingDtos.BatchSaveResult;
import com.blueseer.pay.PayProcessingDtos.PayEntryRowDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

/**
 * S-19 Quick Edit Entry, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * Grid mode over the same {@link IPayEntryController}/{@link GrossPayAssembler}
 * S-18 uses, so a value entered here and one entered via S-18 for the same
 * employee/period always agree - notes and the advanced fields are
 * deliberately absent here, matching the exemplar's own stated grid-mode
 * limitation, and require the S-18 drill-in instead.
 */
public class QuickEditPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IPayEntryController payEntryController;
    private final IDepartmentController departmentController;
    private final IPayrollCalendarController calendarController;

    private Week53Banner week53Banner;
    private JComboBox<DepartmentDTO> cbDepartmentFilter;
    private GridTableModel tableModel;

    public QuickEditPanel() {
        this(new InMemoryPayEntryController(PayrollStubStore.shared(), PayrollEngineFactory.defaultCalculationService()),
                new InMemoryDepartmentController(PayrollStubStore.shared()),
                new InMemoryPayrollCalendarController(PayrollStubStore.shared()));
    }

    public QuickEditPanel(IPayEntryController payEntryController, IDepartmentController departmentController,
            IPayrollCalendarController calendarController) {
        this.payEntryController = payEntryController;
        this.departmentController = departmentController;
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
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Quick Edit Entry"));
        content.setPreferredSize(new java.awt.Dimension(650, 450));

        week53Banner = new Week53Banner();

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Department:"));
        cbDepartmentFilter = new JComboBox<>();
        cbDepartmentFilter.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.id() == null ? value.name() : value.code() + " - " + value.name()));
        cbDepartmentFilter.addItem(new DepartmentDTO(null, "", "All Departments"));
        for (DepartmentDTO dept : departmentController.listDepartments(DEMO_COMPANY)) {
            cbDepartmentFilter.addItem(dept);
        }
        cbDepartmentFilter.addActionListener(e -> reload());
        north.add(cbDepartmentFilter);

        JPanel northWrapper = new JPanel(new BorderLayout());
        northWrapper.add(week53Banner, BorderLayout.NORTH);
        northWrapper.add(north, BorderLayout.SOUTH);
        content.add(northWrapper, BorderLayout.NORTH);

        tableModel = new GridTableModel();
        JTable table = new JTable(tableModel);
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2) {
                    int row = table.rowAtPoint(e.getPoint());
                    if (row >= 0) {
                        openDrillIn(tableModel.rows.get(row));
                    }
                }
            }
        });
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btUpdateAll = new JButton("Update All");
        btUpdateAll.addActionListener(e -> onUpdateAll());
        south.add(btUpdateAll);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void reload() {
        DepartmentDTO filter = (DepartmentDTO) cbDepartmentFilter.getSelectedItem();
        var filterId = filter == null ? null : filter.id();
        int currentPeriod = payEntryController.getCurrentPeriodNumber(DEMO_COMPANY);
        week53Banner.setWeek53(calendarController.isWeek53Period(DEMO_COMPANY, currentPeriod));
        tableModel.setRows(payEntryController.loadQuickEditGrid(DEMO_COMPANY, currentPeriod, filterId));
    }

    private void openDrillIn(PayEntryRowDTO row) {
        JOptionPane.showMessageDialog(this,
                "Open Payroll > Pay Entry and select " + row.displayName() + " for advanced fields and notes.",
                "Advanced Entry", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onUpdateAll() {
        BatchSaveResult result = payEntryController.saveQuickEditBatch(tableModel.rows);
        JOptionPane.showMessageDialog(this, result.savedCount() + " entries updated.", "Update All", JOptionPane.INFORMATION_MESSAGE);
        reload();
    }

    private final class GridTableModel extends AbstractTableModel {

        private List<PayEntryRowDTO> rows = new ArrayList<>();

        void setRows(List<PayEntryRowDTO> rows) {
            this.rows = new ArrayList<>(rows);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Employee";
                case 1 -> "Hours";
                case 2 -> "Basic Pay";
                default -> "Gross Preview";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 1 || columnIndex == 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            PayEntryRowDTO row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.displayName();
                case 1 -> row.hours() == null ? "" : row.hours().toPlainString();
                case 2 -> row.basicPay() == null ? "" : row.basicPay().toPlainString();
                default -> row.grossPayPreview() == null ? "" : row.grossPayPreview().toPlainString();
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            PayEntryRowDTO row = rows.get(rowIndex);
            BigDecimal parsed = parseOrNull(value);
            PayEntryRowDTO updated = columnIndex == 1
                    ? new PayEntryRowDTO(row.employeeId(), row.displayName(), parsed, row.basicPay(), row.grossPayPreview())
                    : new PayEntryRowDTO(row.employeeId(), row.displayName(), row.hours(), parsed, row.grossPayPreview());
            rows.set(rowIndex, updated);
            fireTableRowsUpdated(rowIndex, rowIndex);
        }

        private BigDecimal parseOrNull(Object value) {
            try {
                String text = String.valueOf(value);
                return text.isBlank() ? null : new BigDecimal(text.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
    }
}

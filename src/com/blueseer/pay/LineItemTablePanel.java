package com.blueseer.pay;

import com.blueseer.pay.PayrollIds.EmployeeId;
import com.blueseer.pay.PayrollIds.LineItemId;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

/**
 * Shared table + Add/Remove/Update row implementation for S-07 (Additions)
 * and S-08 (Deductions), per docs/architecture/irish-payroll-2026-screen-specs.md.
 * The two tabs differ only in the boolean column label ("Taxable?" vs
 * "Pre-Tax?"), whether the once-off reminder banner shows, and whether the
 * ASC special case (S-08 only) is active - everything else is identical
 * table/row behaviour, so one implementation serves both.
 */
public class LineItemTablePanel extends JPanel {

    private final IAdditionDeductionService service;
    private final Supplier<EmployeeId> employeeIdSupplier;
    private final boolean ascSpecialCase;

    private final RowTableModel tableModel;
    private JTable table;
    private JButton btRemoveRow;

    public LineItemTablePanel(IAdditionDeductionService service, Supplier<EmployeeId> employeeIdSupplier,
            String flagColumnLabel, boolean showOnceOffReminder, boolean ascSpecialCase) {
        this.service = service;
        this.employeeIdSupplier = employeeIdSupplier;
        this.ascSpecialCase = ascSpecialCase;
        this.tableModel = new RowTableModel(flagColumnLabel, ascSpecialCase);
        initComponents(showOnceOffReminder);
    }

    public List<LineItemRow> getRows() {
        return new ArrayList<>(tableModel.rows);
    }

    public void setRows(List<LineItemRow> rows) {
        tableModel.rows.clear();
        tableModel.rows.addAll(rows);
        tableModel.fireTableDataChanged();
    }

    /** Lets the S-04 shell's shared top btUpdate also trigger this tab's own save, per S-04's "one Update per tab" framing. */
    public void triggerSave() {
        onUpdate();
    }

    private void initComponents(boolean showOnceOffReminder) {
        setLayout(new BorderLayout());

        if (showOnceOffReminder) {
            JLabel lblOnceOffReminder = new JLabel(
                    "If this is a once off addition, remember to revert the amount entered to zero in the next pay period");
            lblOnceOffReminder.setFont(lblOnceOffReminder.getFont().deriveFont(java.awt.Font.ITALIC, 11f));
            add(lblOnceOffReminder, BorderLayout.NORTH);
        }

        table = new JTable(tableModel);
        table.getColumnModel().getColumn(1).setCellEditor(new javax.swing.DefaultCellEditor(new JCheckBox()));
        table.getColumnModel().getColumn(3).setCellRenderer(endDateRenderer());
        if (ascSpecialCase) {
            table.getColumnModel().getColumn(2).setCellRenderer(amountRenderer());
            table.getColumnModel().getColumn(4).setCellRenderer(optionsRenderer());
        }
        table.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                int row = table.rowAtPoint(e.getPoint());
                int col = table.columnAtPoint(e.getPoint());
                if (row < 0) {
                    return;
                }
                if (col == 3) {
                    openEndDateDialog(row);
                } else if (ascSpecialCase && col == 4 && tableModel.rows.get(row).isAsc()) {
                    openAscOptionsDialog(row);
                }
            }
        });
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btRemoveRow.setEnabled(table.getSelectedRow() >= 0);
            }
        });
        add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btAddRow = new JButton("+");
        btAddRow.addActionListener(e -> {
            int newRow = tableModel.addRow();
            table.editCellAt(newRow, 0);
            table.getEditorComponent().requestFocus();
        });
        btRemoveRow = new JButton("-");
        btRemoveRow.setEnabled(false);
        btRemoveRow.addActionListener(e -> onRemoveRow());
        JButton btUpdate = new JButton("Update");
        btUpdate.addActionListener(e -> onUpdate());
        south.add(btAddRow);
        south.add(btRemoveRow);
        south.add(btUpdate);
        add(south, BorderLayout.SOUTH);
    }

    private void onRemoveRow() {
        int row = table.getSelectedRow();
        if (row < 0) {
            return;
        }
        LineItemRow line = tableModel.rows.get(row);
        boolean hasAmount = line.amount != null && line.amount.signum() != 0;
        if (hasAmount) {
            int confirm = JOptionPane.showConfirmDialog(this, "Remove this line?", "Confirm Remove", JOptionPane.YES_NO_OPTION);
            if (confirm != JOptionPane.YES_OPTION) {
                return;
            }
        }
        tableModel.rows.remove(row);
        tableModel.fireTableDataChanged();
    }

    private void onUpdate() {
        EmployeeId employeeId = employeeIdSupplier.get();
        if (employeeId == null) {
            return;
        }
        if (ascSpecialCase) {
            List<EmployeeDtos.DeductionLineDTO> lines = new ArrayList<>();
            for (LineItemRow row : tableModel.rows) {
                lines.add(new EmployeeDtos.DeductionLineDTO(row.id, row.description, row.flag,
                        row.isAsc() ? null : row.amount, row.endDate, row.ascGroup, row.ascOverrideAmount, row.ascOverridePercentage));
            }
            service.saveDeductions(employeeId, lines);
        } else {
            List<EmployeeDtos.AdditionLineDTO> lines = new ArrayList<>();
            for (LineItemRow row : tableModel.rows) {
                lines.add(new EmployeeDtos.AdditionLineDTO(row.id, row.description, row.flag, row.amount, row.endDate));
            }
            service.saveAdditions(employeeId, lines);
        }
        JOptionPane.showMessageDialog(this, "Saved.", "Update", JOptionPane.INFORMATION_MESSAGE);
    }

    private void openEndDateDialog(int row) {
        LineItemRow line = tableModel.rows.get(row);
        EmployeeId employeeId = employeeIdSupplier.get();
        if (employeeId == null) {
            return;
        }
        AdditionDeductionEndDateDialog dialog = new AdditionDeductionEndDateDialog(
                ownerFrame(), service, employeeId, line.id, line.description.isEmpty() ? "(new line)" : line.description, line.endDate);
        dialog.setVisible(true);
        if (dialog.wasSaved()) {
            line.endDate = dialog.getResultEndDate();
            tableModel.fireTableRowsUpdated(row, row);
        }
    }

    private void openAscOptionsDialog(int row) {
        LineItemRow line = tableModel.rows.get(row);
        AscOptionsDialog dialog = new AscOptionsDialog(ownerFrame(),
                line.ascGroup == ITaxYearRules.AscGroup.SINGLE_SCHEME, line.ascOverrideAmount, line.ascOverridePercentage);
        dialog.setVisible(true);
        if (dialog.wasSaved()) {
            line.ascGroup = dialog.getResultGroup();
            line.ascOverrideAmount = dialog.getResultOverrideAmount();
            line.ascOverridePercentage = dialog.getResultOverridePercentage();
            tableModel.fireTableRowsUpdated(row, row);
        }
    }

    private Frame ownerFrame() {
        return (Frame) SwingUtilities.getWindowAncestor(this);
    }

    private DefaultTableCellRenderer endDateRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                LocalDate endDate = tableModel.rows.get(row).endDate;
                String text = endDate == null ? "" : endDate.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
                return super.getTableCellRendererComponent(t, text, isSelected, hasFocus, row, column);
            }
        };
    }

    private DefaultTableCellRenderer amountRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                LineItemRow line = tableModel.rows.get(row);
                if (line.isAsc()) {
                    JLabel label = (JLabel) super.getTableCellRendererComponent(t, "Auto-calculated", isSelected, hasFocus, row, column);
                    label.setForeground(Color.GRAY);
                    label.setFont(label.getFont().deriveFont(java.awt.Font.ITALIC));
                    return label;
                }
                return super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
            }
        };
    }

    private DefaultTableCellRenderer optionsRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                LineItemRow line = tableModel.rows.get(row);
                return super.getTableCellRendererComponent(t, line.isAsc() ? "Edit..." : "", isSelected, hasFocus, row, column);
            }
        };
    }

    private final class RowTableModel extends AbstractTableModel {

        private final List<LineItemRow> rows = new ArrayList<>();
        private final String flagColumnLabel;
        private final boolean ascSpecialCase;
        private long nextRowId = -1;

        RowTableModel(String flagColumnLabel, boolean ascSpecialCase) {
            this.flagColumnLabel = flagColumnLabel;
            this.ascSpecialCase = ascSpecialCase;
        }

        int addRow() {
            LineItemRow row = new LineItemRow(new LineItemId(nextRowId--));
            rows.add(row);
            fireTableRowsInserted(rows.size() - 1, rows.size() - 1);
            return rows.size() - 1;
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return ascSpecialCase ? 5 : 4;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Description";
                case 1 -> flagColumnLabel;
                case 2 -> "Amount";
                case 3 -> "End Date";
                default -> "Options";
            };
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            return columnIndex == 1 ? Boolean.class : String.class;
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            if (columnIndex == 2 && ascSpecialCase && rows.get(rowIndex).isAsc()) {
                return false;
            }
            return columnIndex == 0 || columnIndex == 1 || columnIndex == 2;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            LineItemRow row = rows.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> row.description;
                case 1 -> row.flag;
                case 2 -> row.amount == null ? "" : row.amount.toPlainString();
                default -> "";
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            LineItemRow row = rows.get(rowIndex);
            switch (columnIndex) {
                case 0 -> row.description = String.valueOf(value);
                case 1 -> row.flag = Boolean.TRUE.equals(value);
                case 2 -> {
                    try {
                        row.amount = new BigDecimal(String.valueOf(value));
                    } catch (NumberFormatException ignored) {
                        // leave amount unchanged on unparsable input
                    }
                }
                default -> {
                }
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}

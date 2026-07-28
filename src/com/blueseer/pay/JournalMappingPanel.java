package com.blueseer.pay;

import com.blueseer.pay.JournalDtos.AccountingTarget;
import com.blueseer.pay.JournalDtos.JournalMappingRowDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
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
 * S-62 Payroll Journal Mapping. The GL Account Code column uses a plain text
 * cell editor for every target, including {@code NATIVE_BLUESEER_GL} - the
 * spec's chart-of-accounts-backed combo editor for that case needs a live
 * {@code com.blueseer.fgl} query this Track A stub doesn't wire up, so the
 * account code is free-text here too rather than fabricating chart-of-
 * accounts data.
 */
public class JournalMappingPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IJournalController controller;

    private JComboBox<String> cbAccountingTarget;
    private MappingTableModel tableModel;
    private JTable table;
    private JButton btSaveMapping;

    public JournalMappingPanel() {
        this(new InMemoryJournalController(PayrollStubStore.shared()));
    }

    public JournalMappingPanel(IJournalController controller) {
        this.controller = controller;
        initComponents();
        loadMapping();
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("Payroll Journal Mapping"));
        content.setPreferredSize(new Dimension(560, 380));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Accounting Target:"));
        cbAccountingTarget = new JComboBox<>();
        for (AccountingTarget t : AccountingTarget.values()) {
            cbAccountingTarget.addItem(t.label());
        }
        cbAccountingTarget.addActionListener(e -> loadMapping());
        north.add(cbAccountingTarget);
        content.add(north, BorderLayout.NORTH);

        tableModel = new MappingTableModel();
        table = new JTable(tableModel);
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btSaveMapping = new JButton("Save Mapping");
        btSaveMapping.addActionListener(e -> onSave());
        south.add(btSaveMapping);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private AccountingTarget selectedTarget() {
        return AccountingTarget.fromLabel(String.valueOf(cbAccountingTarget.getSelectedItem()));
    }

    private void loadMapping() {
        if (tableModel == null) {
            return;
        }
        tableModel.setRows(controller.getMapping(DEMO_COMPANY, selectedTarget()));
    }

    private void onSave() {
        SaveResult result = controller.saveMapping(DEMO_COMPANY, selectedTarget(), tableModel.getRows());
        JOptionPane.showMessageDialog(this,
                result.detailMessageOrNull() == null ? "Saved." : result.detailMessageOrNull(),
                "Payroll Journal Mapping", JOptionPane.INFORMATION_MESSAGE);
    }

    private static final class MappingTableModel extends AbstractTableModel {

        private final List<JournalMappingRowDTO> rows = new ArrayList<>();

        void setRows(List<JournalMappingRowDTO> newRows) {
            rows.clear();
            rows.addAll(newRows);
            fireTableDataChanged();
        }

        List<JournalMappingRowDTO> getRows() {
            return new ArrayList<>(rows);
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return 2;
        }

        @Override
        public String getColumnName(int column) {
            return column == 0 ? "Payroll Category" : "GL Account Code";
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 1;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            JournalMappingRowDTO row = rows.get(rowIndex);
            return columnIndex == 0 ? row.payrollCategory() : row.glAccountCode();
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            if (columnIndex == 1) {
                JournalMappingRowDTO row = rows.get(rowIndex);
                rows.set(rowIndex, new JournalMappingRowDTO(row.payrollCategory(), String.valueOf(value)));
                fireTableCellUpdated(rowIndex, columnIndex);
            }
        }
    }
}

package com.blueseer.pay;

import com.blueseer.pay.PayProcessingDtos.CalendarPeriodDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.time.format.DateTimeFormatter;
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
import javax.swing.table.DefaultTableCellRenderer;

/**
 * S-17 Payroll Calendar, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * Read-only - the current period's row is highlighted, per the spec's custom
 * {@code TableCellRenderer} requirement.
 */
public class PayrollCalendarPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int TAX_YEAR = 2026;

    private final IPayrollCalendarController controller;

    private JComboBox<PayFrequency> cbPayFrequency;
    private CalendarTableModel tableModel;

    public PayrollCalendarPanel() {
        this(new InMemoryPayrollCalendarController(PayrollStubStore.shared()));
    }

    public PayrollCalendarPanel(IPayrollCalendarController controller) {
        this.controller = controller;
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
        content.setBorder(BorderFactory.createTitledBorder("Payroll Calendar"));
        content.setPreferredSize(new java.awt.Dimension(480, 500));

        List<PayFrequency> frequenciesInUse = controller.getFrequenciesInUse(DEMO_COMPANY);
        if (frequenciesInUse.size() > 1) {
            JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
            north.add(new JLabel("Pay Frequency:"));
            cbPayFrequency = new JComboBox<>(frequenciesInUse.toArray(new PayFrequency[0]));
            cbPayFrequency.addActionListener(e -> reload());
            north.add(cbPayFrequency);
            content.add(north, BorderLayout.NORTH);
        }

        tableModel = new CalendarTableModel();
        JTable table = new JTable(tableModel);
        table.setDefaultRenderer(Object.class, currentPeriodRenderer());
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btPrint = new JButton("Print");
        btPrint.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "Payroll Calendar sent to printer.", "Print", JOptionPane.INFORMATION_MESSAGE));
        south.add(btPrint);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void reload() {
        PayFrequency freq = cbPayFrequency != null
                ? (PayFrequency) cbPayFrequency.getSelectedItem()
                : controller.getFrequenciesInUse(DEMO_COMPANY).get(0);
        tableModel.setPeriods(controller.getCalendar(DEMO_COMPANY, freq, TAX_YEAR));
    }

    private DefaultTableCellRenderer currentPeriodRenderer() {
        return new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
                Component c = super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
                boolean current = tableModel.periods.get(row).isCurrentPeriod();
                c.setBackground(isSelected ? t.getSelectionBackground() : current ? new Color(255, 245, 200) : Color.WHITE);
                return c;
            }
        };
    }

    private final class CalendarTableModel extends AbstractTableModel {

        private List<CalendarPeriodDTO> periods = List.of();

        void setPeriods(List<CalendarPeriodDTO> periods) {
            this.periods = periods;
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return periods.size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Period No";
                case 1 -> "From";
                default -> "To";
            };
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            CalendarPeriodDTO p = periods.get(rowIndex);
            return switch (columnIndex) {
                case 0 -> p.periodNumber();
                case 1 -> p.from().format(DATE_FMT);
                default -> p.to().format(DATE_FMT);
            };
        }
    }
}

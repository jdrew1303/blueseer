package com.blueseer.pay;

import com.blueseer.pay.BikDtos.BikLoanDTO;
import com.blueseer.pay.BikDtos.BikLoanSubPeriodDTO;
import com.blueseer.pay.BikDtos.BikPreviewDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.AbstractTableModel;

/** S-54 BIK - Preferential Loans. */
public class BikLoanPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IBikController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JComboBox<String> cbLoanCategory;
    private SubPeriodTableModel tableModel;
    private JTable table;
    private JButton btRemoveSubPeriod;
    private JCheckBox cbJointLoan;
    private JCheckBox cbMarriedOrCivilPartner;
    private JLabel lblEmployeeShareLabel;
    private JTextField tbEmployeeSharePercentage;
    private JLabel lblAnnualTaxableBenefit;
    private JButton btSaveLoanEntry;

    public BikLoanPanel() {
        this(new InMemoryBikController(PayrollStubStore.shared(), new InMemoryAdditionDeductionService(PayrollStubStore.shared())),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public BikLoanPanel(IBikController controller, IEmployeeRepository employeeRepository) {
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshEmployees();
        recompute();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshEmployees();
        }
    }

    private void initComponents() {
        JPanel content = new JPanel(new BorderLayout());
        content.setBorder(BorderFactory.createTitledBorder("BIK - Preferential Loans"));
        content.setPreferredSize(new Dimension(620, 480));

        JPanel north = new JPanel(new FlowLayout(FlowLayout.LEFT));
        north.add(new JLabel("Employee:"));
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        cbEmployeeSelector.addActionListener(e -> recompute());
        north.add(cbEmployeeSelector);
        north.add(new JLabel("Loan Category:"));
        cbLoanCategory = new JComboBox<>(new String[] {"Home Loan", "Other"});
        cbLoanCategory.addActionListener(e -> recompute());
        north.add(cbLoanCategory);
        content.add(north, BorderLayout.NORTH);

        tableModel = new SubPeriodTableModel();
        table = new JTable(tableModel);
        tableModel.addTableModelListener(e -> recompute());
        table.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                btRemoveSubPeriod.setEnabled(table.getSelectedRow() >= 0);
            }
        });
        content.add(new JScrollPane(table), BorderLayout.CENTER);

        JPanel tableButtons = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btAddSubPeriod = new JButton("+ Sub-Period");
        btAddSubPeriod.addActionListener(e -> tableModel.addRow());
        btRemoveSubPeriod = new JButton("- Remove");
        btRemoveSubPeriod.setEnabled(false);
        btRemoveSubPeriod.addActionListener(e -> {
            int row = table.getSelectedRow();
            if (row >= 0) {
                tableModel.removeRow(row);
                recompute();
            }
        });
        tableButtons.add(btAddSubPeriod);
        tableButtons.add(btRemoveSubPeriod);

        JPanel south = new JPanel();
        south.setLayout(new javax.swing.BoxLayout(south, javax.swing.BoxLayout.Y_AXIS));
        south.add(tableButtons);

        JPanel jointRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        cbJointLoan = new JCheckBox("Joint Loan with Non-Employee");
        cbMarriedOrCivilPartner = new JCheckBox("Married / Civil Partner");
        cbMarriedOrCivilPartner.setVisible(false);
        lblEmployeeShareLabel = new JLabel("Employee Share %:");
        lblEmployeeShareLabel.setVisible(false);
        tbEmployeeSharePercentage = new JTextField("0.50", 6);
        tbEmployeeSharePercentage.setVisible(false);
        cbJointLoan.addActionListener(e -> {
            boolean joint = cbJointLoan.isSelected();
            cbMarriedOrCivilPartner.setVisible(joint);
            boolean showShare = joint && !cbMarriedOrCivilPartner.isSelected();
            lblEmployeeShareLabel.setVisible(showShare);
            tbEmployeeSharePercentage.setVisible(showShare);
            recompute();
        });
        cbMarriedOrCivilPartner.addActionListener(e -> {
            boolean showShare = cbJointLoan.isSelected() && !cbMarriedOrCivilPartner.isSelected();
            lblEmployeeShareLabel.setVisible(showShare);
            tbEmployeeSharePercentage.setVisible(showShare);
            recompute();
        });
        jointRow.add(cbJointLoan);
        jointRow.add(cbMarriedOrCivilPartner);
        jointRow.add(lblEmployeeShareLabel);
        jointRow.add(tbEmployeeSharePercentage);
        south.add(jointRow);

        JPanel resultRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        resultRow.add(new JLabel("Annual Taxable Benefit:"));
        lblAnnualTaxableBenefit = new JLabel("-");
        resultRow.add(lblAnnualTaxableBenefit);
        south.add(resultRow);

        JPanel saveRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btSaveLoanEntry = new JButton("Save Loan Entry");
        btSaveLoanEntry.addActionListener(e -> onSave());
        saveRow.add(btSaveLoanEntry);
        south.add(saveRow);

        content.add(south, BorderLayout.SOUTH);
        add(content);
    }

    private void refreshEmployees() {
        EmployeeSummaryDTO previous = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        cbEmployeeSelector.removeAllItems();
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            cbEmployeeSelector.addItem(summary);
        }
        if (previous != null) {
            cbEmployeeSelector.setSelectedItem(previous);
        }
    }

    private BikLoanDTO currentDraft(EmployeeSummaryDTO selected) {
        String category = "Home Loan".equals(cbLoanCategory.getSelectedItem()) ? "HOME_LOAN" : "OTHER";
        BigDecimal sharePercentage;
        try {
            sharePercentage = new BigDecimal(tbEmployeeSharePercentage.getText().trim());
        } catch (Exception ex) {
            sharePercentage = BigDecimal.ZERO;
        }
        return new BikLoanDTO(selected.id(), category, tableModel.toSubPeriods(),
                cbJointLoan.isSelected(), cbMarriedOrCivilPartner.isSelected(), sharePercentage);
    }

    private void recompute() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        BikPreviewDTO preview = controller.previewLoanBik(currentDraft(selected));
        lblAnnualTaxableBenefit.setText(preview.finalBikChargeable().toPlainString());
    }

    private void onSave() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        SaveResult result = controller.saveLoanEntry(currentDraft(selected));
        JOptionPane.showMessageDialog(this,
                result.wasCreate() ? "Loan entry saved and scheduled as a recurring addition."
                        : (result.detailMessageOrNull() == null ? "Save failed." : result.detailMessageOrNull()),
                "BIK - Preferential Loans", JOptionPane.INFORMATION_MESSAGE);
    }

    private static final class SubPeriodTableModel extends AbstractTableModel {

        private final List<BigDecimal[]> balanceAndRate = new ArrayList<>();
        private final List<Integer> days = new ArrayList<>();
        private final List<BigDecimal> interestPaid = new ArrayList<>();
        private final List<BigDecimal> rates = new ArrayList<>();

        void addRow() {
            balanceAndRate.add(new BigDecimal[] {BigDecimal.ZERO});
            days.add(365);
            rates.add(BigDecimal.ZERO);
            interestPaid.add(BigDecimal.ZERO);
            int row = getRowCount() - 1;
            fireTableRowsInserted(row, row);
        }

        void removeRow(int row) {
            balanceAndRate.remove(row);
            days.remove(row);
            rates.remove(row);
            interestPaid.remove(row);
            fireTableDataChanged();
        }

        List<BikLoanSubPeriodDTO> toSubPeriods() {
            List<BikLoanSubPeriodDTO> result = new ArrayList<>();
            for (int i = 0; i < getRowCount(); i++) {
                result.add(new BikLoanSubPeriodDTO(balanceAndRate.get(i)[0], days.get(i), rates.get(i), interestPaid.get(i)));
            }
            return result;
        }

        @Override
        public int getRowCount() {
            return days.size();
        }

        @Override
        public int getColumnCount() {
            return 4;
        }

        @Override
        public String getColumnName(int column) {
            return switch (column) {
                case 0 -> "Opening Balance";
                case 1 -> "Days";
                case 2 -> "Actual Rate";
                default -> "Actual Interest Paid";
            };
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return true;
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            return switch (columnIndex) {
                case 0 -> balanceAndRate.get(rowIndex)[0].toPlainString();
                case 1 -> String.valueOf(days.get(rowIndex));
                case 2 -> rates.get(rowIndex).toPlainString();
                default -> interestPaid.get(rowIndex).toPlainString();
            };
        }

        @Override
        public void setValueAt(Object value, int rowIndex, int columnIndex) {
            String text = String.valueOf(value).trim();
            try {
                switch (columnIndex) {
                    case 0 -> balanceAndRate.get(rowIndex)[0] = new BigDecimal(text);
                    case 1 -> days.set(rowIndex, Integer.parseInt(text));
                    case 2 -> rates.set(rowIndex, new BigDecimal(text));
                    default -> interestPaid.set(rowIndex, new BigDecimal(text));
                }
            } catch (NumberFormatException ignored) {
                // leave value unchanged on unparsable input
            }
            fireTableCellUpdated(rowIndex, columnIndex);
        }
    }
}

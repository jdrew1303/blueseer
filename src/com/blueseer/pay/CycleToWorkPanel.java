package com.blueseer.pay;

import com.blueseer.pay.CycleToWorkDtos.CycleEligibilityDTO;
import com.blueseer.pay.CycleToWorkDtos.CycleToWorkDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.text.DateFormatter;

/**
 * S-27 Cycle to Work Scheme, per docs/architecture/irish-payroll-2026-screen-specs.md.
 */
public class CycleToWorkPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final ICycleToWorkController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JFormattedTextField tbBenefitValue;
    private JCheckBox cbIsElectricBike;
    private JFormattedTextField dcSacrificeStartDate;
    private JLabel lblExemptionLimit;
    private JLabel lblEligibleThisCycle;
    private JFormattedTextField tbSalaryForgonePerPeriod;

    public CycleToWorkPanel() {
        this(new InMemoryCycleToWorkController(PayrollStubStore.shared(), new InMemoryAdditionDeductionService(PayrollStubStore.shared())),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public CycleToWorkPanel(ICycleToWorkController controller, IEmployeeRepository employeeRepository) {
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshEmployeeSelector();
    }

    @Override
    public void setVisible(boolean visible) {
        super.setVisible(visible);
        if (visible) {
            refreshEmployeeSelector();
        }
    }

    private void initComponents() {
        JPanel panel = new JPanel();
        panel.setBorder(BorderFactory.createTitledBorder("Cycle to Work Scheme"));
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblEmployee = new JLabel("Employee:");
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        cbEmployeeSelector.addActionListener(e -> refreshEligibility());

        JLabel lblBenefitValue = new JLabel("Benefit Value:");
        tbBenefitValue = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        tbBenefitValue.setColumns(10);

        cbIsElectricBike = new JCheckBox("Electric Bike");
        cbIsElectricBike.addActionListener(e -> refreshEligibility());

        JLabel lblSacrificeStart = new JLabel("Sacrifice Start Date:");
        dcSacrificeStartDate = dateField();

        JLabel lblExemptionLimitLabel = new JLabel("Exemption Limit:");
        lblExemptionLimit = new JLabel("-");

        JLabel lblEligibleLabel = new JLabel("Eligible This Cycle:");
        lblEligibleThisCycle = new JLabel("-");

        JLabel lblSalaryForgone = new JLabel("Salary Forgone/Period:");
        tbSalaryForgonePerPeriod = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        tbSalaryForgonePerPeriod.setColumns(10);

        JButton btSaveSacrificeArrangement = new JButton("Save Sacrifice Arrangement");
        btSaveSacrificeArrangement.addActionListener(e -> onSave());

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblEmployee).addComponent(lblBenefitValue).addComponent(lblSacrificeStart)
                                .addComponent(lblExemptionLimitLabel).addComponent(lblEligibleLabel).addComponent(lblSalaryForgone))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(cbEmployeeSelector).addComponent(tbBenefitValue).addComponent(dcSacrificeStartDate)
                                .addComponent(lblExemptionLimit).addComponent(lblEligibleThisCycle).addComponent(tbSalaryForgonePerPeriod)))
                .addComponent(cbIsElectricBike)
                .addComponent(btSaveSacrificeArrangement));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmployee).addComponent(cbEmployeeSelector))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblBenefitValue).addComponent(tbBenefitValue))
                .addComponent(cbIsElectricBike)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblSacrificeStart).addComponent(dcSacrificeStartDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblExemptionLimitLabel).addComponent(lblExemptionLimit))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEligibleLabel).addComponent(lblEligibleThisCycle))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblSalaryForgone).addComponent(tbSalaryForgonePerPeriod))
                .addComponent(btSaveSacrificeArrangement));

        add(panel);
    }

    private JFormattedTextField dateField() {
        SimpleDateFormat fmt = new SimpleDateFormat("dd/MM/yyyy");
        fmt.setLenient(false);
        DateFormatter formatter = new DateFormatter(fmt);
        formatter.setAllowsInvalid(true);
        return new JFormattedTextField(formatter);
    }

    private void refreshEmployeeSelector() {
        cbEmployeeSelector.removeAllItems();
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            if (!summary.isFormerEmployee()) {
                cbEmployeeSelector.addItem(summary);
            }
        }
        refreshEligibility();
    }

    private void refreshEligibility() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            lblExemptionLimit.setText("-");
            lblEligibleThisCycle.setText("-");
            return;
        }
        CycleEligibilityDTO eligibility = controller.checkEligibility(selected.id());
        lblExemptionLimit.setText(CycleToWorkCalculator.exemptionLimit(cbIsElectricBike.isSelected()).toPlainString());
        lblEligibleThisCycle.setText(eligibility.eligibleThisCycle() ? "Yes"
                : "No (last arrangement: " + eligibility.lastArrangementDateOrNull() + ")");
    }

    private void onSave() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        LocalDate startDate = dcSacrificeStartDate.getValue() instanceof Date d
                ? d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate() : LocalDate.now();
        CycleToWorkDTO draft = new CycleToWorkDTO(selected.id(), asBigDecimal(tbBenefitValue.getValue()),
                cbIsElectricBike.isSelected(), startDate, asBigDecimal(tbSalaryForgonePerPeriod.getValue()));
        SaveResult result = controller.saveSacrificeArrangement(draft);
        if (!result.wasCreate() && result.detailMessageOrNull() != null && result.detailMessageOrNull().startsWith("Not eligible")) {
            JOptionPane.showMessageDialog(this, result.detailMessageOrNull(), "Save Sacrifice Arrangement", JOptionPane.WARNING_MESSAGE);
            return;
        }
        JOptionPane.showMessageDialog(this, "Sacrifice arrangement saved.", "Save Sacrifice Arrangement", JOptionPane.INFORMATION_MESSAGE);
        refreshEligibility();
    }

    private static BigDecimal asBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return new BigDecimal(value.toString());
    }
}

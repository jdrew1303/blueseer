package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.ParentingBenefitDtos.ParentingBenefitDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
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
import javax.swing.JTextField;
import javax.swing.text.DateFormatter;

/** S-58 Parenting Benefits (Maternity/Paternity/Parent's). */
public class ParentingBenefitPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");

    private final IParentingBenefitController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JComboBox<String> cbBenefitType;
    private JFormattedTextField dcLeaveStartDate;
    private JFormattedTextField dcLeaveEndDate;
    private JCheckBox cbReportedToRevenue;
    private JButton btRequestUpdatedRpn;
    private JTextField tbEmployerTopUpAmountPerPeriod;
    private JButton btSaveLeaveRecord;

    public ParentingBenefitPanel() {
        this(new InMemoryParentingBenefitController(PayrollStubStore.shared(), new InMemoryAdditionDeductionService(PayrollStubStore.shared())),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public ParentingBenefitPanel(IParentingBenefitController controller, IEmployeeRepository employeeRepository) {
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshEmployees();
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
        content.setBorder(BorderFactory.createTitledBorder("Parenting Benefits"));
        content.setPreferredSize(new Dimension(560, 340));

        JPanel form = new JPanel();
        GroupLayout layout = new GroupLayout(form);
        form.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblEmployee = new JLabel("Employee:");
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));

        JLabel lblBenefitType = new JLabel("Benefit Type:");
        cbBenefitType = new JComboBox<>(new String[] {"Maternity", "Paternity", "Parent's", "Adoptive", "Health-and-Safety"});

        JLabel lblStart = new JLabel("Leave Start Date:");
        dcLeaveStartDate = newDateField();

        JLabel lblEnd = new JLabel("Leave End Date:");
        dcLeaveEndDate = newDateField();

        JLabel lblReported = new JLabel("Reported to Revenue:");
        cbReportedToRevenue = new JCheckBox();
        cbReportedToRevenue.addActionListener(e -> btRequestUpdatedRpn.setEnabled(cbReportedToRevenue.isSelected()));

        JLabel lblTopUp = new JLabel("Employer Top-Up Amount Per Period:");
        tbEmployerTopUpAmountPerPeriod = new JTextField("0", 10);

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblEmployee).addComponent(lblBenefitType).addComponent(lblStart)
                                .addComponent(lblEnd).addComponent(lblReported).addComponent(lblTopUp))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(cbEmployeeSelector).addComponent(cbBenefitType).addComponent(dcLeaveStartDate)
                                .addComponent(dcLeaveEndDate).addComponent(cbReportedToRevenue).addComponent(tbEmployerTopUpAmountPerPeriod))));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmployee).addComponent(cbEmployeeSelector))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblBenefitType).addComponent(cbBenefitType))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblStart).addComponent(dcLeaveStartDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEnd).addComponent(dcLeaveEndDate))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblReported).addComponent(cbReportedToRevenue))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblTopUp).addComponent(tbEmployerTopUpAmountPerPeriod)));

        content.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.setLayout(new javax.swing.BoxLayout(south, javax.swing.BoxLayout.Y_AXIS));

        JPanel noteRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        JLabel lblNoCalculationNote = new JLabel(
                "Revenue collects tax on this benefit via adjusted RPN credits - no amount is added to gross pay for the benefit itself.");
        lblNoCalculationNote.setFont(lblNoCalculationNote.getFont().deriveFont(Font.ITALIC, 11f));
        noteRow.add(lblNoCalculationNote);
        south.add(noteRow);

        JPanel buttonRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        btRequestUpdatedRpn = new JButton("Request Updated RPN");
        btRequestUpdatedRpn.setEnabled(false);
        btRequestUpdatedRpn.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "RPN retrieval (S-14/S-15) is not built in this Track A build yet - request the updated RPN through Revenue's ROS system directly for now.",
                "Request Updated RPN", JOptionPane.INFORMATION_MESSAGE));
        btSaveLeaveRecord = new JButton("Save Leave Record");
        btSaveLeaveRecord.addActionListener(e -> onSave());
        buttonRow.add(btRequestUpdatedRpn);
        buttonRow.add(btSaveLeaveRecord);
        south.add(buttonRow);

        content.add(south, BorderLayout.SOUTH);
        add(content);
    }

    private static JFormattedTextField newDateField() {
        DateFormatter formatter = new DateFormatter(DATE_FMT);
        formatter.setAllowsInvalid(true);
        JFormattedTextField field = new JFormattedTextField(formatter);
        field.setValue(new Date());
        field.setColumns(10);
        return field;
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

    private void onSave() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        ParentingBenefitDTO draft = new ParentingBenefitDTO(selected.id(), String.valueOf(cbBenefitType.getSelectedItem()),
                asDate(dcLeaveStartDate), asDate(dcLeaveEndDate), cbReportedToRevenue.isSelected(), parse(tbEmployerTopUpAmountPerPeriod));
        SaveResult result = controller.saveLeaveRecord(draft);
        JOptionPane.showMessageDialog(this,
                result.detailMessageOrNull() == null ? "Saved." : result.detailMessageOrNull(),
                "Parenting Benefits", JOptionPane.INFORMATION_MESSAGE);
    }

    private static java.time.LocalDate asDate(JFormattedTextField field) {
        Object value = field.getValue();
        if (value instanceof Date d) {
            return d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
        return null;
    }

    private static BigDecimal parse(JTextField field) {
        try {
            return new BigDecimal(field.getText().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

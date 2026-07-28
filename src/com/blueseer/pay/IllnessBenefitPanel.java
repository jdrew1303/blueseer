package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.IllnessBenefitDtos.IllnessBenefitDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;

/** S-57 Illness Benefit Handling. */
public class IllnessBenefitPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final String NO_EMPLOYER_PAY = "no-employer-pay";
    private static final String EMPLOYER_PAYS_SALARY = "employer-pays-salary";
    private static final String MANDATE_REIMBURSEMENT = "mandate-reimbursement";

    private final IIllnessBenefitController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JRadioButton rbNoEmployerPay;
    private JRadioButton rbEmployerPaysSalary;
    private JRadioButton rbMandateReimbursement;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    private JTextField tbEmployerPaidAmountThisPeriod;
    private JTextField tbEmployerPaidAmountThisPeriodMandate;
    private JTextField tbDspReimbursementAmount;

    private JButton btSaveIllnessBenefitRecord;

    public IllnessBenefitPanel() {
        this(new InMemoryIllnessBenefitController(PayrollStubStore.shared(), new InMemoryAdditionDeductionService(PayrollStubStore.shared())),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public IllnessBenefitPanel(IIllnessBenefitController controller, IEmployeeRepository employeeRepository) {
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
        content.setBorder(BorderFactory.createTitledBorder("Illness Benefit Handling"));
        content.setPreferredSize(new Dimension(560, 340));

        JPanel north = new JPanel();
        north.setLayout(new javax.swing.BoxLayout(north, javax.swing.BoxLayout.Y_AXIS));
        JPanel employeeRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        employeeRow.add(new JLabel("Employee:"));
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        employeeRow.add(cbEmployeeSelector);
        north.add(employeeRow);

        JPanel policyRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        rbNoEmployerPay = new JRadioButton("Employer does not pay sick leave", true);
        rbEmployerPaysSalary = new JRadioButton("Employer pays employee while out sick");
        rbMandateReimbursement = new JRadioButton("Employer receives Illness Benefit payment (mandate)");
        ButtonGroup group = new ButtonGroup();
        group.add(rbNoEmployerPay);
        group.add(rbEmployerPaysSalary);
        group.add(rbMandateReimbursement);
        rbNoEmployerPay.addActionListener(e -> cardLayout.show(cardPanel, NO_EMPLOYER_PAY));
        rbEmployerPaysSalary.addActionListener(e -> cardLayout.show(cardPanel, EMPLOYER_PAYS_SALARY));
        rbMandateReimbursement.addActionListener(e -> cardLayout.show(cardPanel, MANDATE_REIMBURSEMENT));
        JPanel policyRadios = new JPanel();
        policyRadios.setLayout(new javax.swing.BoxLayout(policyRadios, javax.swing.BoxLayout.Y_AXIS));
        policyRadios.add(rbNoEmployerPay);
        policyRadios.add(rbEmployerPaysSalary);
        policyRadios.add(rbMandateReimbursement);
        policyRow.add(policyRadios);
        north.add(policyRow);

        content.add(north, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(new JPanel(), NO_EMPLOYER_PAY);
        cardPanel.add(buildEmployerPaysSalaryCard(), EMPLOYER_PAYS_SALARY);
        cardPanel.add(buildMandateReimbursementCard(), MANDATE_REIMBURSEMENT);
        content.add(cardPanel, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        btSaveIllnessBenefitRecord = new JButton("Save");
        btSaveIllnessBenefitRecord.addActionListener(e -> onSave());
        south.add(btSaveIllnessBenefitRecord);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private JPanel buildEmployerPaysSalaryCard() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        panel.add(new JLabel("Employer-Paid Amount This Period:"));
        tbEmployerPaidAmountThisPeriod = new JTextField("0", 10);
        panel.add(tbEmployerPaidAmountThisPeriod);
        return panel;
    }

    private JPanel buildMandateReimbursementCard() {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        JPanel row1 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row1.add(new JLabel("Employer-Paid Amount This Period:"));
        tbEmployerPaidAmountThisPeriodMandate = new JTextField("0", 10);
        row1.add(tbEmployerPaidAmountThisPeriodMandate);
        panel.add(row1);
        JPanel row2 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row2.add(new JLabel("DSP Reimbursement Amount:"));
        tbDspReimbursementAmount = new JTextField("0", 10);
        row2.add(tbDspReimbursementAmount);
        panel.add(row2);
        JLabel lblLedgerNote = new JLabel("<html><i>This reimbursement is not part of payroll - record it as a receipt "
                + "in your own bookkeeping/GL, not on this employee's payslip.</i></html>");
        JPanel row3 = new JPanel(new FlowLayout(FlowLayout.LEFT));
        row3.add(lblLedgerNote);
        panel.add(row3);
        return panel;
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
        String variant;
        BigDecimal employerPaid = BigDecimal.ZERO;
        BigDecimal dspReimbursement = null;
        if (rbNoEmployerPay.isSelected()) {
            variant = "NO_EMPLOYER_SICK_PAY";
        } else if (rbEmployerPaysSalary.isSelected()) {
            variant = "EMPLOYER_PAYS_FULL_OR_PARTIAL_SALARY";
            employerPaid = parse(tbEmployerPaidAmountThisPeriod);
        } else {
            variant = "EMPLOYER_RECEIVES_MANDATE_REIMBURSEMENT";
            employerPaid = parse(tbEmployerPaidAmountThisPeriodMandate);
            dspReimbursement = parse(tbDspReimbursementAmount);
        }
        SaveResult result = controller.saveIllnessBenefitRecord(new IllnessBenefitDTO(selected.id(), variant, employerPaid, dspReimbursement));
        JOptionPane.showMessageDialog(this,
                result.detailMessageOrNull() == null ? "Saved." : result.detailMessageOrNull(),
                "Illness Benefit Handling", JOptionPane.INFORMATION_MESSAGE);
    }

    private static BigDecimal parse(JTextField field) {
        try {
            return new BigDecimal(field.getText().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

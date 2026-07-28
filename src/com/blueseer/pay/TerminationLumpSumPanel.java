package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.TerminationDtos.LumpSumExemptionResultDTO;
import com.blueseer.pay.TerminationDtos.TerminationLumpSumDTO;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;

/** S-50 Leaver - Taxable/Non-Taxable Lump Sum. */
public class TerminationLumpSumPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final ITerminationController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JTextField tbExGratiaLumpSumAmount;
    private JTextField tbCompleteYearsOfService;
    private JTextField tbAverageAnnual36MonthPay;
    private JLabel lblEligibleForIncreasedExemption;
    private JTextField tbRelevantCapitalSum;
    private JLabel lblBasicExemption;
    private JLabel lblIncreasedExemption;
    private JLabel lblScsb;
    private JLabel lblFinalExemptionApplied;
    private JLabel lblTaxableAmount;
    private JButton btApplyToFinalPayslip;

    private LumpSumExemptionResultDTO currentResult;

    public TerminationLumpSumPanel() {
        this(new InMemoryTerminationController(PayrollStubStore.shared()), new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public TerminationLumpSumPanel(ITerminationController controller, IEmployeeRepository employeeRepository) {
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
        content.setBorder(BorderFactory.createTitledBorder("Leaver - Taxable/Non-Taxable Lump Sum"));
        content.setPreferredSize(new java.awt.Dimension(560, 480));

        JPanel form = new JPanel();
        GroupLayout layout = new GroupLayout(form);
        form.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblEmployee = new JLabel("Employee:");
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        cbEmployeeSelector.addActionListener(e -> recompute());

        JLabel lblExGratia = new JLabel("Ex-Gratia Lump Sum Amount:");
        tbExGratiaLumpSumAmount = new JTextField(12);
        addRecomputeListener(tbExGratiaLumpSumAmount);

        JLabel lblYears = new JLabel("Complete Years of Service:");
        tbCompleteYearsOfService = new JTextField(6);
        tbCompleteYearsOfService.setEditable(false);

        JLabel lblAvgPay = new JLabel("Average Annual Pay (last 36 months):");
        tbAverageAnnual36MonthPay = new JTextField(12);
        tbAverageAnnual36MonthPay.setEditable(false);

        JLabel lblEligibleLabel = new JLabel("Eligible for Increased Exemption:");
        lblEligibleForIncreasedExemption = new JLabel("-");

        JLabel lblRcs = new JLabel("Relevant Capital Sum:");
        tbRelevantCapitalSum = new JTextField(12);
        tbRelevantCapitalSum.setText("0");
        addRecomputeListener(tbRelevantCapitalSum);

        JLabel lblBasicExemptionLabel = new JLabel("Basic Exemption:");
        lblBasicExemption = new JLabel("-");
        JLabel lblIncreasedExemptionLabel = new JLabel("Increased Exemption:");
        lblIncreasedExemption = new JLabel("-");
        JLabel lblScsbLabel = new JLabel("SCSB:");
        lblScsb = new JLabel("-");
        JLabel lblFinalExemptionLabel = new JLabel("Final Exemption Applied:");
        lblFinalExemptionApplied = new JLabel("-");
        JLabel lblTaxableLabel = new JLabel("Taxable Amount:");
        lblTaxableAmount = new JLabel("-");

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblEmployee).addComponent(lblExGratia).addComponent(lblYears)
                                .addComponent(lblAvgPay).addComponent(lblEligibleLabel).addComponent(lblRcs)
                                .addComponent(lblBasicExemptionLabel).addComponent(lblIncreasedExemptionLabel)
                                .addComponent(lblScsbLabel).addComponent(lblFinalExemptionLabel).addComponent(lblTaxableLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(cbEmployeeSelector).addComponent(tbExGratiaLumpSumAmount)
                                .addComponent(tbCompleteYearsOfService).addComponent(tbAverageAnnual36MonthPay)
                                .addComponent(lblEligibleForIncreasedExemption).addComponent(tbRelevantCapitalSum)
                                .addComponent(lblBasicExemption).addComponent(lblIncreasedExemption)
                                .addComponent(lblScsb).addComponent(lblFinalExemptionApplied).addComponent(lblTaxableAmount))));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmployee).addComponent(cbEmployeeSelector))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblExGratia).addComponent(tbExGratiaLumpSumAmount))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblYears).addComponent(tbCompleteYearsOfService))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblAvgPay).addComponent(tbAverageAnnual36MonthPay))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEligibleLabel).addComponent(lblEligibleForIncreasedExemption))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblRcs).addComponent(tbRelevantCapitalSum))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblBasicExemptionLabel).addComponent(lblBasicExemption))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblIncreasedExemptionLabel).addComponent(lblIncreasedExemption))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblScsbLabel).addComponent(lblScsb))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblFinalExemptionLabel).addComponent(lblFinalExemptionApplied))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblTaxableLabel).addComponent(lblTaxableAmount)));

        content.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        btApplyToFinalPayslip = new JButton("Apply to Final Payslip");
        btApplyToFinalPayslip.addActionListener(e -> onApply());
        south.add(btApplyToFinalPayslip);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private void addRecomputeListener(JTextField field) {
        DocumentListener listener = new DocumentListener() {
            @Override
            public void insertUpdate(javax.swing.event.DocumentEvent e) {
                recompute();
            }

            @Override
            public void removeUpdate(javax.swing.event.DocumentEvent e) {
                recompute();
            }

            @Override
            public void changedUpdate(javax.swing.event.DocumentEvent e) {
                recompute();
            }
        };
        field.getDocument().addDocumentListener(listener);
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

    private void recompute() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        TerminationLumpSumDTO draft = new TerminationLumpSumDTO(selected.id(), parse(tbExGratiaLumpSumAmount), parse(tbRelevantCapitalSum));
        currentResult = controller.previewLumpSumExemption(draft);

        tbCompleteYearsOfService.setText(String.valueOf(currentResult.completeYearsOfService()));
        tbAverageAnnual36MonthPay.setText(currentResult.averageAnnualRemunerationLast36Months().toPlainString());
        lblEligibleForIncreasedExemption.setText(currentResult.eligibleForIncreasedExemption() ? "Yes" : "No");
        lblBasicExemption.setText(currentResult.totalBasicExemption().toPlainString());
        lblIncreasedExemption.setText(currentResult.increasedExemption().toPlainString());
        lblScsb.setText(currentResult.scsb().toPlainString());
        lblFinalExemptionApplied.setText(currentResult.finalExemptionApplied().toPlainString());
        lblTaxableAmount.setText(currentResult.taxableAmount().toPlainString());
    }

    private void onApply() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null || currentResult == null) {
            return;
        }
        controller.applyToFinalPayslip(selected.id(), currentResult);
        JOptionPane.showMessageDialog(this,
                "Taxable amount of " + currentResult.taxableAmount().toPlainString()
                        + " will be applied to " + selected.surname() + ", " + selected.firstName()
                        + "'s next final payslip (PAYE/USC chargeable, PRSI-exempt).",
                "Termination Lump Sum", JOptionPane.INFORMATION_MESSAGE);
    }

    private static BigDecimal parse(JTextField field) {
        try {
            return new BigDecimal(field.getText().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

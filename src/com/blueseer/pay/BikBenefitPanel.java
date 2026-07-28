package com.blueseer.pay;

import com.blueseer.pay.BikDtos.BikBenefitDTO;
import com.blueseer.pay.BikDtos.BikBenefitPreviewDTO;
import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.Dimension;
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

/** S-55 BIK - Annual/One-Off Benefits. */
public class BikBenefitPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final String SMALL_BENEFIT_CATEGORY = "Small Benefit Exemption eligible";

    private final IBikController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JTextField tbBenefitDescription;
    private JTextField tbBenefitAmount;
    private JComboBox<String> cbBenefitCategory;
    private JLabel lblRemainingAllowanceLabel;
    private JLabel lblRemainingAnnualAllowance;
    private JButton btSaveBenefit;

    public BikBenefitPanel() {
        this(new InMemoryBikController(PayrollStubStore.shared(), new InMemoryAdditionDeductionService(PayrollStubStore.shared())),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public BikBenefitPanel(IBikController controller, IEmployeeRepository employeeRepository) {
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
        content.setBorder(BorderFactory.createTitledBorder("BIK - Annual/One-Off Benefits"));
        content.setPreferredSize(new Dimension(520, 300));

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

        JLabel lblDescription = new JLabel("Benefit Description:");
        tbBenefitDescription = new JTextField(16);

        JLabel lblAmount = new JLabel("Benefit Amount:");
        tbBenefitAmount = new JTextField(12);
        tbBenefitAmount.setText("0");
        addRecomputeListener(tbBenefitAmount);

        JLabel lblCategory = new JLabel("Category:");
        cbBenefitCategory = new JComboBox<>(new String[] {SMALL_BENEFIT_CATEGORY, "Other"});
        cbBenefitCategory.addActionListener(e -> {
            boolean small = SMALL_BENEFIT_CATEGORY.equals(cbBenefitCategory.getSelectedItem());
            lblRemainingAllowanceLabel.setVisible(small);
            lblRemainingAnnualAllowance.setVisible(small);
            recompute();
        });

        lblRemainingAllowanceLabel = new JLabel("Remaining Annual Allowance:");
        lblRemainingAnnualAllowance = new JLabel("-");

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblEmployee).addComponent(lblDescription).addComponent(lblAmount)
                                .addComponent(lblCategory).addComponent(lblRemainingAllowanceLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(cbEmployeeSelector).addComponent(tbBenefitDescription).addComponent(tbBenefitAmount)
                                .addComponent(cbBenefitCategory).addComponent(lblRemainingAnnualAllowance))));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmployee).addComponent(cbEmployeeSelector))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDescription).addComponent(tbBenefitDescription))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblAmount).addComponent(tbBenefitAmount))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblCategory).addComponent(cbBenefitCategory))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblRemainingAllowanceLabel).addComponent(lblRemainingAnnualAllowance)));

        content.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        btSaveBenefit = new JButton("Save Benefit");
        btSaveBenefit.addActionListener(e -> onSave());
        south.add(btSaveBenefit);
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

    private BikBenefitDTO currentDraft(EmployeeSummaryDTO selected) {
        boolean small = SMALL_BENEFIT_CATEGORY.equals(cbBenefitCategory.getSelectedItem());
        return new BikBenefitDTO(selected.id(), tbBenefitDescription.getText().trim(), parse(tbBenefitAmount), small);
    }

    private void recompute() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null || tbBenefitAmount == null) {
            return;
        }
        if (!SMALL_BENEFIT_CATEGORY.equals(cbBenefitCategory.getSelectedItem())) {
            return;
        }
        BikBenefitPreviewDTO preview = controller.previewBenefitBik(currentDraft(selected));
        lblRemainingAnnualAllowance.setText(preview.remainingAnnualAllowance().toPlainString());
    }

    private void onSave() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        SaveResult result = controller.saveBenefitEntry(currentDraft(selected));
        JOptionPane.showMessageDialog(this,
                result.detailMessageOrNull() == null ? "Benefit saved." : result.detailMessageOrNull(),
                "BIK - Annual/One-Off Benefits", JOptionPane.INFORMATION_MESSAGE);
        recompute();
    }

    private static BigDecimal parse(JTextField field) {
        try {
            return new BigDecimal(field.getText().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PensionDtos.CwpsPreviewDTO;
import com.blueseer.pay.PensionDtos.NeciPreviewDTO;
import com.blueseer.pay.PensionDtos.PensionDeductionDTO;
import com.blueseer.pay.PensionDtos.PensionReliefPreviewDTO;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.math.BigDecimal;
import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JTextField;
import javax.swing.event.DocumentListener;

/** S-59 Pension Deduction Setup, with S-60 Pension Tracing Number Entry embedded. */
public class PensionDeductionPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final String STANDARD = "standard";
    private static final String CWPS = "cwps";
    private static final String NECI = "neci";

    private final IPensionController controller;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JComboBox<String> cbSchemeType;
    private CardLayout cardLayout;
    private JPanel cardPanel;

    // Standard card
    private JRadioButton rbPercentage;
    private JRadioButton rbFixedAmount;
    private JTextField tbContributionValue;
    private JCheckBox cbEmployerMatch;
    private JTextField tbEmployerMatchValue;
    private JLabel lblAgeRelatedLimit;
    private JLabel lblRemainingReliefHeadroom;
    private JLabel lblTaxRelievablePortion;
    private JLabel lblNonRelievableExcess;

    // CWPS card
    private JLabel lblCwpsIsRegistered;
    private JLabel lblCwpsBreakdown;
    private JCheckBox cbIncludeHealthTrust;
    private JLabel lblHealthTrustAmount;
    private JCheckBox cbIncludeBenevolentFund;
    private JLabel lblBenevolentFundAmount;

    // NECI card
    private JLabel lblPensionableEarnings;
    private JLabel lblMemberContribution;
    private JLabel lblEmployerContribution;

    private JTextField tbPensionTracingNumber;
    private JButton btSaveTracingNumber;
    private JButton btSavePensionSetup;

    public PensionDeductionPanel() {
        this(new InMemoryPensionController(PayrollStubStore.shared(), new InMemoryAdditionDeductionService(PayrollStubStore.shared())),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public PensionDeductionPanel(IPensionController controller, IEmployeeRepository employeeRepository) {
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
        content.setBorder(BorderFactory.createTitledBorder("Pension Deduction Setup"));
        content.setPreferredSize(new Dimension(620, 520));

        JPanel north = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        north.add(new JLabel("Employee:"));
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        cbEmployeeSelector.addActionListener(e -> recompute());
        north.add(cbEmployeeSelector);
        north.add(new JLabel("Scheme Type:"));
        cbSchemeType = new JComboBox<>(new String[] {"Standard", "CWPS", "NECI"});
        cbSchemeType.addActionListener(e -> {
            String card = switch (String.valueOf(cbSchemeType.getSelectedItem())) {
                case "CWPS" -> CWPS;
                case "NECI" -> NECI;
                default -> STANDARD;
            };
            cardLayout.show(cardPanel, card);
            recompute();
        });
        north.add(cbSchemeType);
        content.add(north, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(buildStandardCard(), STANDARD);
        cardPanel.add(buildCwpsCard(), CWPS);
        cardPanel.add(buildNeciCard(), NECI);
        content.add(cardPanel, BorderLayout.CENTER);

        JPanel south = new JPanel();
        south.setLayout(new javax.swing.BoxLayout(south, javax.swing.BoxLayout.Y_AXIS));

        JPanel tracingRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        tracingRow.add(new JLabel("Pension Tracing Number:"));
        tbPensionTracingNumber = new JTextField(14);
        tracingRow.add(tbPensionTracingNumber);
        btSaveTracingNumber = new JButton("Save Tracing Number");
        btSaveTracingNumber.addActionListener(e -> onSaveTracingNumber());
        tracingRow.add(btSaveTracingNumber);
        south.add(tracingRow);

        JPanel saveRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        btSavePensionSetup = new JButton("Save Pension Setup");
        btSavePensionSetup.addActionListener(e -> onSave());
        saveRow.add(btSavePensionSetup);
        south.add(saveRow);

        content.add(south, BorderLayout.SOUTH);
        add(content);
    }

    private JPanel buildStandardCard() {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        JPanel modeRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        rbPercentage = new JRadioButton("Percentage", true);
        rbFixedAmount = new JRadioButton("Fixed Amount");
        ButtonGroup group = new ButtonGroup();
        group.add(rbPercentage);
        group.add(rbFixedAmount);
        rbPercentage.addActionListener(e -> recompute());
        rbFixedAmount.addActionListener(e -> recompute());
        modeRow.add(rbPercentage);
        modeRow.add(rbFixedAmount);
        modeRow.add(new JLabel("Value:"));
        tbContributionValue = new JTextField("0.05", 8);
        addRecomputeListener(tbContributionValue);
        modeRow.add(tbContributionValue);
        panel.add(modeRow);

        JPanel matchRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        cbEmployerMatch = new JCheckBox("Employer Match");
        tbEmployerMatchValue = new JTextField("0", 8);
        tbEmployerMatchValue.setVisible(false);
        cbEmployerMatch.addActionListener(e -> tbEmployerMatchValue.setVisible(cbEmployerMatch.isSelected()));
        matchRow.add(cbEmployerMatch);
        matchRow.add(tbEmployerMatchValue);
        panel.add(matchRow);

        panel.add(labelledRow("Age-Related Limit:", lblAgeRelatedLimit = new JLabel("-")));
        panel.add(labelledRow("Remaining Relief Headroom:", lblRemainingReliefHeadroom = new JLabel("-")));
        panel.add(labelledRow("Tax-Relievable Portion:", lblTaxRelievablePortion = new JLabel("-")));
        panel.add(labelledRow("Non-Relievable Excess:", lblNonRelievableExcess = new JLabel("-")));
        return panel;
    }

    private JPanel buildCwpsCard() {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        panel.add(labelledRow("Sectoral Employment Order Coverage:", lblCwpsIsRegistered = new JLabel("-")));
        lblCwpsBreakdown = new JLabel("-");
        JPanel breakdownRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        breakdownRow.add(lblCwpsBreakdown);
        panel.add(breakdownRow);

        JPanel healthRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        cbIncludeHealthTrust = new JCheckBox("Include Voluntary Health Trust");
        cbIncludeHealthTrust.addActionListener(e -> recompute());
        lblHealthTrustAmount = new JLabel("-");
        healthRow.add(cbIncludeHealthTrust);
        healthRow.add(lblHealthTrustAmount);
        panel.add(healthRow);

        JPanel benevolentRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        cbIncludeBenevolentFund = new JCheckBox("Include Voluntary Benevolent Fund");
        cbIncludeBenevolentFund.addActionListener(e -> recompute());
        lblBenevolentFundAmount = new JLabel("-");
        benevolentRow.add(cbIncludeBenevolentFund);
        benevolentRow.add(lblBenevolentFundAmount);
        panel.add(benevolentRow);
        return panel;
    }

    private JPanel buildNeciCard() {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));
        JLabel lblWarning = new JLabel("NECI rates shown are not yet confirmed against a primary source - verify before relying on this figure.");
        lblWarning.setForeground(new Color(200, 130, 0));
        lblWarning.setFont(lblWarning.getFont().deriveFont(Font.BOLD));
        JPanel warningRow = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        warningRow.add(lblWarning);
        panel.add(warningRow);

        panel.add(labelledRow("Pensionable Earnings:", lblPensionableEarnings = new JLabel("-")));
        panel.add(labelledRow("Member Contribution:", lblMemberContribution = new JLabel("-")));
        panel.add(labelledRow("Employer Contribution:", lblEmployerContribution = new JLabel("-")));
        return panel;
    }

    private JPanel labelledRow(String label, JLabel valueLabel) {
        JPanel row = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.LEFT));
        row.add(new JLabel(label));
        row.add(valueLabel);
        return row;
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

    private PensionDeductionDTO currentDraft(EmployeeSummaryDTO selected) {
        String schemeType = schemeTypeKey();
        return new PensionDeductionDTO(selected.id(), schemeType, rbPercentage.isSelected(), parse(tbContributionValue),
                cbEmployerMatch.isSelected(), parse(tbEmployerMatchValue),
                cbIncludeHealthTrust.isSelected(), cbIncludeBenevolentFund.isSelected());
    }

    private String schemeTypeKey() {
        return switch (String.valueOf(cbSchemeType.getSelectedItem())) {
            case "CWPS" -> "CWPS";
            case "NECI" -> "NECI";
            default -> "STANDARD";
        };
    }

    private void recompute() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null || tbContributionValue == null) {
            return;
        }
        switch (schemeTypeKey()) {
            case "CWPS" -> {
                CwpsPreviewDTO preview = controller.previewCwpsContribution(selected.id());
                lblCwpsIsRegistered.setText(preview.isCwpsRegistered() ? "Covered" : "Not Covered");
                lblCwpsBreakdown.setText(String.format(
                        "<html>Pension: Employer &euro;%s / Member &euro;%s<br>Death in Service: Employer &euro;%s / Member &euro;%s<br>"
                                + "Sick Pay: Employer &euro;%s / Member &euro;%s</html>",
                        preview.employerPensionWeekly(), preview.memberPensionWeekly(),
                        preview.employerDeathInServiceWeekly(), preview.memberDeathInServiceWeekly(),
                        preview.employerSickPayWeekly(), preview.memberSickPayWeekly()));
                lblHealthTrustAmount.setText(cbIncludeHealthTrust.isSelected() ? preview.memberHealthTrustWeekly().toPlainString() : "");
                lblBenevolentFundAmount.setText(cbIncludeBenevolentFund.isSelected() ? preview.memberBenevolentFundWeekly().toPlainString() : "");
            }
            case "NECI" -> {
                NeciPreviewDTO preview = controller.previewNeciContribution(selected.id(), null);
                lblPensionableEarnings.setText("(from pay entry)");
                lblMemberContribution.setText(preview.memberContribution().toPlainString());
                lblEmployerContribution.setText(preview.employerContribution().toPlainString());
            }
            default -> {
                PensionReliefPreviewDTO preview = controller.previewPensionRelief(currentDraft(selected));
                lblAgeRelatedLimit.setText(preview.ageRelatedPercentageLimit().toPlainString());
                lblRemainingReliefHeadroom.setText(preview.remainingReliefHeadroom().toPlainString());
                lblTaxRelievablePortion.setText(preview.taxRelievablePortion().toPlainString());
                lblNonRelievableExcess.setText(preview.nonRelievableExcess().toPlainString());
            }
        }
    }

    private void onSave() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        SaveResult result = controller.savePensionDeduction(currentDraft(selected));
        JOptionPane.showMessageDialog(this,
                result.detailMessageOrNull() == null ? "Pension deduction saved." : result.detailMessageOrNull(),
                "Pension Deduction Setup", JOptionPane.INFORMATION_MESSAGE);
    }

    private void onSaveTracingNumber() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        SaveResult result = controller.savePensionTracingNumber(selected.id(), tbPensionTracingNumber.getText().trim());
        JOptionPane.showMessageDialog(this,
                result.detailMessageOrNull() == null ? "Saved." : result.detailMessageOrNull(),
                "Pension Tracing Number", JOptionPane.INFORMATION_MESSAGE);
    }

    private static BigDecimal parse(JTextField field) {
        try {
            return new BigDecimal(field.getText().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

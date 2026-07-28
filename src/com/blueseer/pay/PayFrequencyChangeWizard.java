package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayFrequencyDtos.FrequencyChangePreviewDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Window;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;

/** S-64 Changing an Employee's Pay Frequency. */
public class PayFrequencyChangeWizard extends JDialog {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final String SELECT = "select";
    private static final String WARNING = "proration-warning";
    private static final String CONFIRM = "confirm";

    private final IPayFrequencyController controller;
    private final IEmployeeRepository employeeRepository;

    private CardLayout cardLayout;
    private JPanel cardPanel;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JLabel lblCurrentFrequency;
    private JComboBox<PayFrequency> cbNewFrequency;
    private JLabel lblProrationExplanation;
    private JButton btConfirmChange;

    private FrequencyChangePreviewDTO currentPreview;

    public PayFrequencyChangeWizard(Window owner, IPayFrequencyController controller, IEmployeeRepository employeeRepository) {
        super(owner, "Change Employee Pay Frequency", ModalityType.APPLICATION_MODAL);
        this.controller = controller;
        this.employeeRepository = employeeRepository;
        initComponents();
        refreshEmployees();
        setSize(520, 360);
        setLocationRelativeTo(owner);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        cardLayout = new CardLayout();
        cardPanel = new JPanel(cardLayout);
        cardPanel.add(buildSelectCard(), SELECT);
        cardPanel.add(buildWarningCard(), WARNING);
        cardPanel.add(buildConfirmCard(), CONFIRM);
        add(cardPanel, BorderLayout.CENTER);

        cardLayout.show(cardPanel, SELECT);
    }

    private JPanel buildSelectCard() {
        JPanel panel = new JPanel();
        panel.setLayout(new javax.swing.BoxLayout(panel, javax.swing.BoxLayout.Y_AXIS));

        JPanel empRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        empRow.add(new JLabel("Employee:"));
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        cbEmployeeSelector.addActionListener(e -> onEmployeeChanged());
        empRow.add(cbEmployeeSelector);
        panel.add(empRow);

        JPanel curRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        curRow.add(new JLabel("Current Frequency:"));
        lblCurrentFrequency = new JLabel("-");
        curRow.add(lblCurrentFrequency);
        panel.add(curRow);

        JPanel newRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        newRow.add(new JLabel("New Frequency:"));
        cbNewFrequency = new JComboBox<>();
        newRow.add(cbNewFrequency);
        panel.add(newRow);

        JPanel nextRow = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btNext = new JButton("Next >");
        btNext.addActionListener(e -> onAdvanceToWarning());
        nextRow.add(btNext);
        panel.add(nextRow);

        return panel;
    }

    private JPanel buildWarningCard() {
        JPanel panel = new JPanel(new BorderLayout());
        lblProrationExplanation = new JLabel("<html></html>");
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(lblProrationExplanation);
        panel.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btBack = new JButton("< Back");
        btBack.addActionListener(e -> cardLayout.show(cardPanel, SELECT));
        JButton btNext = new JButton("Next >");
        btNext.addActionListener(e -> cardLayout.show(cardPanel, CONFIRM));
        south.add(btBack);
        south.add(btNext);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildConfirmCard() {
        JPanel panel = new JPanel(new BorderLayout());
        JPanel center = new JPanel(new FlowLayout(FlowLayout.LEFT));
        center.add(new JLabel("Confirm the pay frequency change shown on the previous step."));
        panel.add(center, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btBack = new JButton("< Back");
        btBack.addActionListener(e -> cardLayout.show(cardPanel, WARNING));
        btConfirmChange = new JButton("Confirm Change");
        btConfirmChange.addActionListener(e -> onConfirm());
        south.add(btBack);
        south.add(btConfirmChange);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private void refreshEmployees() {
        cbEmployeeSelector.removeAllItems();
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            cbEmployeeSelector.addItem(summary);
        }
    }

    private void onEmployeeChanged() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null) {
            return;
        }
        PayFrequency current = controller.getCurrentFrequency(selected.id());
        lblCurrentFrequency.setText(String.valueOf(current));
        cbNewFrequency.removeAllItems();
        for (PayFrequency f : PayFrequency.values()) {
            if (f != current) {
                cbNewFrequency.addItem(f);
            }
        }
    }

    private void onAdvanceToWarning() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        PayFrequency newFrequency = (PayFrequency) cbNewFrequency.getSelectedItem();
        if (selected == null || newFrequency == null) {
            return;
        }
        currentPreview = controller.previewFrequencyChange(selected.id(), newFrequency);
        lblProrationExplanation.setText("<html><body style='width: 380px'>" + currentPreview.explanation() + "</body></html>");
        cardLayout.show(cardPanel, WARNING);
    }

    private void onConfirm() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null || currentPreview == null) {
            return;
        }
        SaveResult result = controller.applyFrequencyChange(selected.id(), currentPreview.newFrequency());
        JOptionPane.showMessageDialog(this,
                result.detailMessageOrNull() == null ? "Frequency changed." : result.detailMessageOrNull(),
                "Change Pay Frequency", JOptionPane.INFORMATION_MESSAGE);
        dispose();
    }
}

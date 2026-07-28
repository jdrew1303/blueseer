package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayProcessingDtos.PayslipRecordDTO;
import com.blueseer.pay.PsrDtos.CorrectionDetailsDTO;
import com.blueseer.pay.PsrDtos.CorrectionResult;
import com.blueseer.pay.PsrDtos.CorrectionType;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextArea;
import javax.swing.JTextField;

/**
 * S-33 Correction PSR Wizard, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * Only three of the six correction tiles carry a Controller payload -
 * "Do it all again," "New Employee," and "Employee has left" route to other
 * screens instead (S-18, S-04, S-48/S-49 respectively), matching the
 * spec's own distinction.
 */
public class CorrectionWizardDialog extends JDialog {

    private static final String[] PRSI_CLASSES = { "A1", "A2", "AX", "AL", "J", "S0" };
    private static final CompanyId DEMO_COMPANY = new CompanyId(1);

    private final IPsrGateway gateway;
    private final IEmployeeRepository employeeRepository;
    private final PayrollStubStore store;

    private CardLayout cardLayout;
    private JPanel cards;

    public CorrectionWizardDialog(Frame owner, IPsrGateway gateway, IEmployeeRepository employeeRepository, PayrollStubStore store) {
        super(owner, "Correction PSR Wizard", true);
        this.gateway = gateway;
        this.employeeRepository = employeeRepository;
        this.store = store;
        initComponents();
        setSize(560, 380);
        setLocation(60, 60);
    }

    private void initComponents() {
        setLayout(new BorderLayout());

        JPanel tiles = new JPanel(new GridLayout(2, 3, 5, 5));
        tiles.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        addTile(tiles, "Do it all again", "do-it-all-again");
        addTile(tiles, "New Employee", "new-employee");
        addTile(tiles, "Payment was different", "payment-was-different");
        addTile(tiles, "Wrong PPS Number", "wrong-pps-number");
        addTile(tiles, "Wrong PRSI Class", "wrong-prsi-class");
        addTile(tiles, "Employee has left", "employee-has-left");
        add(tiles, BorderLayout.NORTH);

        cardLayout = new CardLayout();
        cards = new JPanel(cardLayout);
        cards.add(buildRoutingCard("Reopens the just-finalised, not-yet-paid period in Pay Entry so you can correct and re-finalise it.",
                "Open Payroll > Pay Entry"), "do-it-all-again");
        cards.add(buildRoutingCard("Opens Employee Maintenance to add the missed employee - their first pay entry will be picked up in a correction PSR.",
                "Open Payroll > Employee Maintenance"), "new-employee");
        cards.add(buildPaymentWasDifferentCard(), "payment-was-different");
        cards.add(buildWrongPpsNumberCard(), "wrong-pps-number");
        cards.add(buildWrongPrsiClassCard(), "wrong-prsi-class");
        cards.add(buildRoutingCard("Opens the Leaver flow to record this employee's final payslip and cessation date.",
                "Open Payroll > Pay Entry (leaver checkbox)"), "employee-has-left");
        add(cards, BorderLayout.CENTER);

        JPanel south = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton btClose = new JButton("Close");
        btClose.addActionListener(e -> dispose());
        south.add(btClose);
        add(south, BorderLayout.SOUTH);
    }

    private void addTile(JPanel tiles, String label, String cardName) {
        JButton tile = new JButton("<html><center>" + label + "</center></html>");
        tile.addActionListener(e -> cardLayout.show(cards, cardName));
        tiles.add(tile);
    }

    private JPanel buildRoutingCard(String explanation, String routeMessage) {
        JPanel panel = new JPanel(new BorderLayout());
        JTextArea text = new JTextArea(explanation);
        text.setEditable(false);
        text.setLineWrap(true);
        text.setWrapStyleWord(true);
        text.setOpaque(false);
        panel.add(text, BorderLayout.CENTER);
        JButton btReopen = new JButton("Continue");
        btReopen.addActionListener(e -> JOptionPane.showMessageDialog(this, routeMessage, "Correction", JOptionPane.INFORMATION_MESSAGE));
        JPanel south = new JPanel(new FlowLayout(FlowLayout.LEFT));
        south.add(btReopen);
        panel.add(south, BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildPaymentWasDifferentCard() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<EmployeeSummaryDTO> cbEmployee = employeeCombo();
        JComboBox<LocalDate> cbOriginalPeriod = new JComboBox<>();
        cbEmployee.addActionListener(e -> refreshOriginalPeriods(cbEmployee, cbOriginalPeriod));
        refreshOriginalPeriods(cbEmployee, cbOriginalPeriod);
        JFormattedTextField tbActualAmountPaid = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        tbActualAmountPaid.setColumns(10);
        JButton btApply = new JButton("Apply");
        btApply.addActionListener(e -> {
            EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployee.getSelectedItem();
            LocalDate period = (LocalDate) cbOriginalPeriod.getSelectedItem();
            if (selected == null || period == null || tbActualAmountPaid.getValue() == null) {
                return;
            }
            BigDecimal actual = new BigDecimal(tbActualAmountPaid.getValue().toString());
            CorrectionResult result = gateway.applyCorrection(CorrectionType.PAYMENT_WAS_DIFFERENT,
                    new CorrectionDetailsDTO.PaymentWasDifferent(selected.id(), period, actual));
            showResultAndCloseIfSuccess(result);
        });
        panel.add(new JLabel("Employee:"));
        panel.add(cbEmployee);
        panel.add(new JLabel("Original Period:"));
        panel.add(cbOriginalPeriod);
        panel.add(new JLabel("Actual Amount Paid:"));
        panel.add(tbActualAmountPaid);
        panel.add(btApply);
        return panel;
    }

    private JPanel buildWrongPpsNumberCard() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<EmployeeSummaryDTO> cbEmployee = employeeCombo();
        JTextField tbCorrectPpsNumber = new JTextField(10);
        JButton btApply = new JButton("Apply");
        btApply.addActionListener(e -> {
            EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployee.getSelectedItem();
            if (selected == null || tbCorrectPpsNumber.getText().isBlank()) {
                return;
            }
            CorrectionResult result = gateway.applyCorrection(CorrectionType.WRONG_PPS_NUMBER,
                    new CorrectionDetailsDTO.WrongPpsNumber(selected.id(), tbCorrectPpsNumber.getText().trim()));
            showResultAndCloseIfSuccess(result);
        });
        panel.add(new JLabel("Employee:"));
        panel.add(cbEmployee);
        panel.add(new JLabel("Correct PPS Number:"));
        panel.add(tbCorrectPpsNumber);
        panel.add(btApply);
        return panel;
    }

    private JPanel buildWrongPrsiClassCard() {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JComboBox<EmployeeSummaryDTO> cbEmployee = employeeCombo();
        JComboBox<String> cbCorrectPrsiClass = new JComboBox<>(PRSI_CLASSES);
        JButton btApply = new JButton("Apply");
        btApply.addActionListener(e -> {
            EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployee.getSelectedItem();
            if (selected == null) {
                return;
            }
            CorrectionResult result = gateway.applyCorrection(CorrectionType.WRONG_PRSI_CLASS,
                    new CorrectionDetailsDTO.WrongPrsiClass(selected.id(), (String) cbCorrectPrsiClass.getSelectedItem()));
            showResultAndCloseIfSuccess(result);
        });
        panel.add(new JLabel("Employee:"));
        panel.add(cbEmployee);
        panel.add(new JLabel("Correct PRSI Class:"));
        panel.add(cbCorrectPrsiClass);
        panel.add(btApply);
        return panel;
    }

    private JComboBox<EmployeeSummaryDTO> employeeCombo() {
        JComboBox<EmployeeSummaryDTO> combo = new JComboBox<>();
        combo.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            if (!summary.isFormerEmployee()) {
                combo.addItem(summary);
            }
        }
        return combo;
    }

    private void refreshOriginalPeriods(JComboBox<EmployeeSummaryDTO> cbEmployee, JComboBox<LocalDate> cbOriginalPeriod) {
        cbOriginalPeriod.removeAllItems();
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployee.getSelectedItem();
        if (selected == null) {
            return;
        }
        for (PayslipRecordDTO p : store.payslips.values()) {
            if (p.employeeId().equals(selected.id())) {
                cbOriginalPeriod.addItem(p.payDate());
            }
        }
    }

    private void showResultAndCloseIfSuccess(CorrectionResult result) {
        JOptionPane.showMessageDialog(this, result.detailMessageOrNull(), "Correction",
                result.success() ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
        if (result.success()) {
            dispose();
        }
    }
}

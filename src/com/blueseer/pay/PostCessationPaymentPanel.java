package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayrollIds.CompanyId;
import com.blueseer.pay.PostCessationDtos.PostCessationResult;
import com.blueseer.pay.PostCessationDtos.TaxYearScope;

import java.awt.BorderLayout;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.text.DateFormatter;

/**
 * S-51 Post-Cessation Payment (Current Year) and, via {@link
 * PostCessationOutOfYearPaymentPanel}, S-52 (Out of Year) - the same
 * component per the screen spec, with {@code outOfYear} adding {@code
 * cbTaxYear} and switching which {@link TaxYearScope} is resolved. Split
 * into two menu-loadable classes only because the menu system launches one
 * concrete no-arg-constructor class per entry, not because the screen
 * itself differs.
 */
public class PostCessationPaymentPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private static final SimpleDateFormat DATE_FMT = new SimpleDateFormat("dd/MM/yyyy");

    private final IPostCessationController controller;
    private final boolean outOfYear;

    private JComboBox<Integer> cbTaxYear;
    private JComboBox<EmployeeSummaryDTO> cbFormerEmployeeSelector;
    private JTextField tbPaymentAmount;
    private JFormattedTextField dcPaymentDate;
    private JButton btProcessPayment;

    public PostCessationPaymentPanel() {
        this(new InMemoryPostCessationController(PayrollStubStore.shared(), PayrollEngineFactory.defaultCalculationService()), false);
    }

    protected PostCessationPaymentPanel(IPostCessationController controller, boolean outOfYear) {
        this.controller = controller;
        this.outOfYear = outOfYear;
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
        content.setBorder(BorderFactory.createTitledBorder(
                outOfYear ? "Post-Cessation Payment (Out of Year)" : "Post-Cessation Payment (Current Year)"));
        content.setPreferredSize(new java.awt.Dimension(500, outOfYear ? 220 : 190));

        JPanel form = new JPanel();
        GroupLayout layout = new GroupLayout(form);
        form.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblTaxYear = new JLabel("Tax Year:");
        cbTaxYear = new JComboBox<>();
        for (Integer year : PayrollEngineFactory.registeredTaxYears()) {
            if (year != currentTaxYear()) {
                cbTaxYear.addItem(year);
            }
        }
        cbTaxYear.addActionListener(e -> refreshEmployees());

        JLabel lblEmployee = new JLabel("Former Employee:");
        cbFormerEmployeeSelector = new JComboBox<>();
        cbFormerEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));

        JLabel lblAmount = new JLabel("Payment Amount:");
        tbPaymentAmount = new JTextField(12);

        JLabel lblDate = new JLabel("Payment Date:");
        DateFormatter formatter = new DateFormatter(DATE_FMT);
        formatter.setAllowsInvalid(true);
        dcPaymentDate = new JFormattedTextField(formatter);
        dcPaymentDate.setValue(new Date());
        dcPaymentDate.setColumns(10);

        GroupLayout.ParallelGroup labels = layout.createParallelGroup(Alignment.LEADING);
        GroupLayout.ParallelGroup fields = layout.createParallelGroup(Alignment.LEADING);
        GroupLayout.SequentialGroup rows = layout.createSequentialGroup();

        if (outOfYear) {
            labels.addComponent(lblTaxYear);
            fields.addComponent(cbTaxYear);
            rows.addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblTaxYear).addComponent(cbTaxYear));
        }
        labels.addComponent(lblEmployee).addComponent(lblAmount).addComponent(lblDate);
        fields.addComponent(cbFormerEmployeeSelector).addComponent(tbPaymentAmount).addComponent(dcPaymentDate);
        rows.addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmployee).addComponent(cbFormerEmployeeSelector))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblAmount).addComponent(tbPaymentAmount))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblDate).addComponent(dcPaymentDate));

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup().addGroup(labels).addGroup(fields)));
        layout.setVerticalGroup(rows);

        content.add(form, BorderLayout.CENTER);

        JPanel south = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT));
        btProcessPayment = new JButton("Process Payment");
        btProcessPayment.addActionListener(e -> onProcessPayment());
        south.add(btProcessPayment);
        content.add(south, BorderLayout.SOUTH);

        add(content);
    }

    private int currentTaxYear() {
        List<Integer> years = PayrollEngineFactory.registeredTaxYears();
        return years.get(years.size() - 1);
    }

    private TaxYearScope currentScope() {
        if (!outOfYear) {
            return TaxYearScope.forCurrentYear();
        }
        Integer selected = (Integer) cbTaxYear.getSelectedItem();
        return selected == null ? TaxYearScope.forHistoricalYear(currentTaxYear()) : TaxYearScope.forHistoricalYear(selected);
    }

    private void refreshEmployees() {
        EmployeeSummaryDTO previous = (EmployeeSummaryDTO) cbFormerEmployeeSelector.getSelectedItem();
        cbFormerEmployeeSelector.removeAllItems();
        for (EmployeeSummaryDTO summary : controller.getEligibleFormerEmployees(DEMO_COMPANY, currentScope())) {
            cbFormerEmployeeSelector.addItem(summary);
        }
        if (previous != null) {
            cbFormerEmployeeSelector.setSelectedItem(previous);
        }
    }

    private void onProcessPayment() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbFormerEmployeeSelector.getSelectedItem();
        if (selected == null) {
            JOptionPane.showMessageDialog(this, "No eligible former employees for this tax year.", "Post-Cessation Payment", JOptionPane.WARNING_MESSAGE);
            return;
        }
        BigDecimal amount = parse(tbPaymentAmount);
        Object dateValue = dcPaymentDate.getValue();
        java.time.LocalDate paymentDate = dateValue instanceof Date d
                ? d.toInstant().atZone(ZoneId.systemDefault()).toLocalDate()
                : java.time.LocalDate.now();

        PostCessationResult result = controller.processPayment(selected.id(), amount, paymentDate, currentScope());
        if (result.success()) {
            JOptionPane.showMessageDialog(this,
                    "Payment processed.\nPAYE: " + result.paye().toPlainString()
                            + "\nPRSI: " + result.prsi().toPlainString()
                            + "\nUSC: " + result.usc().toPlainString()
                            + "\nNet: " + result.net().toPlainString(),
                    "Post-Cessation Payment", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, result.message(), "Post-Cessation Payment", JOptionPane.WARNING_MESSAGE);
        }
    }

    private static BigDecimal parse(JTextField field) {
        try {
            return new BigDecimal(field.getText().trim());
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }
}

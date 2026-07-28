package com.blueseer.pay;

import com.blueseer.pay.EmployeeDtos.EmployeeSummaryDTO;
import com.blueseer.pay.PayProcessingDtos.NetToGrossResultDTO;
import com.blueseer.pay.PayrollIds.CompanyId;

import java.awt.FlowLayout;
import java.math.BigDecimal;
import javax.swing.BorderFactory;
import javax.swing.GroupLayout;
import javax.swing.GroupLayout.Alignment;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingWorker;

/**
 * S-20 Net to Gross Payments, per docs/architecture/irish-payroll-2026-screen-specs.md.
 * The C-20 solve is fast but still routed through a {@link SwingWorker}, on
 * principle, per the spec's explicit note that even a sub-second solve
 * deserves this treatment since every iteration is part of the audit trail.
 */
public class NetToGrossPanel extends JPanel {

    private static final CompanyId DEMO_COMPANY = new CompanyId(1);
    private final IPayEntryController payEntryController;
    private final IEmployeeRepository employeeRepository;

    private JComboBox<EmployeeSummaryDTO> cbEmployeeSelector;
    private JFormattedTextField tbTargetNetAmount;
    private JButton btApplyToPayEntry;
    private JLabel lblGross;
    private JLabel lblPaye;
    private JLabel lblPrsi;
    private JLabel lblUsc;
    private JLabel lblNet;
    private NetToGrossResultDTO lastResult;

    public NetToGrossPanel() {
        this(new InMemoryPayEntryController(PayrollStubStore.shared(), PayrollEngineFactory.defaultCalculationService()),
                new InMemoryEmployeeRepository(PayrollStubStore.shared()));
    }

    public NetToGrossPanel(IPayEntryController payEntryController, IEmployeeRepository employeeRepository) {
        this.payEntryController = payEntryController;
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
        panel.setBorder(BorderFactory.createTitledBorder("Net to Gross Payments"));
        GroupLayout layout = new GroupLayout(panel);
        panel.setLayout(layout);
        layout.setAutoCreateGaps(true);
        layout.setAutoCreateContainerGaps(true);

        JLabel lblEmployee = new JLabel("Employee:");
        cbEmployeeSelector = new JComboBox<>();
        cbEmployeeSelector.setRenderer((list, value, index, isSelected, cellHasFocus) ->
                new JLabel(value == null ? "" : value.surname() + ", " + value.firstName() + " (" + value.worksNumber() + ")"));
        JLabel lblTargetNet = new JLabel("Target Net Amount:");
        tbTargetNetAmount = new JFormattedTextField(java.text.NumberFormat.getNumberInstance());
        tbTargetNetAmount.setColumns(10);
        JButton btCalculate = new JButton("Calculate");
        btCalculate.addActionListener(e -> onCalculate());

        JLabel lblGrossLabel = new JLabel("Gross Pay:");
        lblGross = new JLabel("-");
        JLabel lblPayeLabel = new JLabel("PAYE:");
        lblPaye = new JLabel("-");
        JLabel lblPrsiLabel = new JLabel("PRSI:");
        lblPrsi = new JLabel("-");
        JLabel lblUscLabel = new JLabel("USC:");
        lblUsc = new JLabel("-");
        JLabel lblNetLabel = new JLabel("Net Pay:");
        lblNet = new JLabel("-");

        btApplyToPayEntry = new JButton("Apply to Pay Entry");
        btApplyToPayEntry.setEnabled(false);
        btApplyToPayEntry.addActionListener(e -> onApplyToPayEntry());

        layout.setHorizontalGroup(layout.createParallelGroup(Alignment.LEADING)
                .addGroup(layout.createSequentialGroup()
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(lblEmployee).addComponent(lblTargetNet)
                                .addComponent(lblGrossLabel).addComponent(lblPayeLabel)
                                .addComponent(lblPrsiLabel).addComponent(lblUscLabel).addComponent(lblNetLabel))
                        .addGroup(layout.createParallelGroup(Alignment.LEADING)
                                .addComponent(cbEmployeeSelector).addComponent(tbTargetNetAmount)
                                .addComponent(lblGross).addComponent(lblPaye)
                                .addComponent(lblPrsi).addComponent(lblUsc).addComponent(lblNet)))
                .addComponent(btCalculate)
                .addComponent(btApplyToPayEntry));

        layout.setVerticalGroup(layout.createSequentialGroup()
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblEmployee).addComponent(cbEmployeeSelector))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblTargetNet).addComponent(tbTargetNetAmount))
                .addComponent(btCalculate)
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblGrossLabel).addComponent(lblGross))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPayeLabel).addComponent(lblPaye))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblPrsiLabel).addComponent(lblPrsi))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblUscLabel).addComponent(lblUsc))
                .addGroup(layout.createParallelGroup(Alignment.BASELINE).addComponent(lblNetLabel).addComponent(lblNet))
                .addComponent(btApplyToPayEntry));

        add(panel);
    }

    private void refreshEmployeeSelector() {
        cbEmployeeSelector.removeAllItems();
        for (EmployeeSummaryDTO summary : employeeRepository.searchEmployees(DEMO_COMPANY, "")) {
            if (!summary.isFormerEmployee()) {
                cbEmployeeSelector.addItem(summary);
            }
        }
    }

    private void onCalculate() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null || tbTargetNetAmount.getValue() == null) {
            return;
        }
        BigDecimal targetNet = new BigDecimal(tbTargetNetAmount.getValue().toString());
        btApplyToPayEntry.setEnabled(false);
        new SwingWorker<NetToGrossResultDTO, Void>() {
            @Override
            protected NetToGrossResultDTO doInBackground() {
                int currentPeriod = payEntryController.getCurrentPeriodNumber(DEMO_COMPANY);
                return payEntryController.solveNetToGross(selected.id(), currentPeriod, targetNet);
            }

            @Override
            protected void done() {
                try {
                    lastResult = get();
                    lblGross.setText(lastResult.solvedGross().toPlainString());
                    lblPaye.setText(lastResult.paye().toPlainString());
                    lblPrsi.setText(lastResult.prsi().toPlainString());
                    lblUsc.setText(lastResult.usc().toPlainString());
                    lblNet.setText(lastResult.net().toPlainString());
                    btApplyToPayEntry.setEnabled(true);
                } catch (Exception ex) {
                    JOptionPane.showMessageDialog(NetToGrossPanel.this, "Unable to solve: " + ex.getMessage(),
                            "Net to Gross", JOptionPane.ERROR_MESSAGE);
                }
            }
        }.execute();
    }

    private void onApplyToPayEntry() {
        EmployeeSummaryDTO selected = (EmployeeSummaryDTO) cbEmployeeSelector.getSelectedItem();
        if (selected == null || lastResult == null) {
            return;
        }
        int currentPeriod = payEntryController.getCurrentPeriodNumber(DEMO_COMPANY);
        var existing = payEntryController.loadPayEntry(selected.id(), currentPeriod);
        var updated = new PayProcessingDtos.PayEntryDTO(selected.id(), currentPeriod, existing.hourlyRate(),
                existing.standardHours(), existing.timeAndAThirdHours(), existing.timeAndAHalfHours(), existing.doubleTimeHours(),
                lastResult.solvedGross(), existing.holidayPayAmount(), existing.additionalWeeksSpread(),
                existing.leaving(), existing.leaveDateOrNull(), existing.noteOrNull());
        payEntryController.savePayEntry(updated);
        JOptionPane.showMessageDialog(this,
                "Applied - the solved gross of " + lastResult.solvedGross() + " has been saved to "
                        + selected.surname() + ", " + selected.firstName() + "'s pay entry for this period. "
                        + "Open Payroll > Pay Entry to review.",
                "Apply to Pay Entry", JOptionPane.INFORMATION_MESSAGE);
    }
}
